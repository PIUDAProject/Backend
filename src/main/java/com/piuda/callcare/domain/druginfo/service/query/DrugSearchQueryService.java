package com.piuda.callcare.domain.druginfo.service.query;

import com.piuda.callcare.domain.druginfo.converter.DrugInfoConverter;
import com.piuda.callcare.domain.druginfo.dto.response.DrugAutofillResponse;
import com.piuda.callcare.domain.druginfo.dto.response.DrugSearchResponse;
import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import com.piuda.callcare.domain.druginfo.repository.DrugSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DrugSearchQueryService {

    private final DrugSearchRepository drugSearchRepository;
    private final DrugInfoQueryService drugInfoQueryService;
    private final DrugInfoConverter drugInfoConverter;

    // Elasticsearch로 약품명 자동완성 검색 (match_phrase_prefix, 최대 20건)
    public List<DrugSearchResponse> search(String keyword) {
        return drugSearchRepository.searchByItemName(keyword, PageRequest.of(0, 20))
                .stream()
                .map(drugInfoConverter::toSearchResponse)
                .toList();
    }

    // 선택한 약의 자동 입력 데이터 반환 (drugName, drugType, memo 자동 생성)
    public DrugAutofillResponse getAutofill(String itemSeq) {
        DrugInfo drugInfo = drugInfoQueryService.getByItemSeq(itemSeq);
        return drugInfoConverter.toAutofillResponse(drugInfo);
    }
}
