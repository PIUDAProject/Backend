package com.piuda.callcare.domain.medication.repository;

import java.time.LocalDate;
import java.util.List;

import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, Long> {

    // 토글 검증용: 해당 약에 그 시간대의 오늘 활성(복용 기간 내) 스케줄이 실제 존재하는지
    // (홈카드 완료 계산의 findActiveSchedulesForHomeCards와 동일한 active/기간 조건으로 게이트를 맞춘다)
    @Query("""
            SELECT (COUNT(ms) > 0) FROM MedicationSchedule ms
            JOIN ms.medication m
            WHERE m.id = :medicationId
              AND ms.mealTime = :mealTime
              AND m.isActive = true
              AND m.startDate <= :date
              AND m.endDate >= :date
            """)
    boolean existsActiveScheduleForToggle(
            @Param("medicationId") Long medicationId,
            @Param("mealTime") MealTime mealTime,
            @Param("date") LocalDate date
    );

    // 홈카드용: 해당 날짜에 복용 중(active + 기간 내)인 약의 스케줄을 약·병원과 함께 한 번에 조회 (N+1 방지)
    @Query("""
            SELECT ms FROM MedicationSchedule ms
            JOIN FETCH ms.medication m
            LEFT JOIN FETCH m.hospital
            WHERE m.senior.id = :seniorId
              AND m.isActive = true
              AND m.startDate <= :date
              AND m.endDate >= :date
            ORDER BY ms.mealTime, m.hospital.id NULLS LAST, m.id
            """)
    List<MedicationSchedule> findActiveSchedulesForHomeCards(
            @Param("seniorId") Long seniorId,
            @Param("date") LocalDate date
    );
}
