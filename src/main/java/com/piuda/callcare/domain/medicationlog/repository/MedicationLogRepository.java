package com.piuda.callcare.domain.medicationlog.repository;

import java.time.LocalDate;
import java.util.List;

import com.piuda.callcare.domain.medicationlog.entity.MedicationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationLogRepository extends JpaRepository<MedicationLog, Long> {

    // 홈카드 완료 상태 합성용: 특정 어르신의 해당 날짜 복약 로그 전체 조회
    List<MedicationLog> findBySenior_IdAndTakenDate(Long seniorId, LocalDate takenDate);
}
