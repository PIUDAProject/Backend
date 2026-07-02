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
}
