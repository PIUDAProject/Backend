package com.piuda.callcare.domain.medication.repository;

import java.time.LocalDate;
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

    // 약물노트 전체 리스트: 활성/비활성 관계없이 전체 조회 (startDate DESC → hospitalName ASC 정렬)
    @Query("""
            SELECT m FROM Medication m
            WHERE m.senior.id = :seniorId
            ORDER BY m.startDate DESC, m.hospitalName ASC NULLS LAST
            """)
    List<Medication> findAllBySeniorId(@Param("seniorId") Long seniorId);

    // 약물노트 검색: 약 이름/별명/병원명 키워드 + 날짜 범위 필터 (활성/비활성 모두 포함)
    @Query("""
            SELECT m FROM Medication m
            WHERE m.senior.id = :seniorId
              AND m.startDate >= :fromDate
              AND (LOWER(m.drugName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(m.drugNickname) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(m.hospitalName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY m.startDate DESC, m.hospitalName ASC NULLS LAST
            """)
    List<Medication> searchByKeyword(@Param("seniorId") Long seniorId,
                                     @Param("keyword") String keyword,
                                     @Param("fromDate") LocalDate fromDate);

    // 약물노트 그룹 상세 조회: 병원명 + 처방일 조합이 그룹 키 (둘 다 null 가능)
    @Query("""
            SELECT m FROM Medication m
            WHERE m.senior.id = :seniorId
              AND ((:hospitalName IS NULL AND m.hospitalName IS NULL) OR m.hospitalName = :hospitalName)
              AND ((:prescriptionDate IS NULL AND m.prescriptionDate IS NULL) OR m.prescriptionDate = :prescriptionDate)
              AND m.isActive = true
            ORDER BY m.createdAt ASC
            """)
    List<Medication> findByGroup(@Param("seniorId") Long seniorId,
                                 @Param("hospitalName") String hospitalName,
                                 @Param("prescriptionDate") LocalDate prescriptionDate);
}
