package com.piuda.callcare.domain.medicationlog.repository;

import com.piuda.callcare.domain.medicationlog.entity.MedicationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationLogRepository extends JpaRepository<MedicationLog, Long> {
}
