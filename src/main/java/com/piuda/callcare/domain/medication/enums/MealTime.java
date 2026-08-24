package com.piuda.callcare.domain.medication.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MealTime {
    BREAKFAST("아침"),
    LUNCH("점심"),
    DINNER("저녁");

    private final String description;
}
