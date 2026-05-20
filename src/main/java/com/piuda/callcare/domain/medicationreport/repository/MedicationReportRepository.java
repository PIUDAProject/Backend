package com.piuda.callcare.domain.medicationreport.repository;

import com.piuda.callcare.domain.medicationreport.entity.MedicationReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationReportRepository extends JpaRepository<MedicationReport, Long> {
}
