package com.piuda.callcare.domain.home.service.query;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.home.converter.HomeSummaryConverter;
import com.piuda.callcare.domain.home.dto.response.HomeSummaryResponse;
import com.piuda.callcare.domain.home.dto.response.NextDoseResponse;
import com.piuda.callcare.domain.home.enums.HomeCardMode;
import com.piuda.callcare.domain.home.service.MealTimeCompletionCalculator;
import com.piuda.callcare.domain.home.service.MealTimeCompletionCalculator.TakenKey;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.medicationlog.repository.MedicationLogRepository;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

// 홈 상단 요약 카드 집계. 홈 카드 응답을 재사용하지 않고 스케줄·복약기록을 직접 센다 —
// 카드 조회는 오늘 모드에서 완료된 시간대를 응답에서 빼기 때문에 "3건 예정 · 1건 완료"를 셀 수 없다.
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HomeSummaryQueryService {

    // 요약은 아침·점심·저녁만 센다(기획). BEDTIME은 Senior에 시각 필드가 없어 전화 알림에서도 제외돼 있고,
    // 홈 카드에는 그대로 네 번째 그룹으로 남는다 — 요약 숫자와 카드 그룹 수가 다를 수 있는 지점이다.
    private static final List<MealTime> SUMMARY_MEAL_TIMES = List.of(MealTime.BREAKFAST, MealTime.LUNCH, MealTime.DINNER);

    private final SeniorRepository seniorRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final MedicationLogRepository medicationLogRepository;
    private final MealTimeCompletionCalculator completionCalculator;
    private final HomeSummaryConverter homeSummaryConverter;

    public HomeSummaryResponse getSummary(Long seniorId, LocalDate date) {
        return getSummary(seniorId, date, LocalTime.now());
    }

    // 현재 시각을 파라미터로 받는다 — "다음 복용"이 시각에 의존하므로, 시각을 넘겨야 테스트에서 경계를 단언할 수 있다
    // (7단계 배치에서 기준일을 호출자가 넘기게 한 것과 같은 이유).
    HomeSummaryResponse getSummary(Long seniorId, LocalDate date, LocalTime now) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();

        // TODO: 인증 도입 후 seniorId 소유권 검증 추가
        Senior senior = seniorRepository.findById(seniorId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));

        HomeCardMode mode = HomeCardMode.from(targetDate);

        // 카드 조회와 같은 쿼리를 쓴다 — 대상 약 집합(활성·복용기간·소프트 삭제 규칙)이 갈리면 안 된다
        Map<MealTime, List<MedicationSchedule>> byMealTime = medicationScheduleRepository
                .findActiveSchedulesForHomeCards(seniorId, targetDate, targetDate.plusDays(1).atStartOfDay()).stream()
                .filter(schedule -> SUMMARY_MEAL_TIMES.contains(schedule.getMealTime()))
                .collect(Collectors.groupingBy(MedicationSchedule::getMealTime, TreeMap::new, Collectors.toList()));

        Set<TakenKey> takenKeys = mode.tracksCompletion()
                ? completionCalculator.toTakenKeys(medicationLogRepository.findBySenior_IdAndTakenDate(seniorId, targetDate))
                : Set.of();

        // 어제는 완료만, 내일은 예정만 내려준다(기획). 오늘만 둘 다 채운다.
        Integer scheduledCount = (mode == HomeCardMode.PAST) ? null : byMealTime.size();
        Integer completedCount = (mode == HomeCardMode.FUTURE) ? null : countCompleted(byMealTime, takenKeys);
        NextDoseResponse nextDose = (mode == HomeCardMode.TODAY) ? resolveNextDose(senior, byMealTime, takenKeys, now) : null;

        return homeSummaryConverter.toResponse(targetDate, mode, scheduledCount, completedCount, nextDose);
    }

    // 완료는 시간대 단위 — 그 시간대 약이 전부 완료여야 1건이다(홈 카드에서 시간대가 사라지는 기준과 동일).
    private int countCompleted(Map<MealTime, List<MedicationSchedule>> byMealTime, Set<TakenKey> takenKeys) {
        return (int) byMealTime.values().stream()
                .filter(schedules -> completionCalculator.isMealTimeCompleted(schedules, takenKeys))
                .count();
    }

    // 다음 복용 = 지금 시각 이후 가장 가까운 시간대. 시각이 이미 지난 시간대는 미완료여도 잡지 않는다.
    // 이미 완료한 시간대는 안내할 것이 없으므로 건너뛰고, 남은 시간대가 없으면 null(응답에서 생략).
    // 시간대 안에서도 이미 완료한 약은 안내에서 뺀다.
    private NextDoseResponse resolveNextDose(
            Senior senior, Map<MealTime, List<MedicationSchedule>> byMealTime, Set<TakenKey> takenKeys, LocalTime now) {
        return byMealTime.entrySet().stream()
                .filter(entry -> !completionCalculator.isMealTimeCompleted(entry.getValue(), takenKeys))
                .filter(entry -> {
                    LocalTime mealTime = mealTimeOf(senior, entry.getKey());
                    return mealTime != null && mealTime.isAfter(now);
                })
                // 어르신이 식사 시각을 바꾸면 enum 순서와 실제 시각 순서가 어긋날 수 있어 시각으로 정렬한다
                .min(Comparator.comparing(entry -> mealTimeOf(senior, entry.getKey())))
                // 시간대가 미완료여도 그 안의 일부 약은 이미 완료일 수 있다(시각이 되기 전에 미리 체크한 경우).
                // 완료 판정은 시간대 단위지만 안내는 약 단위라, 남은 약만 추려 첫 약과 나머지 수를 낸다.
                .map(entry -> {
                    List<MedicationSchedule> remaining = entry.getValue().stream()
                            .filter(schedule -> !completionCalculator.isTaken(schedule, takenKeys))
                            .toList();
                    return homeSummaryConverter.toNextDose(remaining.get(0), remaining.size() - 1);
                })
                .orElse(null);
    }

    // BEDTIME은 대응하는 시각 컬럼이 없어 여기 오지 않는다(SUMMARY_MEAL_TIMES에서 이미 걸러짐)
    private LocalTime mealTimeOf(Senior senior, MealTime mealTime) {
        return switch (mealTime) {
            case BREAKFAST -> senior.getBreakfastTime();
            case LUNCH -> senior.getLunchTime();
            case DINNER -> senior.getDinnerTime();
            default -> null;
        };
    }
}
