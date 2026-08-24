package com.piuda.callcare.global.config.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.piuda.callcare.domain.fcmtoken.repository.FcmTokenRepository;
import com.piuda.callcare.domain.notification.entity.Notification;
import com.piuda.callcare.domain.notification.enums.NotificationType;
import com.piuda.callcare.domain.notification.repository.NotificationRepository;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.domain.user.entity.User;
import com.piuda.callcare.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmSendRecorder 단위 테스트")
class FcmSendRecorderTest {

    @Mock private FcmTokenRepository fcmTokenRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private SeniorRepository seniorRepository;

    @InjectMocks private FcmSendRecorder fcmSendRecorder;

    @Captor private ArgumentCaptor<Notification> notificationCaptor;

    @Test
    @DisplayName("이력: recipient의 user·senior에 type과 body(message)로 Notification 1건을 저장한다")
    void saveNotification_savesForRecipient() {
        // Given
        User user = stubUser();
        Senior senior = stubSenior();
        stubSavedNotification(100L);

        // When
        fcmSendRecorder.saveNotification(request());

        // Then
        then(notificationRepository).should(times(1)).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getSenior()).isSameAs(senior);
        assertThat(saved.getType()).isEqualTo(NotificationType.LOW_STOCK);
        assertThat(saved.getMessage()).isEqualTo("본문");
        assertThat(saved.getIsRead()).isFalse();
    }

    @Test
    @DisplayName("이력: 저장된 Notification의 id를 반환한다 — 이 값이 푸시 payload에 실린다")
    void saveNotification_returnsSavedId() {
        // Given
        stubUser();
        stubSenior();
        stubSavedNotification(100L);

        // When
        Long notificationId = fcmSendRecorder.saveNotification(request());

        // Then
        assertThat(notificationId).isEqualTo(100L);
    }

    @Test
    @DisplayName("이력: 딥링크 대상 약(targetMedicationId)을 함께 저장한다 — 목록에서 딥링크를 복원하려면 DB에 있어야 한다")
    void saveNotification_savesTargetMedicationId() {
        // Given
        stubUser();
        stubSenior();
        stubSavedNotification(100L);

        // When
        fcmSendRecorder.saveNotification(new FcmSendRequest(NotificationType.LOW_STOCK, "제목", "본문",
            new FcmRecipient(1L, 10L), Map.of(), 777L));

        // Then
        then(notificationRepository).should(times(1)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getMedicationId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("이력: 가리킬 약이 없는 알림(충돌·전화 미수신)은 medicationId가 null로 저장된다")
    void saveNotification_nullMedicationId_whenNoTarget() {
        // Given
        stubUser();
        stubSenior();
        stubSavedNotification(100L);

        // When
        fcmSendRecorder.saveNotification(request());

        // Then
        then(notificationRepository).should(times(1)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getMedicationId()).isNull();
    }

    @Test
    @DisplayName("무효 토큰: 넘겨받은 id만 벌크 비활성화한다")
    void deactivateInvalidTokens_deactivatesGivenIds() {
        // When
        fcmSendRecorder.deactivateInvalidTokens(List.of(2L, 5L));

        // Then
        then(fcmTokenRepository).should(times(1)).deactivateAllByIdIn(List.of(2L, 5L));
    }

    // --- helpers ---

    private FcmSendRequest request() {
        return new FcmSendRequest(NotificationType.LOW_STOCK, "제목", "본문", new FcmRecipient(1L, 10L), Map.of());
    }

    private User stubUser() {
        User user = mock(User.class);
        given(userRepository.getReferenceById(1L)).willReturn(user);
        return user;
    }

    private Senior stubSenior() {
        Senior senior = mock(Senior.class);
        given(seniorRepository.getReferenceById(10L)).willReturn(senior);
        return senior;
    }

    private void stubSavedNotification(Long id) {
        given(notificationRepository.save(any(Notification.class)))
            .willAnswer(invocation -> {
                Notification notification = invocation.getArgument(0);
                ReflectionTestUtils.setField(notification, "id", id);
                return notification;
            });
    }
}