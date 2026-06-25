#!/bin/bash
set -e

DOCKER_IMAGE=$1
IMAGE_TAG=$2

BLUE_PORT=8080
GREEN_PORT=8081
NGINX_CONF="/etc/nginx/sites-available/default"

# 현재 실행 중인 컨테이너 확인
CURRENT=$(docker ps --format '{{.Names}}' | grep -E 'callcare-(blue|green)' | head -1)

if [ "$CURRENT" == "callcare-blue" ]; then
    NEXT="green"
    NEXT_PORT=$GREEN_PORT
    PREV="blue"
    PREV_PORT=$BLUE_PORT
else
    NEXT="blue"
    NEXT_PORT=$BLUE_PORT
    PREV="green"
    PREV_PORT=$GREEN_PORT
fi

echo ">>> 현재: $PREV ($PREV_PORT) → 배포 대상: $NEXT ($NEXT_PORT)"

# 새 이미지 pull

echo ">>> 이미지 pull: $DOCKER_IMAGE:$IMAGE_TAG"
docker pull $DOCKER_IMAGE:$IMAGE_TAG

# 새 컨테이너 실행
echo ">>> callcare-$NEXT 컨테이너 시작"
docker stop callcare-$NEXT 2>/dev/null || true
docker rm callcare-$NEXT 2>/dev/null || true
docker run -d \
    --name callcare-$NEXT \
    --network app_default \
    --env-file /home/ubuntu/app/.env \
    -e SPRING_PROFILES_ACTIVE=prod \
    -p $NEXT_PORT:8080 \
    --restart unless-stopped \
    $DOCKER_IMAGE:$IMAGE_TAG

# 헬스체크
echo ">>> 헬스체크 시작 ($NEXT_PORT)"
STATUS="000"
for i in {1..15}; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$NEXT_PORT/actuator/health 2>/dev/null || echo "000")
    if [ "$STATUS" == "200" ]; then
        echo ">>> 헬스체크 성공"
        break
    fi
    echo ">>> 헬스체크 대기 중... ($i/15)"
    sleep 5
done

if [ "$STATUS" != "200" ]; then
    echo ">>> 헬스체크 실패 — 롤백"
    docker stop callcare-$NEXT
    docker rm callcare-$NEXT
    exit 1
fi

# Nginx upstream 전환
echo ">>> Nginx upstream → $NEXT_PORT 으로 전환"
sudo sed -i "s/server localhost:$PREV_PORT/server localhost:$NEXT_PORT/" $NGINX_CONF

if ! sudo nginx -t; then
    echo ">>> Nginx 설정 오류 — 롤백"
    sudo sed -i "s/server localhost:$NEXT_PORT/server localhost:$PREV_PORT/" $NGINX_CONF
    docker stop callcare-$NEXT
    docker rm callcare-$NEXT
    exit 1
fi

sudo nginx -s reload
echo ">>> Nginx 전환 완료"

# 이전 컨테이너 종료
echo ">>> callcare-$PREV 컨테이너 종료"
docker stop callcare-$PREV 2>/dev/null || true
docker rm callcare-$PREV 2>/dev/null || true

# 사용하지 않는 이미지 정리
docker image prune -f

echo ">>> 배포 완료 ($NEXT:$NEXT_PORT)"