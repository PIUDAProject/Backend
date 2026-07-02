package com.piuda.callcare.domain.drugconflict.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;

public interface DrugConflictRepository extends JpaRepository<DrugConflict, Long> {

    // 중복 저장 방지용: (medication_id_1 < medication_id_2)로 정규화해 저장하므로 순서 무관 조합을 한 번에 검사
    boolean existsBySenior_IdAndMedication1_IdAndMedication2_Id(Long seniorId, Long medication1Id, Long medication2Id);

    // 목록 조회: 두 약을 함께 로딩(LazyInitialization 방지). 정렬은 서비스에서 등급 우선순위로 처리.
    @Query("""
            SELECT dc FROM DrugConflict dc
            JOIN FETCH dc.medication1
            JOIN FETCH dc.medication2
            WHERE dc.senior.id = :seniorId
            ORDER BY dc.id ASC
            """)
    List<DrugConflict> findAllWithMedicationsBySeniorId(@Param("seniorId") Long seniorId);

    // 상세 조회: 두 약을 함께 로딩
    @Query("""
            SELECT dc FROM DrugConflict dc
            JOIN FETCH dc.medication1
            JOIN FETCH dc.medication2
            WHERE dc.id = :conflictId
            """)
    Optional<DrugConflict> findWithMedicationsById(@Param("conflictId") Long conflictId);
}