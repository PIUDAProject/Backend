package com.piuda.callcare.global.config.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.piuda.callcare.domain.fcmtoken.entity.FcmToken;
import com.piuda.callcare.domain.fcmtoken.enums.DeviceType;
import com.piuda.callcare.domain.fcmtoken.repository.FcmTokenRepository;
import com.piuda.callcare.domain.notification.enums.NotificationType;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmSendService 단위 테스트")
class FcmSendServiceTest {

    private static final Long NOTIFICATION_ID = 100L;

    @Mock private ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    @Mock private FirebaseMessaging firebaseMessaging;
    @Mock private FcmTokenRepository fcmTokenRepository;
    @Mock private FcmSendRecorder fcmSendRecorder;

    @InjectMocks private FcmSendService fcmSendService;

    @Captor private ArgumentCaptor<List<Long>> invalidTokenIdsCaptor;
    @Captor private ArgumentCaptor<MulticastMessage> messageCaptor;

    @Test
    @DisplayName("미설정: FirebaseMessaging 빈이 없으면 발송을 건너뛰고 이력도 남기지 않는다")
    void send_skips_whenNotConfigured() {
        // Given
        given(firebaseMessagingProvider.getIfAvailable()).willReturn(null);

        // When
        FcmSendResult result = fcmSendService.send(request(recipient(1L, 10L)));

        // Then
        assertThat(result.status()).isEqualTo(FcmSendStatus.NOT_CONFIGURED);
        then(fcmTokenRepository).shouldHaveNoInteractions();
        then(fcmSendRecorder).shouldHaveNoInteractions();
        then(firebaseMessaging).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("발송 대상: 수신자 본인의 활성 토큰만 조회한다")
    void send_looksUpTokensOfRecipientOnly() throws FirebaseMessagingException {
        // Given
        givenConfigured();
        givenNotificationSaved();
        given(fcmTokenRepository.findByUser_IdAndIsActiveTrue(anyLong()))
            .willReturn(new ArrayList<>(List.of(token(1L, "t0"))));
        BatchResponse response = batchResponse(success());
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(response);

        // When
        fcmSendService.send(request(recipient(7L, 70L)));

        // Then
        then(fcmTokenRepository).should(times(1)).findByUser_IdAndIsActiveTrue(7L);
    }

    @Test
    @DisplayName("청크 분할: 활성 토큰 501개면 multicast를 2회 나눠 보낸다")
    void send_splitsIntoChunks_whenOver500() throws FirebaseMessagingException {
        // Given
        givenConfigured();
        givenNotificationSaved();
        given(fcmTokenRepository.findByUser_IdAndIsActiveTrue(anyLong())).willReturn(tokens(501));
        BatchResponse response = emptyBatchResponse();
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(response);

        // When
        fcmSendService.send(request(recipient(1L, 10L)));

        // Then
        then(firebaseMessaging).should(times(2)).sendEachForMulticast(any());
    }

    @Test
    @DisplayName("청크 경계: 활성 토큰 500개면 multicast를 1회만 보낸다")
    void send_singleChunk_at500() throws FirebaseMessagingException {
        // Given
        givenConfigured();
        givenNotificationSaved();
        given(fcmTokenRepository.findByUser_IdAndIsActiveTrue(anyLong())).willReturn(tokens(500));
        BatchResponse response = emptyBatchResponse();
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(response);

        // When
        fcmSendService.send(request(recipient(1L, 10L)));

        // Then
        then(firebaseMessaging).should(times(1)).sendEachForMulticast(any());
    }

    @Test
    @DisplayName("무효 토큰: UNREGISTERED 응답을 받은 토큰의 id만 정리 대상으로 넘긴다")
    void send_reportsInvalidTokenOnly() throws FirebaseMessagingException {
        // Given
        givenConfigured();
        givenNotificationSaved();
        given(fcmTokenRepository.findByUser_IdAndIsActiveTrue(anyLong()))
            .willReturn(new ArrayList<>(List.of(token(1L, "t0"), token(2L, "t1"), token(3L, "t2"))));
        BatchResponse response = batchResponse(success(), failure(MessagingErrorCode.UNREGISTERED), success());
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(response);

        // When
        FcmSendResult result = fcmSendService.send(request(recipient(1L, 10L)));

        // Then
        then(fcmSendRecorder).should().deactivateInvalidTokens(invalidTokenIdsCaptor.capture());
        assertThat(invalidTokenIdsCaptor.getValue()).containsExactly(2L);
        assertThat(result.status()).isEqualTo(FcmSendStatus.SENT);
        assertThat(result.deactivatedTokenCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("일시 실패: UNAVAILABLE 응답 토큰은 정리 대상에 넣지 않고, 전 건 실패이므로 SEND_FAILED다")
    void send_keepsToken_onTemporaryFailure() throws FirebaseMessagingException {
        // Given
        givenConfigured();
        givenNotificationSaved();
        given(fcmTokenRepository.findByUser_IdAndIsActiveTrue(anyLong()))
            .willReturn(new ArrayList<>(List.of(token(1L, "t0"))));
        BatchResponse response = batchResponse(failure(MessagingErrorCode.UNAVAILABLE));
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(response);

        // When
        FcmSendResult result = fcmSendService.send(request(recipient(1L, 10L)));

        // Then
        then(fcmSendRecorder).should(never()).deactivateInvalidTokens(anyList());
        assertThat(result.deactivatedTokenCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(1);
        // 호출자가 재시도·폴백을 판단할 수 있어야 하므로 건수뿐 아니라 상태로도 실패가 드러나야 한다
        assertThat(result.status()).isEqualTo(FcmSendStatus.SEND_FAILED);
    }

    @Test
    @DisplayName("payload 오류 방어: INVALID_ARGUMENT는 토큰을 비활성화하지 않는다")
    void send_keepsToken_onInvalidArgument() throws FirebaseMessagingException {
        // Given — INVALID_ARGUMENT는 payload 오류에도 오므로, 이걸로 토큰을 지우면
        // 잘못된 data 한 번에 그 사용자의 모든 기기가 발송 대상에서 사라진다
        givenConfigured();
        givenNotificationSaved();
        given(fcmTokenRepository.findByUser_IdAndIsActiveTrue(anyLong()))
            .willReturn(new ArrayList<>(List.of(token(1L, "t0"), token(2L, "t1"))));
        BatchResponse response = batchResponse(
            failure(MessagingErrorCode.INVALID_ARGUMENT), failure(MessagingErrorCode.INVALID_ARGUMENT));
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(response);

        // When
        FcmSendResult result = fcmSendService.send(request(recipient(1L, 10L)));

        // Then
        then(fcmSendRecorder).should(never()).deactivateInvalidTokens(anyList());
        assertThat(result.deactivatedTokenCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("상태: 청크 전체가 예외로 실패해도 SEND_FAILED로 집계된다")
    void send_returnsSendFailed_whenChunkThrows() throws FirebaseMessagingException {
        // Given
        givenConfigured();
        givenNotificationSaved();
        given(fcmTokenRepository.findByUser_IdAndIsActiveTrue(anyLong()))
            .willReturn(new ArrayList<>(List.of(token(1L, "t0"), token(2L, "t1"))));
        given(firebaseMessaging.sendEachForMulticast(any())).willThrow(mock(FirebaseMessagingException.class));

        // When
        FcmSendResult result = fcmSendService.send(request(recipient(1L, 10L)));

        // Then
        assertThat(result.status()).isEqualTo(FcmSendStatus.SEND_FAILED);
        assertThat(result.failureCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("상태: 활성 토큰이 없으면 NO_TOKEN — 발송은 건너뛰되 이력은 남긴다")
    void send_returnsNoToken_whenNoActiveToken() {
        // Given
        givenConfigured();
        givenNotificationSaved();
        given(fcmTokenRepository.findByUser_IdAndIsActiveTrue(anyLong())).willReturn(new ArrayList<>());

        // When
        FcmSendResult result = fcmSendService.send(request(recipient(1L, 10L)));

        // Then
        assertThat(result.status()).isEqualTo(FcmSendStatus.NO_TOKEN);
        then(fcmSendRecorder).should(times(1)).saveNotification(any());
        then(firebaseMessaging).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("payload: 발송 전에 저장한 notificationId와 type을 data에 실어 보낸다")
    void send_putsNotificationIdAndTypeIntoData() throws FirebaseMessagingException {
        // Given
        givenConfigured();
        givenNotificationSaved();
        given(fcmTokenRepository.findByUser_IdAndIsActiveTrue(anyLong()))
            .willReturn(new ArrayList<>(List.of(token(1L, "t0"))));
        BatchResponse response = batchResponse(success());
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(response);

        // When
        fcmSendService.send(new FcmSendRequest(
            NotificationType.LOW_STOCK, "제목", "본문", recipient(1L, 10L), Map.of("seniorId", "10")));

        // Then
        then(firebaseMessaging).should().sendEachForMulticast(messageCaptor.capture());
        assertThat(dataOf(messageCaptor.getValue()))
            .containsEntry("notificationId", String.valueOf(NOTIFICATION_ID))
            .containsEntry("type", NotificationType.LOW_STOCK.name())
            .containsEntry("seniorId", "10"); // 호출자가 넣은 딥링크 키는 그대로 유지된다
    }

    // --- helpers ---

    private void givenConfigured() {
        given(firebaseMessagingProvider.getIfAvailable()).willReturn(firebaseMessaging);
    }

    private void givenNotificationSaved() {
        given(fcmSendRecorder.saveNotification(any())).willReturn(NOTIFICATION_ID);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> dataOf(MulticastMessage message) {
        return (Map<String, String>) ReflectionTestUtils.getField(message, "data");
    }

    private FcmSendRequest request(FcmRecipient recipient) {
        return new FcmSendRequest(NotificationType.LOW_STOCK, "제목", "본문", recipient, Map.of());
    }

    private FcmRecipient recipient(Long userId, Long seniorId) {
        return new FcmRecipient(userId, seniorId);
    }

    private FcmToken token(Long id, String value) {
        FcmToken fcmToken = FcmToken.builder().token(value).deviceType(DeviceType.ANDROID).build();
        ReflectionTestUtils.setField(fcmToken, "id", id);
        return fcmToken;
    }

    private List<FcmToken> tokens(int count) {
        List<FcmToken> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(token((long) i, "token-" + i));
        }
        return list;
    }

    private BatchResponse emptyBatchResponse() {
        BatchResponse response = mock(BatchResponse.class);
        given(response.getResponses()).willReturn(List.of());
        return response;
    }

    private BatchResponse batchResponse(SendResponse... responses) {
        BatchResponse response = mock(BatchResponse.class);
        given(response.getResponses()).willReturn(List.of(responses));
        return response;
    }

    private SendResponse success() {
        SendResponse sendResponse = mock(SendResponse.class);
        given(sendResponse.isSuccessful()).willReturn(true);
        return sendResponse;
    }

    private SendResponse failure(MessagingErrorCode code) {
        SendResponse sendResponse = mock(SendResponse.class);
        given(sendResponse.isSuccessful()).willReturn(false);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(code);
        given(sendResponse.getException()).willReturn(exception);
        return sendResponse;
    }
}