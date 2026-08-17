package com.piuda.callcare.domain.drugconflict.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;

public interface DrugConflictRepository extends JpaRepository<DrugConflict, Long> {

    // 재분석 upsert용: 정규화된 쌍(medication_id_1 < medication_id_2)으로 기존 행을 가져와
    // 최신 분석 결과로 갱신(없으면 신규 저장). 순서 무관 중복은 정규화 + UNIQUE 제약이 함께 보장.
    Optional<DrugConflict> findBySenior_IdAndMedication1_IdAndMedication2_Id(Long seniorId, Long medication1Id, Long medication2Id);

    // 재분석 정리용: 어르신의 모든 충돌 행(노출 필터 없음)을 약과 함께 로딩.
    // 이번 분석에서 다시 매칭되지 않은 stale 행을 걸러내야 하므로 목록 쿼리의 활성·삭제 조건을 걸지 않는다.
    @Query("""
            SELECT dc FROM DrugConflict dc
            JOIN FETCH dc.medication1
            JOIN FETCH dc.medication2
            WHERE dc.senior.id = :seniorId
            """)
    List<DrugConflict> findAllWithMedicationsForReanalysis(@Param("seniorId") Long seniorId);

    // 목록 조회: 두 약을 함께 로딩(LazyInitialization 방지). 정렬은 서비스에서 등급 우선순위로 처리.
    // 두 약이 모두 활성(is_active=true)이고 삭제되지 않은 충돌만 노출 — 복용 종료·삭제된 약의 stale 충돌 방지.
    @Query("""
            SELECT dc FROM DrugConflict dc
            JOIN FETCH dc.medication1 m1
            JOIN FETCH dc.medication2 m2
            WHERE dc.senior.id = :seniorId
              AND m1.isActive = true
              AND m2.isActive = true
              AND m1.deletedAt IS NULL
              AND m2.deletedAt IS NULL
            ORDER BY dc.id ASC
            """)
    List<DrugConflict> findAllWithMedicationsBySeniorId(@Param("seniorId") Long seniorId);

    // 상세 조회: 두 약을 함께 로딩. 노출 기준은 목록과 동일하게 맞춘다 —
    // 목록에서 사라진 충돌(복용 종료·삭제된 약)은 상세도 열리지 않아야 유효하지 않은 경고를 현재 위험으로 오인하지 않는다.
    @Query("""
            SELECT dc FROM DrugConflict dc
            JOIN FETCH dc.medication1 m1
            JOIN FETCH dc.medication2 m2
            WHERE dc.id = :conflictId
              AND m1.isActive = true
              AND m2.isActive = true
              AND m1.deletedAt IS NULL
              AND m2.deletedAt IS NULL
            """)
    Optional<DrugConflict> findWithMedicationsById(@Param("conflictId") Long conflictId);
}