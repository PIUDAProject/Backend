package com.piuda.callcare.domain.home.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medicationlog.entity.MedicationLog;

// 시간대 복약 완료 판정 규칙 (홈카드 조회 1단계 + 수동 토글 2단계가 공유).
// "완료"의 단일 정의를 한 곳에 두어 파생값으로만 산출한다.
@Component
public class MealTimeCompletionCalculator {

    // 복용 완료 합성 키: (약 ID, 식사시간)
    public record TakenKey(Long medicationId, MealTime mealTime) {
    }

    // 로그 목록 → 복용 완료(isTaken=true) 키 집합
    public Set<TakenKey> toTakenKeys(List<MedicationLog> logs) {
        return logs.stream()
                .filter(MedicationLog::getIsTaken)
                .map(log -> new TakenKey(log.getMedication().getId(), log.getMealTime()))
                .collect(Collectors.toSet());
    }

    // 단일 스케줄(약 1칸)이 복용 완료인지
    public boolean isTaken(MedicationSchedule schedule, Set<TakenKey> takenKeys) {
        return takenKeys.contains(new TakenKey(schedule.getMedication().getId(), schedule.getMealTime()));
    }

    // 해당 시간대의 모든 약 스케줄이 복용 완료면 시간대 완료
    public boolean isMealTimeCompleted(List<MedicationSchedule> schedulesInMealTime, Set<TakenKey> takenKeys) {
        return !schedulesInMealTime.isEmpty()
                && schedulesInMealTime.stream().allMatch(schedule -> isTaken(schedule, takenKeys));
    }
}