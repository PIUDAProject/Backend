package com.piuda.callcare.domain.fcmtoken.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.piuda.callcare.domain.fcmtoken.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    // multicast 발송 대상: 한 수신자의 활성 토큰 전체(폰+태블릿 등 기기 여러 대)
    List<FcmToken> findByUser_IdAndIsActiveTrue(Long userId);

    // 등록/해제 시 기존 토큰 조회 — 같은 토큰은 항상 한 행이므로 단건
    Optional<FcmToken> findByToken(String token);

    // 발송 결과로 판별된 무효 토큰 정리. 발송이 트랜잭션 밖에서 일어나 토큰이 준영속 상태이므로
    // dirty checking 대신 id 기준 벌크 UPDATE로 반영한다.
    @Modifying
    @Query("UPDATE FcmToken t SET t.isActive = false WHERE t.id IN :ids")
    void deactivateAllByIdIn(@Param("ids") Collection<Long> ids);
}