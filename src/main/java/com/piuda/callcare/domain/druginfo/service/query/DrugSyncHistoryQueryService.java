package com.piuda.callcare.domain.druginfo.service.query;

import com.piuda.callcare.domain.druginfo.entity.DrugSyncHistory;
import com.piuda.callcare.domain.druginfo.repository.DrugSyncHistoryRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class DrugSyncHistoryQueryService {

    private final DrugSyncHistoryRepository drugSyncHistoryRepository;

    public List<DrugSyncHistory> getRecentHistories(int limit) {
        return drugSyncHistoryRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit));
    }

    public DrugSyncHistory getHistory(Long historyId) {
        return drugSyncHistoryRepository.findById(historyId)
                .orElseThrow(() -> new CallCareException(ErrorCode.NOT_FOUND, "약품 재색인 이력을 찾을 수 없습니다."));
    }
}
