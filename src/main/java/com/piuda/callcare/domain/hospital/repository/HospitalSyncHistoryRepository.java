package com.piuda.callcare.domain.hospital.repository;

import com.piuda.callcare.domain.hospital.entity.HospitalSyncHistory;
import com.piuda.callcare.domain.hospital.enums.HospitalSyncStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HospitalSyncHistoryRepository extends JpaRepository<HospitalSyncHistory, Long> {

    List<HospitalSyncHistory> findAllByOrderByStartedAtDesc(Pageable pageable);

    boolean existsByStatus(HospitalSyncStatus status);

    List<HospitalSyncHistory> findTop1ByStatusInAndFullSyncTrueOrderByStartedAtDesc(List<HospitalSyncStatus> statuses);
}
