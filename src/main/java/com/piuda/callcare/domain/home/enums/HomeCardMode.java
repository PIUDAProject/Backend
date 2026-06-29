package com.piuda.callcare.domain.home.enums;

import java.time.LocalDate;

// 홈카드 조회 날짜 모드 (과거=읽기전용, 오늘=완료추적, 미래=예정조회)
public enum HomeCardMode {
    PAST, TODAY, FUTURE;

    public static HomeCardMode from(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            return PAST;
        }
        if (date.isAfter(today)) {
            return FUTURE;
        }
        return TODAY;
    }

    // 미래는 완료 개념이 없어 로그 조회/완료 합성을 건너뛴다
    public boolean tracksCompletion() {
        return this != FUTURE;
    }
}
