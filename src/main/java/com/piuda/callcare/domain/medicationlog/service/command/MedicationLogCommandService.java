package com.piuda.callcare.domain.medicationlog.service.command;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medicationlog.entity.MedicationLog;
import com.piuda.callcare.domain.medicationlog.repository.MedicationLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class MedicationLogCommandService {

    private final MedicationLogRepository medicationLogRepository;

    // 공유 쓰기 프리미티브: (약, 날짜, 시간대)의 복약 로그 1건을 isTaken 값으로 upsert.
    // - 멱등성: 유니크 키(medication_id, taken_date, meal_time) + find→(없으면 insert/있으면 update) 경로로
    //   동일 키 중복 로그를 막는다. 충돌 정책은 last-write-wins.
    // - 수동 토글(약 1건)과 7단계 전화 자동완료(시간대 약 전체 루프)가 이 메서드를 재사용한다.
    public MedicationLog writeLog(Medication medication, LocalDate date, MealTime mealTime, boolean isTaken) {
        return medicationLogRepository
                .findByMedication_IdAndTakenDateAndMealTime(medication.getId(), date, mealTime)
                .map(log -> {
                    log.updateIsTaken(isTaken); // dirty checking으로 갱신
                    return log;
                })
                .orElseGet(() -> medicationLogRepository.save(
                        MedicationLog.builder()
                                .medication(medication)
                                .senior(medication.getSenior())
                                .takenDate(date)
                                .mealTime(mealTime)
                                .isTaken(isTaken)
                                .takenAt(isTaken ? java.time.LocalDateTime.now() : null)
                                .build()));
    }
}