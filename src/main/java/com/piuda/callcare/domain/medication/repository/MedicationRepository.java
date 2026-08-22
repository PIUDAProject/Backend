package com.piuda.callcare.domain.medication.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.piuda.callcare.domain.medication.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    // 단건 조회(상세·수정·삭제·토글 공용): 삭제된 약은 없는 것으로 취급해 MEDICATION_NOT_FOUND로 떨어지게 한다.
    // findById는 삭제 여부를 못 거르므로 현재 시점 단건 조회는 반드시 이 메서드를 쓴다.
    Optional<Medication> findByIdAndDeletedAtIsNull(Long medicationId);

    // 소진 예측용: 특정 어르신의 활성(복용 중) 약만 조회. 남은 일수는 endDate로 계산하므로 endDate가 있는 것만 대상으로 한다.
    @Query("""
            SELECT m FROM Medication m
            WHERE m.senior.id = :seniorId
              AND m.isActive = true
              AND m.deletedAt IS NULL
              AND m.endDate IS NOT NULL
            """)
    List<Medication> findActiveMedicationsForDepletion(@Param("seniorId") Long seniorId);

    // 소진 알림 배치용: 어르신 구분 없이 "잔여 0~3일"인 활성 약 전체를 보호자까지 한 번에 로딩(N+1 방지).
    // 발송이 트랜잭션 밖에서 일어나므로 senior/user를 반드시 JOIN FETCH 해야 한다(LazyInitializationException 방지).
    //
    // endDate에 하한(:today)을 두는 이유: 복용이 끝난 약도 isActive로 남아 있어(Medication.deactivate() 호출부 없음)
    // 하한이 없으면 이미 끝난 약에 매일 푸시가 나간다. DepletionCalculator.isDepleting()도 같은 하한을 갖지만,
    // 이 쿼리는 대상 전체를 미리 좁혀 로딩하는 용도라 조건을 쿼리에도 그대로 둔다.
    @Query("""
            SELECT m FROM Medication m
            JOIN FETCH m.senior s
            JOIN FETCH s.user u
            WHERE m.isActive = true
              AND m.deletedAt IS NULL
              AND m.endDate BETWEEN :today AND :until
            ORDER BY m.endDate ASC, m.id ASC
            """)
    List<Medication> findDepletingForNotification(@Param("today") LocalDate today,
                                                  @Param("until") LocalDate until);

    // 충돌 분석용: 특정 어르신의 활성 약 중 DrugInfo(상호작용 텍스트 소스)가 연결된 것만 조회.
    // JOIN FETCH(inner join)라 drugInfo가 없는 약은 자동 제외 → 상호작용 텍스트 없는 약은 대상 아님.
    @Query("""
            SELECT m FROM Medication m
            JOIN FETCH m.drugInfo
            WHERE m.senior.id = :seniorId
              AND m.isActive = true
              AND m.deletedAt IS NULL
            """)
    List<Medication> findActiveWithDrugInfoBySeniorId(@Param("seniorId") Long seniorId);

    // 약물노트 전체 리스트: isActive null이면 전체, true/false면 해당 상태만 조회
    @Query("""
            SELECT m FROM Medication m
            WHERE m.senior.id = :seniorId
              AND m.deletedAt IS NULL
              AND (:isActive IS NULL OR m.isActive = :isActive)
            ORDER BY m.startDate DESC, m.hospitalName ASC NULLS LAST
            """)
    List<Medication> findAllBySeniorId(@Param("seniorId") Long seniorId,
                                       @Param("isActive") Boolean isActive);

    // 약물노트 검색: 약 이름/별명/병원명 키워드 + 날짜 범위 + 상태 필터
    @Query("""
            SELECT m FROM Medication m
            WHERE m.senior.id = :seniorId
              AND m.startDate >= :fromDate
              AND m.deletedAt IS NULL
              AND (:isActive IS NULL OR m.isActive = :isActive)
              AND (LOWER(m.drugName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(m.drugNickname) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(m.hospitalName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY m.startDate DESC, m.hospitalName ASC NULLS LAST
            """)
    List<Medication> searchByKeyword(@Param("seniorId") Long seniorId,
                                     @Param("keyword") String keyword,
                                     @Param("fromDate") LocalDate fromDate,
                                     @Param("isActive") Boolean isActive);

    // 복약 기록 리포트: 최근 90일 이내 약 전체 — 병원·약 이름·시작일 기준 정렬 (연속 합산 로직은 서비스에서 처리)
    // deleted_at 조건을 일부러 넣지 않는다 — 리포트는 지난 복약 이력이므로 이후에 삭제한 약도 그대로 집계에 남긴다.
    @Query("""
            SELECT m FROM Medication m
            WHERE m.senior.id = :seniorId
              AND m.startDate >= :fromDate
              AND m.isActive = true
            ORDER BY m.hospitalName ASC NULLS LAST, m.drugName ASC, m.startDate ASC
            """)
    List<Medication> findForReport(@Param("seniorId") Long seniorId,
                                   @Param("fromDate") LocalDate fromDate);

    // 약물노트 그룹 상세 조회: 병원명 + 처방일 조합이 그룹 키 (둘 다 null 가능)
    @Query("""
            SELECT m FROM Medication m
            WHERE m.senior.id = :seniorId
              AND ((:hospitalName IS NULL AND m.hospitalName IS NULL) OR m.hospitalName = :hospitalName)
              AND ((:prescriptionDate IS NULL AND m.prescriptionDate IS NULL) OR m.prescriptionDate = :prescriptionDate)
              AND m.isActive = true
              AND m.deletedAt IS NULL
            ORDER BY m.createdAt ASC
            """)
    List<Medication> findByGroup(@Param("seniorId") Long seniorId,
                                 @Param("hospitalName") String hospitalName,
                                 @Param("prescriptionDate") LocalDate prescriptionDate);
}
