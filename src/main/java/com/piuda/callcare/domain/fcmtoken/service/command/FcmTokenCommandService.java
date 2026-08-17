package com.piuda.callcare.domain.fcmtoken.service.command;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.fcmtoken.converter.FcmTokenConverter;
import com.piuda.callcare.domain.fcmtoken.dto.request.FcmTokenRegisterRequest;
import com.piuda.callcare.domain.fcmtoken.dto.response.FcmTokenResponse;
import com.piuda.callcare.domain.fcmtoken.entity.FcmToken;
import com.piuda.callcare.domain.fcmtoken.repository.FcmTokenRepository;
import com.piuda.callcare.domain.user.entity.User;
import com.piuda.callcare.domain.user.repository.UserRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FCM 토큰 등록/해제.
 * <p>
 * 발송({@code FcmSendService})은 활성 토큰만 대상으로 하므로, 이 서비스가 그 대상 목록의 입구다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FcmTokenCommandService {

    private final FcmTokenRepository fcmTokenRepository;
    private final UserRepository userRepository;
    private final FcmTokenConverter fcmTokenConverter;

    /**
     * 토큰 등록 — 같은 토큰 값은 항상 한 행만 유지한다(upsert).
     * 이미 있으면 소유자·기기 종류를 갱신하고 재활성화한다. 한 기기에서 계정을 바꿔 로그인하면
     * 소유자가 바뀌어야 이전 사용자에게 갈 푸시가 새 사용자 폰에 뜨지 않는다.
     */
    public FcmTokenResponse register(Long userId, FcmTokenRegisterRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.USER_NOT_FOUND));

        FcmToken fcmToken = fcmTokenRepository.findByToken(request.token())
                .map(existing -> {
                    logIfOwnerChanged(existing, user);
                    existing.renew(user, request.deviceType()); // dirty checking
                    return existing;
                })
                .orElseGet(() -> fcmTokenRepository.save(FcmToken.builder()
                        .user(user)
                        .token(request.token())
                        .deviceType(request.deviceType())
                        .build()));

        return fcmTokenConverter.toResponse(fcmToken);
    }

    /**
     * 토큰 해제(로그아웃·기기 제거) — 멱등 no-op.
     * 없는 토큰이나 남의 토큰이면 예외 없이 아무것도 하지 않는다. 로그아웃은 재시도되는 경로라
     * "이미 해제됨"이 오류가 아니고, 예외로 응답하면 토큰 존재 여부가 외부에 드러난다.
     */
    public void deactivate(Long userId, String token) {
        Optional<FcmToken> found = fcmTokenRepository.findByToken(token);
        if (found.isEmpty()) {
            return;
        }

        FcmToken fcmToken = found.get();
        if (!fcmToken.getUser().getId().equals(userId)) {
            log.warn("[FCM] 다른 사용자의 토큰 해제 시도 — 무시합니다 (requesterId={}, tokenId={})", userId, fcmToken.getId());
            return;
        }

        fcmToken.deactivate(); // dirty checking
    }

    // 기기 소유자가 바뀌는 시점을 남긴다. 오배송 신고가 들어왔을 때 "이 기기가 언제 누구에게 넘어갔나"가
    // 1차 단서인데, 소유자 교체는 renew 안에서 조용히 일어나 추적할 흔적이 없다.
    private void logIfOwnerChanged(FcmToken existing, User newOwner) {
        Long previousUserId = existing.getUser().getId();
        if (!previousUserId.equals(newOwner.getId())) {
            log.info("[FCM] 기기 소유자 변경 — 토큰을 새 사용자로 재할당합니다 (tokenId={}, previousUserId={}, newUserId={})",
                    existing.getId(), previousUserId, newOwner.getId());
        }
    }
}
