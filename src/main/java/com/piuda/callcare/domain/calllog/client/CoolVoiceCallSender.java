package com.piuda.callcare.domain.calllog.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.model.voice.VoiceOption;
import com.solapi.sdk.message.model.voice.VoiceType;
import com.solapi.sdk.message.service.DefaultMessageService;

@Component
@ConditionalOnProperty(name = "coolsms.mock-enabled", havingValue = "false", matchIfMissing = true)
public class CoolVoiceCallSender implements VoiceCallSender {

    private final DefaultMessageService messageService;

    public CoolVoiceCallSender(
            @Value("${coolsms.api-key:}") String apiKey,
            @Value("${coolsms.api-secret:}") String apiSecret
    ) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret)) {
            throw new IllegalStateException(
                    "coolsms.mock-enabled=false 인데 coolsms.api-key/api-secret이 설정되지 않았습니다. " +
                            "COOLSMS_API_KEY, COOLSMS_API_SECRET 환경변수를 확인하세요.");
        }
        this.messageService = SolapiClient.INSTANCE.createInstance(apiKey, apiSecret);
    }

    @Override
    public String call(String from, String to, String headerMessage, String bodyMessage) {
        Message message = new Message();
        message.setFrom(normalizePhoneNumber(from));
        message.setTo(normalizePhoneNumber(to));
        message.setText(bodyMessage);

        VoiceOption voiceOption = new VoiceOption();
        voiceOption.setVoiceType(VoiceType.FEMALE);
        if (StringUtils.hasText(headerMessage)) {
            voiceOption.setHeaderMessage(headerMessage);
        }
        message.setVoiceOptions(voiceOption);

        MultipleDetailMessageSentResponse response;
        try {
            response = messageService.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("SOLAPI 음성 메시지 발송에 실패했습니다.", e);
        }
        if (response.getMessageList() == null || response.getMessageList().isEmpty()) {
            throw new IllegalStateException("SOLAPI 음성 메시지 발송 응답에 messageId가 없습니다.");
        }
        return response.getMessageList().get(0).getMessageId();
    }

    private String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
    }
}
