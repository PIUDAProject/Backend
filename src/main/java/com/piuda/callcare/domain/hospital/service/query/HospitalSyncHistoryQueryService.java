package com.piuda.callcare.domain.hospital.service.query;

import com.piuda.callcare.domain.hospital.entity.HospitalSyncHistory;
import com.piuda.callcare.domain.hospital.repository.HospitalSyncHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class HospitalSyncHistoryQueryService {

    private final HospitalSyncHistoryRepository hospitalSyncHistoryRepository;

    public List<HospitalSyncHistory> getRecentHistories(int limit) {
        return hospitalSyncHistoryRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit));
    }
}
