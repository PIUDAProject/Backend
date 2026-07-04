package com.piuda.callcare.domain.senior.service.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.model.MessageType;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * coolsms API로 실제 문자를 발송하는 구현체.
 * coolsms.mock-enabled=false 일 때(기본값, 운영 환경)만 활성화
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coolsms.mock-enabled", havingValue = "false", matchIfMissing = true)
public class CoolSmsSender implements SmsSender {

    private final DefaultMessageService messageService;

    @Override
    public void send(String from, String to, String text) {
        Message message = new Message();
        message.setFrom(normalizePhoneNumber(from));
        message.setTo(to);
        message.setType(MessageType.SMS);
        message.setText(text);

        SingleMessageSentResponse response = messageService.sendOne(new SingleMessageSendingRequest(message));
        log.info("coolsms 문자 발송 완료 - messageId: {}", response != null ? response.getMessageId() : null);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
    }
}
