package com.piuda.callcare.global.config.fcm;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.fcmtoken.repository.FcmTokenRepository;
import com.piuda.callcare.domain.notification.entity.Notification;
import com.piuda.callcare.domain.notification.repository.NotificationRepository;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 발송 전후의 DB 쓰기를 담당하는 협력자 — 이력 저장(발송 전) + 무효 토큰 비활성화(발송 후).
 * <p>
 * {@link FcmSendService}에서 분리한 이유는 <b>트랜잭션 경계</b> 때문이다. 발송은 외부 HTTP 왕복이라
 * 트랜잭션 안에서 하면 그 시간만큼 DB 커넥션을 쥐고 있게 되므로, 발송은 트랜잭션 밖에서 하고
 * DB 쓰기만 이 클래스의 짧은 트랜잭션에 담는다. (같은 빈의 메서드를 자기 자신이 호출하면
 * 프록시를 타지 않아 {@code @Transactional}이 걸리지 않으므로 별도 빈이어야 한다.)
 * <p>
 * 두 메서드가 <b>각자 트랜잭션</b>인 것도 의도다 — 이력은 발송 <i>전에</i> 저장해야
 * {@code notificationId}를 푸시에 실을 수 있고, 무효 토큰 판별은 발송 <i>후에야</i> 알 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmSendRecorder {

    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SeniorRepository seniorRepository;

    /**
     * 발송 이력을 저장하고 그 id를 돌려준다. <b>발송 전에</b> 호출한다 — 알림을 탭했을 때
     * 클라이언트가 읽음 처리({@code Notification.markAsRead})를 하려면 어느 알림인지 알아야 하고,
     * 그러려면 {@code notificationId}가 푸시 payload에 실려야 하기 때문이다.
     * <p>
     * 이력은 recipient당 1건이다 — 기기가 몇 대든(토큰 여러 개) 사용자가 보는 알림은 하나이기 때문.
     * 토큰이 없거나 전송이 실패한 recipient도 저장한다. {@code Notification}은 인앱 알림 리스트 레코드라
     * 푸시가 실제로 도달했는지와 독립적이다.
     *
     * @return 저장된 Notification id
     */
    @Transactional
    public Long saveNotification(FcmSendRequest request) {
        FcmRecipient recipient = request.recipient();
        Notification notification = notificationRepository.save(Notification.builder()
            .user(userRepository.getReferenceById(recipient.userId()))
            .senior(seniorRepository.getReferenceById(recipient.seniorId()))
            .type(request.type())
            .message(request.body())
            .build());
        return notification.getId();
    }

    /**
     * 영구 무효로 판별된 토큰을 비활성화한다. <b>발송 후에</b> 호출한다.
     * <p>
     * 발송이 트랜잭션 밖이라 조회한 토큰 엔티티에 dirty checking이 걸리지 않으므로,
     * 엔티티가 아니라 id를 받아 벌크 UPDATE로 반영한다.
     *
     * @param invalidTokenIds 비활성화할 토큰 id — 비어 있지 않을 때만 호출한다(불필요한 트랜잭션 방지)
     */
    @Transactional
    public void deactivateInvalidTokens(List<Long> invalidTokenIds) {
        fcmTokenRepository.deactivateAllByIdIn(invalidTokenIds);
    }
}