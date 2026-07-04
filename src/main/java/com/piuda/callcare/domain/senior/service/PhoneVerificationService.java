package com.piuda.callcare.domain.senior.service;

import com.piuda.callcare.domain.senior.service.sms.SmsSender;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private static final int CODE_LENGTH = 6;
    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final String KEY_PREFIX = "SMS:AUTH:";
    private static final String COOLDOWN_PREFIX = "SMS:COOLDOWN:";
    private static final String ATTEMPT_PREFIX = "SMS:ATTEMPT:";

    private final SmsSender smsSender;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${coolsms.sender}")
    private String sender;

    public void sendVerificationCode(String phoneNumber) {
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);

        // 재전송 남용/문자 폭탄 방지: 같은 번호로는 쿨다운 동안 재발송 불가
        Boolean cooldownSet = redisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_PREFIX + normalizedPhoneNumber, "1", RESEND_COOLDOWN);
        if (Boolean.FALSE.equals(cooldownSet)) {
            throw new CallCareException(ErrorCode.PHONE_VERIFICATION_TOO_MANY_REQUESTS);
        }

        String code = generateRandomCode();
        String text = "[콜케어 인증번호] " + code + "\n본인 확인을 위해 인증번호를 입력해주세요.";

        try {
            smsSender.send(normalizePhoneNumber(sender), normalizedPhoneNumber, text);
            redisTemplate.opsForValue().set(KEY_PREFIX + normalizedPhoneNumber, code, CODE_TTL);
            // 이전 시도 횟수 초기화
            redisTemplate.delete(ATTEMPT_PREFIX + normalizedPhoneNumber);
            log.info("SMS 인증번호 발송 완료 - phoneNumber: {}", maskedPhoneNumber(normalizedPhoneNumber));
        } catch (Exception e) {
            // 발송 실패 시에는 다음 재시도가 쿨다운에 막히지 않도록 해제
            redisTemplate.delete(COOLDOWN_PREFIX + normalizedPhoneNumber);
            log.error("SMS 인증번호 발송 실패 - phoneNumber: {}, error: {}",
                    maskedPhoneNumber(normalizedPhoneNumber), e.getMessage(), e);
            throw new CallCareException(ErrorCode.PHONE_VERIFICATION_SEND_FAILED);
        }
    }


    public boolean verifyCode(String phoneNumber, String code) {
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        String attemptKey = ATTEMPT_PREFIX + normalizedPhoneNumber;
        String codeKey = KEY_PREFIX + normalizedPhoneNumber;

        String attemptCountValue = redisTemplate.opsForValue().get(attemptKey);
        int attemptCount = attemptCountValue == null ? 0 : Integer.parseInt(attemptCountValue);
        if (attemptCount >= MAX_VERIFY_ATTEMPTS) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptKey);
            throw new CallCareException(ErrorCode.PHONE_VERIFICATION_ATTEMPTS_EXCEEDED);
        }

        String savedCode = redisTemplate.opsForValue().get(codeKey);
        if (savedCode == null) {
            throw new CallCareException(ErrorCode.PHONE_VERIFICATION_CODE_EXPIRED);
        }

        boolean matched = savedCode.equals(code);
        if (matched) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptKey);
        } else {
            Long updatedAttempts = redisTemplate.opsForValue().increment(attemptKey);
            redisTemplate.expire(attemptKey, CODE_TTL);
            log.warn("SMS 인증번호 불일치 - phoneNumber: {}, attempt: {}",
                    maskedPhoneNumber(normalizedPhoneNumber), updatedAttempts);
        }

        return matched;
    }

    private String generateRandomCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String normalized = phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
        if (normalized.isBlank()) {
            throw new CallCareException(ErrorCode.INVALID_PARAMETER);
        }
        return normalized;
    }

    private String maskedPhoneNumber(String phoneNumber) {
        if (phoneNumber.length() < 7) {
            return "****";
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
