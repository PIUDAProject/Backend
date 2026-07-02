package com.piuda.callcare.domain.medication.repository;

import java.util.List;

import com.piuda.callcare.domain.medication.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    // 소진 예측용: 특정 어르신의 활성(복용 중) 약만 조회. 남은 일수는 endDate로 계산하므로 endDate가 있는 것만 대상으로 한다.
    @Query("""
            SELECT m FROM Medication m
            WHERE m.senior.id = :seniorId
              AND m.isActive = true
              AND m.endDate IS NOT NULL
            """)
    List<Medication> findActiveMedicationsForDepletion(@Param("seniorId") Long seniorId);

    // 충돌 분석용: 특정 어르신의 활성 약 중 DrugInfo(상호작용 텍스트 소스)가 연결된 것만 조회.
    // JOIN FETCH(inner join)라 drugInfo가 없는 약은 자동 제외 → 상호작용 텍스트 없는 약은 대상 아님.
    @Query("""
            SELECT m FROM Medication m
            JOIN FETCH m.drugInfo
            WHERE m.senior.id = :seniorId
              AND m.isActive = true
            """)
    List<Medication> findActiveWithDrugInfoBySeniorId(@Param("seniorId") Long seniorId);
}
