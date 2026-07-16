package com.piuda.callcare.domain.calllog.dto.request;

import java.util.Map;

public record CallResultWebhookRequest(
        String messageId,
        String status,
        String statusCode,
        String result,
        String event,
        Map<String, Object> payload
) {

    public String resolvedMessageId() {
        if (messageId != null) {
            return messageId;
        }
        Object value = payload == null ? null : payload.get("messageId");
        return value == null ? null : value.toString();
    }

    public String resolvedStatus() {
        if (hasText(status) && !isSwaggerPlaceholder(status)) {
            return status;
        }
        if (hasText(result) && !isSwaggerPlaceholder(result)) {
            return result;
        }
        if (hasText(event) && !isSwaggerPlaceholder(event)) {
            return event;
        }
        if (hasText(statusCode) && !isSwaggerPlaceholder(statusCode)) {
            return statusCode;
        }
        Object value = payload == null ? null : payload.get("status");
        return value == null ? null : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isSwaggerPlaceholder(String value) {
        return "string".equalsIgnoreCase(value);
    }
}
