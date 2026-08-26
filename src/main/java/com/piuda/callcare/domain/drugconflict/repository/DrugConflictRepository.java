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

    // 충돌 알림 발송용: 두 약에 더해 senior·user까지 로딩한다 — 발송은 트랜잭션 밖에서 일어나므로
    // 수신자(보호자)를 지연 로딩할 수 없다. 노출 조건(활성·미삭제)은 목록/상세와 동일하게 맞춘다:
    // 이벤트 발행과 발송 사이에 약이 삭제됐다면 이미 유효하지 않은 경고이므로 보내지 않는다.
    @Query("""
            SELECT dc FROM DrugConflict dc
            JOIN FETCH dc.senior s
            JOIN FETCH s.user u
            JOIN FETCH dc.medication1 m1
            JOIN FETCH dc.medication2 m2
            WHERE dc.id = :conflictId
              AND m1.isActive = true
              AND m2.isActive = true
              AND m1.deletedAt IS NULL
              AND m2.deletedAt IS NULL
            """)
    Optional<DrugConflict> findForNotificationById(@Param("conflictId") Long conflictId);

    // 목록 조회: 두 약을 함께 로딩(LazyInitialization 방지). 정렬은 서비스에서 등급 우선순위로 처리.
    // 두 약이 모두 활성(is_active=true)이고 삭제되지 않은 충돌만 노출 — 복용 종료·삭제된 약의 stale 충돌 방지.
    // 보호자가 확인(is_resolved=true)한 조합도 제외한다 — 목록은 "아직 조치가 필요한 위험"만 보여준다.
    // 제외는 목록에만 건다. 상세 조회는 확인한 충돌도 열리며(아래 findWithMedicationsById), 그것이
    // 확인 취소(undo)를 따로 만들지 않고도 확인한 내용을 다시 볼 수 있는 경로다.
    // 확인한 조합이라도 등급이 오르면 DrugConflict.updateAnalysis가 is_resolved를 false로 되돌려 다시 나온다.
    @Query("""
            SELECT dc FROM DrugConflict dc
            JOIN FETCH dc.medication1 m1
            JOIN FETCH dc.medication2 m2
            WHERE dc.senior.id = :seniorId
              AND dc.isResolved = false
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

    // 확인 처리용: 상세 조회와 같은 노출 조건에 보호자 소유 조건을 더한다.
    // 소유자 조건을 조회에 넣어 남의 충돌을 확인 처리할 수 없게 한다 — 남의 것이면 NOT_FOUND가
    // 나가는데, 403과 구분하지 않는 것이 의도다(id 존재 여부 노출 방지, 알림 읽음 처리와 같은 논리).
    @Query("""
            SELECT dc FROM DrugConflict dc
            JOIN dc.senior s
            JOIN FETCH dc.medication1 m1
            JOIN FETCH dc.medication2 m2
            WHERE dc.id = :conflictId
              AND s.user.id = :userId
              AND m1.isActive = true
              AND m2.isActive = true
              AND m1.deletedAt IS NULL
              AND m2.deletedAt IS NULL
            """)
    Optional<DrugConflict> findWithMedicationsByIdAndUserId(@Param("conflictId") Long conflictId, @Param("userId") Long userId);
}