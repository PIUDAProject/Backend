package com.piuda.callcare.domain.senior.service.command;

import com.piuda.callcare.domain.senior.converter.SeniorConverter;
import com.piuda.callcare.domain.senior.dto.request.SeniorCreateRequest;
import com.piuda.callcare.domain.senior.dto.request.SeniorMealTimeUpdateRequest;
import com.piuda.callcare.domain.senior.dto.response.SeniorResponse;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.domain.senior.service.PhoneVerificationService;
import com.piuda.callcare.domain.user.entity.User;
import com.piuda.callcare.domain.user.repository.UserRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeniorCommandService {

    private final SeniorRepository seniorRepository;
    private final UserRepository userRepository;
    private final SeniorConverter seniorConverter;
    private final PhoneVerificationService phoneVerificationService;

    @Transactional
    public SeniorResponse register(Long userId, SeniorCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.USER_NOT_FOUND));

        // 문자 인증 확인 (coolsms로 발송된 코드를 Redis에 저장해두고 검증)
        boolean verified = phoneVerificationService.verifyCode(request.phoneNumber(), request.verificationCode());
        if (!verified) {
            throw new CallCareException(ErrorCode.PHONE_VERIFICATION_FAILED);
        }

        Senior senior = seniorConverter.toEntity(user, request);
        Senior saved = seniorRepository.save(senior);
        log.info("부모님 정보 등록 완료 - seniorId: {}, userId: {}", saved.getId(), userId);

        return seniorConverter.toResponse(saved);
    }

    @Transactional
    public SeniorResponse updateMealTime(Long userId, Long seniorId, SeniorMealTimeUpdateRequest request) {
        Senior senior = seniorRepository.findByIdAndUser_Id(seniorId, userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));

        senior.updateMealTime(request.breakfastTime(), request.lunchTime(), request.dinnerTime());
        log.info("생활 시간 수정 완료 - seniorId: {}, userId: {}", seniorId, userId);

        return seniorConverter.toResponse(senior);
    }
}
