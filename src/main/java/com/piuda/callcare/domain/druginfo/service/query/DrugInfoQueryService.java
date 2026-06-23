package com.piuda.callcare.domain.druginfo.service.query;

import com.piuda.callcare.domain.druginfo.converter.DrugInfoConverter;
import com.piuda.callcare.domain.druginfo.dto.response.DrugSearchResponse;
import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import com.piuda.callcare.domain.druginfo.repository.DrugInfoRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DrugInfoQueryService {

    private final DrugInfoRepository drugInfoRepository;
    private final DrugInfoConverter drugInfoConverter;

    // 약품명 키워드로 DB 검색 (최대 20건, 이름 오름차순)
    public List<DrugSearchResponse> searchByKeyword(String keyword) {
        List<DrugInfo> drugInfoList = drugInfoRepository
                .findByItemNameContainingIgnoreCaseOrderByItemNameAsc(keyword, PageRequest.of(0, 20));
        return drugInfoList.stream()
                .map(drugInfoConverter::toSearchResponse)
                .toList();
    }

    // 품목기준코드로 약품 상세 조회 → DrugSearchResponse 반환 (컨트롤러 응답용)
    public DrugSearchResponse getDetailByItemSeq(String itemSeq) {
        DrugInfo drugInfo = drugInfoRepository.findByItemSeq(itemSeq)
                .orElseThrow(() -> new CallCareException(ErrorCode.DRUG_NOT_FOUND));
        return drugInfoConverter.toSearchResponse(drugInfo);
    }

    // 품목기준코드로 DrugInfo 엔티티 반환 (다른 서비스에서 내부 조회 시 사용)
    public DrugInfo getByItemSeq(String itemSeq) {
        return drugInfoRepository.findByItemSeq(itemSeq)
                .orElseThrow(() -> new CallCareException(ErrorCode.DRUG_NOT_FOUND));
    }
}
