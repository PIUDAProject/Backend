package com.piuda.callcare.domain.ocrresult.dto;

public record ParsedOcrData(
        String drugName, // 약 이름
        String dosagePerTime, // 1회 복용량 (예: "1정", "5ml")
        Integer timesPerDay, // 1일 복용 횟수
        Integer totalDays // 총 복용 일수
) {}
