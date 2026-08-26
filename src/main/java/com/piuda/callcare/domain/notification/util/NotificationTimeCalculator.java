package com.piuda.callcare.domain.notification.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.senior.entity.Senior;

import lombok.experimental.UtilityClass;

// 전화 알림 발신/재시도 시각을 계산한다 (전화 재시도, FCM 푸시 스케줄링에서 사용)
@UtilityClass
public class NotificationTimeCalculator {

	private static final int CALL_OFFSET_MINUTES = 30;  // 식사 시각 + 30분 후 최초 발신
	// Senior의 해당 식사 시각 + 30분 = 최초 발신 시각 (자정 경계 시 날짜가 다음 날로 넘어감)
	public LocalDateTime firstCallTime(Senior senior, MealTime mealTime, LocalDate baseDate) {
		LocalTime mealTimeValue = resolveMealTime(senior, mealTime);
		return LocalDateTime.of(baseDate, mealTimeValue).plusMinutes(CALL_OFFSET_MINUTES);
	}

	// MealTime → Senior의 LocalTime 필드 매핑. 모든 시간대가 대응 필드를 가지므로 분기가 전부 덮인다
	// — 시간대를 늘리면 여기서 컴파일 에러가 나고, 그것이 대응 필드를 함께 만들라는 신호다.
	private LocalTime resolveMealTime(Senior senior, MealTime mealTime) {
		return switch (mealTime) {
			case BREAKFAST -> senior.getBreakfastTime();
			case LUNCH -> senior.getLunchTime();
			case DINNER -> senior.getDinnerTime();
		};
	}
}
