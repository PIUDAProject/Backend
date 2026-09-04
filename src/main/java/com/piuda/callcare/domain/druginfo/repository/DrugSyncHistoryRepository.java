package com.piuda.callcare.domain.druginfo.repository;

import com.piuda.callcare.domain.druginfo.entity.DrugSyncHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrugSyncHistoryRepository extends JpaRepository<DrugSyncHistory, Long> {

    // 최근 재색인 이력 최신순
    List<DrugSyncHistory> findAllByOrderByStartedAtDesc(Pageable pageable);
}
