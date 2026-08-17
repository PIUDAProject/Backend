package com.piuda.callcare.domain.calllog.client;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.piuda.callcare.global.util.PhoneMaskUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "coolsms.mock-enabled", havingValue = "true")
public class MockVoiceCallSender implements VoiceCallSender {

    @Override
    public String call(String from, String to, String headerMessage, String bodyMessage) {
        String messageId = "mock-call-" + UUID.randomUUID();
        log.info("[MOCK VOICE CALL] messageId={} from={} to={} | {} {}",
                messageId, PhoneMaskUtil.mask(from), PhoneMaskUtil.mask(to),
                headerMessage, bodyMessage);
        return messageId;
    }
}
