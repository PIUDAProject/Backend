package com.piuda.callcare.domain.notification.service.query;

import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.MEDICATION_1_ID;
import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.SENIOR_ID;
import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.SENIOR_NAME;
import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.USER_ID;
import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.createGuardian;
import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.createNotification;
import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.createSenior;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.piuda.callcare.domain.notification.converter.NotificationConverter;
import com.piuda.callcare.domain.notification.dto.response.NotificationResponse;
import com.piuda.callcare.domain.notification.entity.Notification;
import com.piuda.callcare.domain.notification.enums.NotificationType;
import com.piuda.callcare.domain.notification.repository.NotificationRepository;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationQueryService 단위 테스트")
class NotificationQueryServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 8, 20, 9, 0);

    @InjectMocks private NotificationQueryService notificationQueryService;

    @Mock private NotificationRepository notificationRepository;
    // 변환은 순수 매핑이라 실제 구현을 주입한다(딥링크 값이 실제로 실리는지까지 보려면 목으로는 부족하다)
    @Spy private NotificationConverter notificationConverter = new NotificationConverter();

    @Test
    @DisplayName("저장된 3종(전화 미수신·약 부족·약물 충돌)이 모두 목록에 내려간다")
    void getNotifications_includesAllThreeTypes() {
        // Given
        User guardian = createGuardian();
        Senior senior = createSenior();
        given(notificationRepository.findAllWithSeniorByUserId(USER_ID)).willReturn(List.of(
                createNotification(3L, guardian, senior, NotificationType.DRUG_CONFLICT, "충돌", null, BASE.plusHours(2)),
                createNotification(2L, guardian, senior, NotificationType.LOW_STOCK, "소진", MEDICATION_1_ID, BASE.plusHours(1)),
                createNotification(1L, guardian, senior, NotificationType.MISSED_CALL, "미수신", null, BASE)
        ));

        // When
        List<NotificationResponse> result = notificationQueryService.getNotifications(USER_ID);

        // Then
        assertThat(result).extracting(NotificationResponse::type)
                .containsExactly(NotificationType.DRUG_CONFLICT, NotificationType.LOW_STOCK, NotificationType.MISSED_CALL);
    }

    @Test
    @DisplayName("여러 어르신의 알림이 조회 순서(최신순) 그대로 섞여 내려간다")
    void getNotifications_keepsRepositoryOrderAcrossSeniors() {
        // Given - 같은 보호자가 돌보는 어르신 둘
        User guardian = createGuardian();
        Senior first = createSenior();
        Senior second = createSenior();
        ReflectionTestUtils.setField(second, "id", 11L);
        ReflectionTestUtils.setField(second, "name", "박영수");

        given(notificationRepository.findAllWithSeniorByUserId(USER_ID)).willReturn(List.of(
                createNotification(2L, guardian, second, NotificationType.LOW_STOCK, "소진", MEDICATION_1_ID, BASE.plusHours(1)),
                createNotification(1L, guardian, first, NotificationType.MISSED_CALL, "미수신", null, BASE)
        ));

        // When
        List<NotificationResponse> result = notificationQueryService.getNotifications(USER_ID);

        // Then - 정렬은 쿼리의 책임이고, 서비스는 그 순서를 바꾸지 않는다
        assertThat(result).extracting(NotificationResponse::seniorId).containsExactly(11L, SENIOR_ID);
        assertThat(result).extracting(NotificationResponse::seniorName).containsExactly("박영수", SENIOR_NAME);
    }

    @Test
    @DisplayName("딥링크: 소진 알림만 medicationId가 실리고 나머지 유형은 null이다")
    void getNotifications_medicationIdOnlyForLowStock() {
        // Given
        User guardian = createGuardian();
        Senior senior = createSenior();
        given(notificationRepository.findAllWithSeniorByUserId(USER_ID)).willReturn(List.of(
                createNotification(2L, guardian, senior, NotificationType.LOW_STOCK, "소진", MEDICATION_1_ID, BASE.plusHours(1)),
                createNotification(1L, guardian, senior, NotificationType.DRUG_CONFLICT, "충돌", null, BASE)
        ));

        // When
        List<NotificationResponse> result = notificationQueryService.getNotifications(USER_ID);

        // Then
        assertThat(result).extracting(NotificationResponse::medicationId).containsExactly(MEDICATION_1_ID, null);
    }

    @Test
    @DisplayName("읽지 않은 알림은 isRead=false, readAt=null로 내려간다")
    void getNotifications_unreadFields() {
        // Given
        given(notificationRepository.findAllWithSeniorByUserId(USER_ID)).willReturn(List.of(
                createNotification(1L, createGuardian(), createSenior(), NotificationType.LOW_STOCK, "소진", MEDICATION_1_ID, BASE)
        ));

        // When
        NotificationResponse result = notificationQueryService.getNotifications(USER_ID).get(0);

        // Then
        assertThat(result.isRead()).isFalse();
        assertThat(result.readAt()).isNull();
        assertThat(result.typeLabel()).isEqualTo("약 부족");
    }

    @Test
    @DisplayName("알림이 없으면 빈 목록을 반환한다")
    void getNotifications_empty() {
        // Given
        given(notificationRepository.findAllWithSeniorByUserId(USER_ID)).willReturn(List.<Notification>of());

        // When / Then
        assertThat(notificationQueryService.getNotifications(USER_ID)).isEmpty();
    }
}
