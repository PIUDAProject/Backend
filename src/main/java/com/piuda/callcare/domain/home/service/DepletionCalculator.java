package com.piuda.callcare.domain.home.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

// 약 소진 계산 규칙(3단계). 남은 복용 일수/부족 판정을 파생값으로만 산출하는 단일 정의.
// 단순 날짜 기준(A안): 실제 복용기록(MedicationLog)은 보지 않고 복용 종료일과 오늘만 사용한다.
@Component
public class DepletionCalculator {

    // 소진 임박(복약 부족) 판정 임계값: 남은 일수가 이 값 이하이면 부족 (명세: 3일 이하)
    public static final int DEPLETION_THRESHOLD_DAYS = 3;

    // 남은 복용 일수 = 복용 종료일 - 오늘.
    // 종료일 포함(endDate >= 오늘이 복용 기간) 컨벤션과 일관되게, 오늘이 종료일이면 0(마지막 복용일), 이미 지났으면 음수.
    public long remainingDays(LocalDate endDate, LocalDate today) {
        return ChronoUnit.DAYS.between(today, endDate);
    }

    // 남은 일수가 임계값 이하이면 소진 임박(부족)
    public boolean isDepleting(LocalDate endDate, LocalDate today) {
        return remainingDays(endDate, today) <= DEPLETION_THRESHOLD_DAYS;
    }
}