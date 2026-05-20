package com.piuda.callcare.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    MISSED_CALL("전화 미수신"),
    LOW_STOCK("약 부족"),
    DRUG_CONFLICT("약물 충돌");

    private final String description;
}
