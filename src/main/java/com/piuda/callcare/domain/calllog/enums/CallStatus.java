package com.piuda.callcare.domain.calllog.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CallStatus {
    PENDING("결과 대기"),
    ANSWERED("수신"),
    NO_ANSWER("미수신"),
    FAILED("발신 실패"),
    SKIPPED("발신 생략"); // 재발신 시점에 그 시간대가 이미 복약 완료라 발신하지 않음

    private final String description;

    public boolean isTerminal() {
        return this == ANSWERED || this == NO_ANSWER || this == FAILED || this == SKIPPED;
    }
}
