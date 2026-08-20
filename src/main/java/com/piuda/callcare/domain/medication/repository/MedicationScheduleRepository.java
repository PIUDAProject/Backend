package com.piuda.callcare.domain.medication.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, Long> {

    void deleteAllByMedication_Id(Long medicationId);

    // 충돌 알림 문구용: 두 약의 복용 시간대를 한 번에 조회. 발송이 트랜잭션 밖이라 지연 로딩을 쓸 수 없고,
    // 약이 2건뿐이라 IN 절 한 번이면 충분하다. 정렬은 서비스에서 enum 순서(아침→점심→저녁→취침)로 처리한다
    // — mealTime이 STRING이라 DB 정렬은 알파벳순(BEDTIME이 먼저)이 되어 사람이 읽는 순서와 어긋난다.
    // 호출부가 스케줄을 약별로 묶느라 medication에 접근하므로 JOIN FETCH로 함께 로딩한다.
    @Query("""
            SELECT ms FROM MedicationSchedule ms
            JOIN FETCH ms.medication m
            WHERE m.id IN :medicationIds
            """)
    List<MedicationSchedule> findAllByMedication_IdIn(@Param("medicationIds") Collection<Long> medicationIds);

    // 토글 검증용: 해당 약에 그 시간대의 오늘 활성(복용 기간 내) 스케줄이 실제 존재하는지
    // (홈카드 완료 계산의 findActiveSchedulesForHomeCards와 동일한 active/기간 조건으로 게이트를 맞춘다)
    // 토글은 오늘만 허용하므로 삭제 조건은 단순히 IS NULL — 삭제된 약은 오늘 홈카드에 없으니 토글도 막는다.
    @Query("""
            SELECT (COUNT(ms) > 0) FROM MedicationSchedule ms
            JOIN ms.medication m
            WHERE m.id = :medicationId
              AND ms.mealTime = :mealTime
              AND m.isActive = true
              AND m.deletedAt IS NULL
              AND m.startDate <= :date
              AND m.endDate >= :date
            """)
    boolean existsActiveScheduleForToggle(
            @Param("medicationId") Long medicationId,
            @Param("mealTime") MealTime mealTime,
            @Param("date") LocalDate date
    );

    // 전화 발신 게이트: 해당 시간대에 "아직 복용 완료로 기록되지 않은" 활성 약이 하나라도 있는지.
    // 보호자가 이미 그 시간대를 체크했으면 전화를 걸지 않는다 — 통보 억제가 아니라 발신 자체를 생략한다.
    // 약이 아예 없는 경우도 false이므로 "발신할 약이 있는가" 판정까지 이 쿼리 하나로 처리한다.
    @Query("""
            SELECT (COUNT(ms) > 0) FROM MedicationSchedule ms
            JOIN ms.medication m
            WHERE m.senior.id = :seniorId
              AND ms.mealTime = :mealTime
              AND m.isActive = true
              AND m.startDate <= :date
              AND m.endDate >= :date
              AND NOT EXISTS (
                  SELECT 1 FROM MedicationLog ml
                  WHERE ml.medication = m
                    AND ml.takenDate = :date
                    AND ml.mealTime = :mealTime
                    AND ml.isTaken = true
              )
            """)
    boolean existsUntakenScheduleForCall(
            @Param("seniorId") Long seniorId,
            @Param("mealTime") MealTime mealTime,
            @Param("date") LocalDate date
    );

    // 홈카드용: 해당 날짜에 복용 중(active + 기간 내)인 약의 스케줄을 약과 함께 한 번에 조회 (N+1 방지)
    // 삭제된 약은 삭제일 당일부터만 숨긴다 — deletedAt이 조회 날짜의 다음날 0시 이후면 그 날엔 아직 살아있던 약.
    // 그래서 어제 카드에는 남고 오늘·미래 카드에서만 빠진다(지난 복약 이력 보존).
    @Query("""
            SELECT ms FROM MedicationSchedule ms
            JOIN FETCH ms.medication m
            WHERE m.senior.id = :seniorId
              AND m.isActive = true
              AND (m.deletedAt IS NULL OR m.deletedAt >= :hiddenFrom)
              AND m.startDate <= :date
              AND m.endDate >= :date
            ORDER BY ms.mealTime, m.hospitalName NULLS LAST, m.id
            """)
    List<MedicationSchedule> findActiveSchedulesForHomeCards(
            @Param("seniorId") Long seniorId,
            @Param("date") LocalDate date,
            @Param("hiddenFrom") LocalDateTime hiddenFrom
    );

    @Query("""
            SELECT ms FROM MedicationSchedule ms
            JOIN FETCH ms.medication m
            WHERE m.senior.id = :seniorId
              AND ms.mealTime = :mealTime
              AND m.isActive = true
              AND m.startDate <= :date
              AND m.endDate >= :date
            ORDER BY m.id
            """)
    List<MedicationSchedule> findActiveSchedulesForMealTime(
            @Param("seniorId") Long seniorId,
            @Param("mealTime") MealTime mealTime,
            @Param("date") LocalDate date
    );
}
