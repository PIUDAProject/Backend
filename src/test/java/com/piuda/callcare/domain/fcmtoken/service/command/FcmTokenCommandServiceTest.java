package com.piuda.callcare.domain.fcmtoken.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.piuda.callcare.domain.fcmtoken.converter.FcmTokenConverter;
import com.piuda.callcare.domain.fcmtoken.dto.request.FcmTokenRegisterRequest;
import com.piuda.callcare.domain.fcmtoken.dto.response.FcmTokenResponse;
import com.piuda.callcare.domain.fcmtoken.entity.FcmToken;
import com.piuda.callcare.domain.fcmtoken.enums.DeviceType;
import com.piuda.callcare.domain.fcmtoken.repository.FcmTokenRepository;
import com.piuda.callcare.domain.user.entity.User;
import com.piuda.callcare.domain.user.enums.Provider;
import com.piuda.callcare.domain.user.repository.UserRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmTokenCommandService 단위 테스트")
class FcmTokenCommandServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FCM_TOKEN_ID = 10L;
    private static final String TOKEN = "fcm-registration-token-abc";

    @InjectMocks
    private FcmTokenCommandService fcmTokenCommandService;

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private FcmTokenConverter fcmTokenConverter;

    @Test
    @DisplayName("정상 케이스: 처음 보는 토큰이면 새로 저장한다")
    void register_saves_when_token_is_new() {
        // Given
        User user = createUser(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(fcmTokenRepository.findByToken(TOKEN)).willReturn(Optional.empty());
        given(fcmTokenRepository.save(any(FcmToken.class))).willAnswer(invocation -> invocation.getArgument(0));

        // When
        fcmTokenCommandService.register(USER_ID, new FcmTokenRegisterRequest(TOKEN, DeviceType.ANDROID));

        // Then
        ArgumentCaptor<FcmToken> captor = ArgumentCaptor.forClass(FcmToken.class);
        then(fcmTokenRepository).should(times(1)).save(captor.capture());
        FcmToken saved = captor.getValue();
        assertThat(saved.getToken()).isEqualTo(TOKEN);
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getDeviceType()).isEqualTo(DeviceType.ANDROID);
        assertThat(saved.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("정상 케이스: 같은 토큰을 다시 등록해도 새 행을 만들지 않고 같은 ID를 반환한다")
    void register_does_not_save_when_token_already_exists() {
        // Given
        User user = createUser(USER_ID);
        FcmToken existing = createFcmToken(user, FCM_TOKEN_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(fcmTokenRepository.findByToken(TOKEN)).willReturn(Optional.of(existing));

        // When
        FcmTokenResponse result = fcmTokenCommandService.register(USER_ID, new FcmTokenRegisterRequest(TOKEN, DeviceType.ANDROID));

        // Then
        then(fcmTokenRepository).should(never()).save(any(FcmToken.class));
        assertThat(result.fcmTokenId()).isEqualTo(FCM_TOKEN_ID);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("정상 케이스: 다른 사용자가 같은 기기로 로그인하면 토큰 소유자가 교체된다")
    void register_reassigns_owner_when_token_belongs_to_another_user() {
        // Given
        FcmToken existing = createFcmToken(createUser(OTHER_USER_ID), FCM_TOKEN_ID);
        User newOwner = createUser(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(newOwner));
        given(fcmTokenRepository.findByToken(TOKEN)).willReturn(Optional.of(existing));

        // When
        fcmTokenCommandService.register(USER_ID, new FcmTokenRegisterRequest(TOKEN, DeviceType.IOS));

        // Then
        assertThat(existing.getUser()).isEqualTo(newOwner);
        assertThat(existing.getDeviceType()).isEqualTo(DeviceType.IOS);
    }

    @Test
    @DisplayName("정상 케이스: 해제됐던 토큰을 재등록하면 다시 발송 대상이 된다")
    void register_reactivates_deactivated_token() {
        // Given
        User user = createUser(USER_ID);
        FcmToken existing = createFcmToken(user, FCM_TOKEN_ID);
        existing.deactivate();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(fcmTokenRepository.findByToken(TOKEN)).willReturn(Optional.of(existing));

        // When
        fcmTokenCommandService.register(USER_ID, new FcmTokenRegisterRequest(TOKEN, DeviceType.ANDROID));

        // Then
        assertThat(existing.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("예외 케이스: 존재하지 않는 사용자면 예외가 발생한다")
    void register_throws_when_user_not_found() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> fcmTokenCommandService.register(USER_ID, new FcmTokenRegisterRequest(TOKEN, DeviceType.ANDROID)))
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        then(fcmTokenRepository).should(never()).save(any(FcmToken.class));
    }

    @Test
    @DisplayName("정상 케이스: 본인 토큰을 해제하면 발송 대상에서 빠진다")
    void deactivate_marks_token_inactive_for_owner() {
        // Given
        FcmToken existing = createFcmToken(createUser(USER_ID), FCM_TOKEN_ID);
        given(fcmTokenRepository.findByToken(TOKEN)).willReturn(Optional.of(existing));

        // When
        fcmTokenCommandService.deactivate(USER_ID, TOKEN);

        // Then
        assertThat(existing.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("정상 케이스: 없는 토큰을 해제해도 예외 없이 무시한다")
    void deactivate_is_noop_when_token_not_found() {
        // Given
        given(fcmTokenRepository.findByToken(anyString())).willReturn(Optional.empty());

        // When & Then
        assertThatCode(() -> fcmTokenCommandService.deactivate(USER_ID, TOKEN)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정상 케이스: 다른 사용자의 토큰은 해제되지 않는다")
    void deactivate_does_not_touch_another_users_token() {
        // Given
        FcmToken othersToken = createFcmToken(createUser(OTHER_USER_ID), FCM_TOKEN_ID);
        given(fcmTokenRepository.findByToken(TOKEN)).willReturn(Optional.of(othersToken));

        // When
        fcmTokenCommandService.deactivate(USER_ID, TOKEN);

        // Then
        assertThat(othersToken.getIsActive()).isTrue();
    }

    private User createUser(Long userId) {
        User user = User.builder()
                .email("test" + userId + "@callcare.com")
                .provider(Provider.KAKAO)
                .providerId("provider-" + userId)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private FcmToken createFcmToken(User user, Long fcmTokenId) {
        FcmToken fcmToken = FcmToken.builder()
                .user(user)
                .token(TOKEN)
                .deviceType(DeviceType.ANDROID)
                .build();
        ReflectionTestUtils.setField(fcmToken, "id", fcmTokenId);
        return fcmToken;
    }
}
