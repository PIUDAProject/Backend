package com.piuda.callcare.domain.senior.service;

import com.piuda.callcare.domain.senior.service.sms.SmsSender;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import com.piuda.callcare.global.util.PhoneMaskUtil;
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

    @Value("${coolsms.mock-enabled:false}")
    private boolean mockEnabled;

    public String sendVerificationCode(String phoneNumber) {
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);

        // 재전송 남용/문자 폭탄 방지: 같은 번호로는 쿨다운 동안 재발송 불가
        Boolean cooldownSet = redisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_PREFIX + normalizedPhoneNumber, "1", RESEND_COOLDOWN);
        if (Boolean.FALSE.equals(cooldownSet)) {
            throw new CallCareException(ErrorCode.PHONE_VERIFICATION_TOO_MANY_REQUESTS);
        }

        String code = generateRandomCode();
        String text = "[콜케어 인증번호] " + code + "\n본인 확인을 위해 인증번호를 입력해주세요.";
        String codeKey = KEY_PREFIX + normalizedPhoneNumber;

        try {
            // Redis에 먼저 저장한 뒤 발송
            redisTemplate.opsForValue().set(codeKey, code, CODE_TTL);
            // sender 포맷팅/검증은 실제 발송 책임을 지는 SmsSender 구현체(CoolSmsSender)에 맡김
            smsSender.send(sender, normalizedPhoneNumber, text);
            // 이전 시도 횟수 초기화
            redisTemplate.delete(ATTEMPT_PREFIX + normalizedPhoneNumber);
            log.info("SMS 인증번호 발송 완료 - phoneNumber: {}", PhoneMaskUtil.mask(normalizedPhoneNumber));
            return mockEnabled ? code : null;
        } catch (Exception e) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(COOLDOWN_PREFIX + normalizedPhoneNumber);
            log.error("SMS 인증번호 발송 실패 - phoneNumber: {}, error: {}",
                    PhoneMaskUtil.mask(normalizedPhoneNumber), e.getMessage(), e);
            throw new CallCareException(ErrorCode.PHONE_VERIFICATION_SEND_FAILED);
        }
    }


    public boolean verifyCode(String phoneNumber, String code) {
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        String attemptKey = ATTEMPT_PREFIX + normalizedPhoneNumber;
        String codeKey = KEY_PREFIX + normalizedPhoneNumber;

        // 만료/미발송 케이스 먼저 걸러냄
        String savedCode = redisTemplate.opsForValue().get(codeKey);
        if (savedCode == null) {
            throw new CallCareException(ErrorCode.PHONE_VERIFICATION_CODE_EXPIRED);
        }
        Long currentAttempts = redisTemplate.opsForValue().increment(attemptKey);
        redisTemplate.expire(attemptKey, CODE_TTL);
        if (currentAttempts != null && currentAttempts > MAX_VERIFY_ATTEMPTS) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptKey);
            throw new CallCareException(ErrorCode.PHONE_VERIFICATION_ATTEMPTS_EXCEEDED);
        }

        boolean matched = savedCode.equals(code);
        if (matched) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptKey);
        } else {
            log.warn("SMS 인증번호 불일치 - phoneNumber: {}, attempt: {}",
                    PhoneMaskUtil.mask(normalizedPhoneNumber), currentAttempts);
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
}
