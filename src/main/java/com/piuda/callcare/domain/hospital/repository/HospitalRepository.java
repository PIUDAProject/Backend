package com.piuda.callcare.domain.hospital.repository;

import com.piuda.callcare.domain.hospital.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    List<Hospital> findAllByExternalIdIn(List<String> externalIds);

    List<Hospital> findAllByActiveTrueAndLastSyncedAtBefore(LocalDateTime cutoff);

    long countByActiveTrue();

    List<Hospital> findAllByActiveTrue();
}
