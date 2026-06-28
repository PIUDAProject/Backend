package com.piuda.callcare.domain.medication.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MealTime enum 단위 테스트")
class MealTimeTest {

	@Test
	@DisplayName("정렬 순서: ordinal이 BREAKFAST < LUNCH < DINNER 순서를 보장한다")
	void ordinal_order_is_breakfast_lunch_dinner() {
		// When / Then
		assertThat(MealTime.BREAKFAST.ordinal()).isLessThan(MealTime.LUNCH.ordinal());
		assertThat(MealTime.LUNCH.ordinal()).isLessThan(MealTime.DINNER.ordinal());
	}

	@Test
	@DisplayName("각 값의 description이 한글 라벨로 매핑된다")
	void description_is_korean_label() {
		// When / Then
		assertThat(MealTime.BREAKFAST.getDescription()).isEqualTo("아침");
		assertThat(MealTime.LUNCH.getDescription()).isEqualTo("점심");
		assertThat(MealTime.DINNER.getDescription()).isEqualTo("저녁");
	}
}
