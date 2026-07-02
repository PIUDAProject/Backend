package com.piuda.callcare.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DepletionCalculator 단위 테스트")
class DepletionCalculatorTest {

    private final DepletionCalculator calculator = new DepletionCalculator();
    private final LocalDate today = LocalDate.of(2026, 7, 2);

    @Test
    @DisplayName("남은 일수는 복용 종료일 - 오늘로 계산된다 (종료일=오늘이면 0)")
    void remainingDays_isEndDateMinusToday() {
        assertThat(calculator.remainingDays(today.plusDays(4), today)).isEqualTo(4);
        assertThat(calculator.remainingDays(today, today)).isEqualTo(0);
        assertThat(calculator.remainingDays(today.minusDays(2), today)).isEqualTo(-2);
    }

    @Test
    @DisplayName("경계: 남은 4일이면 정상(부족 아님)")
    void isDepleting_false_when_4days() {
        assertThat(calculator.isDepleting(today.plusDays(4), today)).isFalse();
    }

    @Test
    @DisplayName("경계: 남은 3일이면 부족")
    void isDepleting_true_when_3days() {
        assertThat(calculator.isDepleting(today.plusDays(3), today)).isTrue();
    }

    @Test
    @DisplayName("경계: 오늘이 종료일(남은 0일)이면 부족")
    void isDepleting_true_when_0days() {
        assertThat(calculator.isDepleting(today, today)).isTrue();
    }

    @Test
    @DisplayName("경계: 종료일이 이미 지나(남은 음수) 있으면 부족")
    void isDepleting_true_when_negative() {
        assertThat(calculator.isDepleting(today.minusDays(1), today)).isTrue();
    }
}