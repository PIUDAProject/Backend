package com.piuda.callcare.domain.notification.service.command;

import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.MEDICATION_1_ID;
import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.USER_ID;
import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.createGuardian;
import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.createNotification;
import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.createSenior;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.piuda.callcare.domain.notification.converter.NotificationConverter;
import com.piuda.callcare.domain.notification.dto.response.NotificationResponse;
import com.piuda.callcare.domain.notification.entity.Notification;
import com.piuda.callcare.domain.notification.enums.NotificationType;
import com.piuda.callcare.domain.notification.repository.NotificationRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationCommandService 단위 테스트")
class NotificationCommandServiceTest {

    private static final Long NOTIFICATION_ID = 1L;
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 8, 20, 9, 0);

    @InjectMocks private NotificationCommandService notificationCommandService;

    @Mock private NotificationRepository notificationRepository;
    @Spy private NotificationConverter notificationConverter = new NotificationConverter();

    @Test
    @DisplayName("읽음 처리하면 isRead=true, readAt이 채워진 응답을 돌려준다")
    void markAsRead_marksAndReturns() {
        // Given
        Notification notification = notification();
        given(notificationRepository.findByIdAndUser_Id(NOTIFICATION_ID, USER_ID))
                .willReturn(Optional.of(notification));

        // When
        NotificationResponse result = notificationCommandService.markAsRead(USER_ID, NOTIFICATION_ID);

        // Then
        assertThat(result.isRead()).isTrue();
        assertThat(result.readAt()).isNotNull();
        assertThat(notification.getIsRead()).isTrue();
        then(notificationRepository).should(times(1)).findByIdAndUser_Id(NOTIFICATION_ID, USER_ID);
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽음 처리해도 처음 읽은 시각이 유지된다")
    void markAsRead_isIdempotent() {
        // Given - 이미 한 번 읽은 알림
        Notification notification = notification();
        notification.markAsRead();
        LocalDateTime firstReadAt = notification.getReadAt();
        given(notificationRepository.findByIdAndUser_Id(NOTIFICATION_ID, USER_ID))
                .willReturn(Optional.of(notification));

        // When - 푸시 탭과 목록 열람이 같은 알림을 두 번 읽음 처리하는 상황
        NotificationResponse result = notificationCommandService.markAsRead(USER_ID, NOTIFICATION_ID);

        // Then
        assertThat(result.readAt()).isEqualTo(firstReadAt);
    }

    @Test
    @DisplayName("예외: 남의 알림이거나 없는 알림이면 NOTIFICATION_NOT_FOUND 예외가 발생한다")
    void markAsRead_throws_when_notOwnedOrMissing() {
        // Given - 소유자 조건이 걸린 조회라 남의 알림도 빈 결과로 온다
        given(notificationRepository.findByIdAndUser_Id(NOTIFICATION_ID, USER_ID))
                .willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> notificationCommandService.markAsRead(USER_ID, NOTIFICATION_ID))
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    private Notification notification() {
        return createNotification(NOTIFICATION_ID, createGuardian(), createSenior(),
                NotificationType.LOW_STOCK, "소진 임박", MEDICATION_1_ID, BASE);
    }
}
