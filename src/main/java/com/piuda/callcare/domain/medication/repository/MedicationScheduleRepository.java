package com.piuda.callcare.domain.medication.repository;

import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, Long> {
}
