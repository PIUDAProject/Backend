package com.piuda.callcare.global.config;

import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class SmsConfig {

    @Value("${coolsms.api-key:}")
    private String apiKey;
    @Value("${coolsms.api-secret:}")
    private String apiSecret;
    @Value("${coolsms.sender:}")
    private String sender;

    @Bean
    @ConditionalOnProperty(name = "coolsms.mock-enabled", havingValue = "false", matchIfMissing = true)
    public DefaultMessageService messageService() {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret)) {
            throw new IllegalStateException(
                    "coolsms.mock-enabled=false 인데 coolsms.api-key/api-secret이 설정되지 않았습니다. " +
                            "COOLSMS_API_KEY, COOLSMS_API_SECRET 환경변수를 확인하세요.");
        }
        if (!StringUtils.hasText(sender)) {
            throw new IllegalStateException(
                    "coolsms.mock-enabled=false 인데 coolsms.sender가 설정되지 않았습니다. " +
                            "COOLSMS_SENDER_NUMBER 환경변수를 확인하세요.");
        }
        return new DefaultMessageService(apiKey, apiSecret, "https://api.coolsms.co.kr");
    }
}
