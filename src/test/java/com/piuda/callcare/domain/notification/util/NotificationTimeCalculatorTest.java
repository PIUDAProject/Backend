package com.piuda.callcare.domain.notification.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

@DisplayName("NotificationTimeCalculator 단위 테스트")
class NotificationTimeCalculatorTest {

	private static final LocalDate BASE_DATE = LocalDate.of(2026, 6, 29);

	private Senior seniorWithTimes(LocalTime breakfast, LocalTime lunch, LocalTime dinner) {
		return Senior.builder()
			.name("테스트어르신")
			.breakfastTime(breakfast)
			.lunchTime(lunch)
			.dinnerTime(dinner)
			.build();
	}

	@Test
	@DisplayName("정상 케이스: 식사 08:00이면 최초 발신은 08:30, 재시도는 08:40")
	void firstCall_and_retry_for_normal_time() {
		// Given
		Senior senior = seniorWithTimes(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(18, 0));

		// When
		LocalDateTime firstCall = NotificationTimeCalculator.firstCallTime(senior, MealTime.BREAKFAST, BASE_DATE);
		LocalDateTime retryCall = NotificationTimeCalculator.retryCallTime(firstCall);

		// Then
		assertThat(firstCall).isEqualTo(LocalDateTime.of(2026, 6, 29, 8, 30));
		assertThat(retryCall).isEqualTo(LocalDateTime.of(2026, 6, 29, 8, 40));
	}

	@Test
	@DisplayName("경계값: 식사 23:50이면 최초 발신이 다음 날 00:20으로 넘어간다")
	void firstCall_crosses_midnight() {
		// Given
		Senior senior = seniorWithTimes(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(23, 50));

		// When
		LocalDateTime firstCall = NotificationTimeCalculator.firstCallTime(senior, MealTime.DINNER, BASE_DATE);

		// Then
		assertThat(firstCall).isEqualTo(LocalDateTime.of(2026, 6, 30, 0, 20));
	}

	@Test
	@DisplayName("경계값: 식사 23:55면 최초 발신 00:25, 재시도가 자정을 넘겨 00:35이 된다")
	void retry_crosses_midnight() {
		// Given
		Senior senior = seniorWithTimes(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(23, 55));

		// When
		LocalDateTime firstCall = NotificationTimeCalculator.firstCallTime(senior, MealTime.DINNER, BASE_DATE);
		LocalDateTime retryCall = NotificationTimeCalculator.retryCallTime(senior, MealTime.DINNER, BASE_DATE);

		// Then
		assertThat(firstCall).isEqualTo(LocalDateTime.of(2026, 6, 30, 0, 25));
		assertThat(retryCall).isEqualTo(LocalDateTime.of(2026, 6, 30, 0, 35));
	}

	@Test
	@DisplayName("예외 케이스: BEDTIME은 식사 시각 필드가 없어 UNSUPPORTED_MEAL_TIME 예외가 발생한다")
	void bedtime_throws_unsupported() {
		// Given
		Senior senior = seniorWithTimes(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(18, 0));

		// When / Then
		assertThatThrownBy(() -> NotificationTimeCalculator.firstCallTime(senior, MealTime.BEDTIME, BASE_DATE))
			.isInstanceOf(CallCareException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_MEAL_TIME);
	}
}
