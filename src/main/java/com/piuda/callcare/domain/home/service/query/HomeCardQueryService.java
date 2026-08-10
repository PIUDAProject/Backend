package com.piuda.callcare.domain.home.service.query;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.home.converter.HomeCardConverter;
import com.piuda.callcare.domain.home.dto.response.HomeCardResponse;
import com.piuda.callcare.domain.home.dto.response.HospitalGroupResponse;
import com.piuda.callcare.domain.home.dto.response.MealGroupResponse;
import com.piuda.callcare.domain.home.enums.CompletedStatus;
import com.piuda.callcare.domain.home.enums.HomeCardMode;
import com.piuda.callcare.domain.home.service.MealTimeCompletionCalculator;
import com.piuda.callcare.domain.home.service.MealTimeCompletionCalculator.TakenKey;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.medicationlog.repository.MedicationLogRepository;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HomeCardQueryService {

    private static final String NO_HOSPITAL_NAME = "병원 정보 없음";

    private final SeniorRepository seniorRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final MedicationLogRepository medicationLogRepository;
    private final HomeCardConverter homeCardConverter;
    private final MealTimeCompletionCalculator completionCalculator;

    // 식사시간(ordinal) → 병원 단위로 약 카드를 그룹화하고, 날짜 모드에 맞춰 완료 상태를 합성한다
    public HomeCardResponse getHomeCards(Long seniorId, LocalDate date) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();

        if (!seniorRepository.existsById(seniorId)) {
            throw new CallCareException(ErrorCode.SENIOR_NOT_FOUND);
        }

        HomeCardMode mode = HomeCardMode.from(targetDate);
        // 조회 날짜의 다음날 0시 — 이 시각 이후에 삭제된 약은 그 날엔 아직 복용 중이었으므로 카드에 남긴다
        List<MedicationSchedule> schedules = medicationScheduleRepository.findActiveSchedulesForHomeCards(
                seniorId, targetDate, targetDate.plusDays(1).atStartOfDay());

        // 미래 모드는 완료 개념이 없어 로그 조회 자체를 생략
        Set<TakenKey> takenKeys = mode.tracksCompletion()
                ? loadTakenKeys(seniorId, targetDate)
                : Set.of();

        // 1차: 식사시간 ordinal 순(TreeMap) 그룹
        List<MealGroupResponse> mealGroups = schedules.stream()
                .collect(Collectors.groupingBy(MedicationSchedule::getMealTime, TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> toMealGroup(entry.getKey(), entry.getValue(), mode, takenKeys))
                .toList();

        return new HomeCardResponse(targetDate, mode, mealGroups);
    }

    private MealGroupResponse toMealGroup(
            MealTime mealTime, List<MedicationSchedule> schedules, HomeCardMode mode, Set<TakenKey> takenKeys) {

        // 2차: 같은 시간대 안에서 병원 단위 그룹 (조회 순서 유지, hospitalName이 null이면 "병원 정보 없음")
        // groupingBy는 null 키를 허용하지 않으므로 Optional로 감싼다
        List<HospitalGroupResponse> hospitalGroups = schedules.stream()
                .collect(Collectors.groupingBy(
                        s -> Optional.ofNullable(s.getMedication().getHospitalName()), LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(group -> toHospitalGroup(group, mode, takenKeys))
                .toList();

        // 한 시간대 모든 약이 복용 완료면 true (오늘 전용)
        boolean mealTimeCompleted = mode == HomeCardMode.TODAY
                && completionCalculator.isMealTimeCompleted(schedules, takenKeys);

        return new MealGroupResponse(mealTime, mealTimeCompleted, hospitalGroups);
    }

    private HospitalGroupResponse toHospitalGroup(
            List<MedicationSchedule> group, HomeCardMode mode, Set<TakenKey> takenKeys) {

        String hospitalName = group.get(0).getMedication().getHospitalName();
        String displayName = (hospitalName == null) ? NO_HOSPITAL_NAME : hospitalName;

        var medications = group.stream()
                .map(schedule -> {
                    boolean taken = completionCalculator.isTaken(schedule, takenKeys);
                    CompletedStatus completedStatus = (mode == HomeCardMode.PAST)
                            ? (taken ? CompletedStatus.COMPLETED : CompletedStatus.INCOMPLETE)
                            : null;
                    // 과거 모드는 completedStatus로 표시하므로 isTaken은 응답에서 생략(null)
                    Boolean isTaken = (mode == HomeCardMode.PAST) ? null : taken;
                    return homeCardConverter.toCard(schedule, isTaken, completedStatus);
                })
                .toList();

        return new HospitalGroupResponse(null, displayName, medications);
    }

    private Set<TakenKey> loadTakenKeys(Long seniorId, LocalDate date) {
        return completionCalculator.toTakenKeys(
                medicationLogRepository.findBySenior_IdAndTakenDate(seniorId, date));
    }
}
