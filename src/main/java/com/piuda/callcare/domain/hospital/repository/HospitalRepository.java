package com.piuda.callcare.domain.hospital.repository;

import com.piuda.callcare.domain.hospital.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {
}
