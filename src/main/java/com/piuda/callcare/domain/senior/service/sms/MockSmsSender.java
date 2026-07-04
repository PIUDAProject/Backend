package com.piuda.callcare.domain.senior.service.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실제로 문자를 보내지 않고 로그로만 남기는 개발/테스트용 구현체
 * coolsms.mock-enabled=true 일 때만 활성화
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "coolsms.mock-enabled", havingValue = "true")
public class MockSmsSender implements SmsSender {

    @Override
    public void send(String from, String to, String text) {
        log.info("[MOCK SMS] from={} to={} | {}", from, to, text);
    }
}
