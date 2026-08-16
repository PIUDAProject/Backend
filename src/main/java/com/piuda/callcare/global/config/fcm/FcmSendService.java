package com.piuda.callcare.global.config.fcm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.piuda.callcare.domain.fcmtoken.entity.FcmToken;
import com.piuda.callcare.domain.fcmtoken.repository.FcmTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FCM 푸시 발송 프리미티브 (한 수신자의 여러 기기에 multicast).
 * <p>
 * 이력 저장({@link FcmSendRecorder#saveNotification}) → 활성 토큰 조회 → multicast 청크 발송
 * → 무효 토큰 정리({@link FcmSendRecorder#deactivateInvalidTokens}) 순서로 동작한다.
 * <b>이력을 먼저 저장하는 이유</b>는 그래야 {@code notificationId}를 푸시 payload에 실을 수 있고,
 * 알림을 탭한 클라이언트가 어느 알림을 읽음 처리해야 하는지 알 수 있기 때문이다.
 * "언제 무엇을 보낼지"(소진/충돌 트리거)는 이 계층의 책임이 아니며, 후속 단계에서 이 위에 얹는다.
 * <p>
 * <b>호출 계약 — 트랜잭션 밖에서 호출할 것.</b> 이 서비스는 자체 트랜잭션을 열지 않는다.
 * 발송은 외부 HTTP 왕복이므로 트랜잭션 안에서 하면 그 시간 내내 DB 커넥션을 점유하고,
 * 더 중요하게는 <b>푸시가 나간 뒤에 커밋이 돌아 롤백돼도 푸시를 회수할 수 없다.</b>
 * 따라서 트리거(소진·충돌 감지)는 자신의 트랜잭션이 <b>커밋된 뒤</b> 이 서비스를 호출해야 한다
 * — {@code @TransactionalEventListener(phase = AFTER_COMMIT)}. DB 쓰기는 {@link FcmSendRecorder}의
 * 짧은 트랜잭션 안에서만 일어난다.
 * <p>
 * FirebaseMessaging은 서비스 계정 키가 없으면 빈이 존재하지 않을 수 있으므로
 * {@link ObjectProvider}로 안전하게 접근하고, 없으면 발송 시점에만 미설정으로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmSendService {

    // FCM sendEachForMulticast 1회 최대 토큰 수
    private static final int MULTICAST_MAX_SIZE = 500;

    // 알림 종류 분기용(클라이언트가 어느 화면으로 갈지 판단)
    private static final String DATA_KEY_TYPE = "type";
    // 읽음 처리용 — 클라이언트가 이 값으로 어느 알림을 읽었는지 서버에 알린다
    private static final String DATA_KEY_NOTIFICATION_ID = "notificationId";

    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    private final FcmTokenRepository fcmTokenRepository;
    private final FcmSendRecorder fcmSendRecorder;

    public FcmSendResult send(FcmSendRequest request) {
        FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
        if (messaging == null) {
            log.warn("[FCM] 서비스 계정 키 미설정 — 푸시 발송을 건너뜁니다 (type={})", request.type());
            return FcmSendResult.notConfigured();
        }

        // 발송 전에 이력을 남긴다 — notificationId를 payload에 실어야 알림 탭 시 읽음 처리가 가능하다.
        Long notificationId = fcmSendRecorder.saveNotification(request);

        List<FcmToken> activeTokens = fcmTokenRepository.findByUser_IdAndIsActiveTrue(request.recipient().userId());
        if (activeTokens.isEmpty()) {
            // 앱 미설치·토큰 미등록. 재시도해도 결과가 같으므로 호출자가 다른 채널을 고려해야 한다.
            log.info("[FCM] 활성 토큰이 없어 발송하지 않습니다 (type={}, userId={})",
                request.type(), request.recipient().userId());
            return FcmSendResult.noToken();
        }

        Map<String, String> data = buildData(request, notificationId);
        int successCount = 0;
        int failureCount = 0;
        // 영구 무효로 판별된 토큰. 발송이 트랜잭션 밖이라 준영속이므로 엔티티가 아니라 id를 모아 넘긴다.
        List<Long> invalidTokenIds = new ArrayList<>();

        for (int start = 0; start < activeTokens.size(); start += MULTICAST_MAX_SIZE) {
            List<FcmToken> chunk = activeTokens.subList(start, Math.min(start + MULTICAST_MAX_SIZE, activeTokens.size()));

            BatchResponse response = sendChunk(messaging, request, data, chunk);
            if (response == null) {
                // 청크 전체 실패(연결/인증 등) — 토큰 개별 판별 불가
                failureCount += chunk.size();
                continue;
            }

            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse sendResponse = responses.get(i);
                if (sendResponse.isSuccessful()) {
                    successCount++;
                    continue;
                }
                failureCount++;
                if (isInvalidToken(sendResponse.getException())) {
                    invalidTokenIds.add(chunk.get(i).getId());
                }
            }
        }

        if (!invalidTokenIds.isEmpty()) {
            fcmSendRecorder.deactivateInvalidTokens(invalidTokenIds);
        }

        log.info("[FCM] 발송 완료 (type={}, success={}, failure={}, deactivated={})",
            request.type(), successCount, failureCount, invalidTokenIds.size());
        return FcmSendResult.completed(successCount, failureCount, invalidTokenIds.size());
    }

    // 딥링크용 키는 알림 종류를 아는 호출자가 채우고, 발송 계층은 자기만 아는 값(notificationId)과
    // 모든 알림에 공통인 분기 키(type)를 얹는다. FCM data의 값은 전부 String이어야 한다.
    private Map<String, String> buildData(FcmSendRequest request, Long notificationId) {
        Map<String, String> data = new HashMap<>(request.data());
        data.put(DATA_KEY_TYPE, request.type().name());
        data.put(DATA_KEY_NOTIFICATION_ID, String.valueOf(notificationId));
        return data;
    }

    private BatchResponse sendChunk(FirebaseMessaging messaging, FcmSendRequest request,
        Map<String, String> data, List<FcmToken> chunk) {
        MulticastMessage message = MulticastMessage.builder()
            .setNotification(Notification.builder()
                .setTitle(request.title())
                .setBody(request.body())
                .build())
            .putAllData(data)
            .addAllTokens(chunk.stream().map(FcmToken::getToken).toList())
            .build();
        try {
            return messaging.sendEachForMulticast(message);
        } catch (FirebaseMessagingException e) {
            log.error("[FCM] multicast 발송 실패 (chunkSize={}): {}", chunk.size(), e.getMessage(), e);
            return null;
        }
    }

    // FCM이 영구 무효로 판단하는 토큰(등록 해제/잘못된 토큰)만 정리 대상. 일시적 실패는 유지.
    private boolean isInvalidToken(FirebaseMessagingException exception) {
        if (exception == null) {
            return false;
        }
        MessagingErrorCode code = exception.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT;
    }
}