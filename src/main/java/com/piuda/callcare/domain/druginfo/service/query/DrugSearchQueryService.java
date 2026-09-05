package com.piuda.callcare.domain.druginfo.service.query;

import com.piuda.callcare.domain.druginfo.converter.DrugInfoConverter;
import com.piuda.callcare.domain.druginfo.dto.response.DrugAutofillResponse;
import com.piuda.callcare.domain.druginfo.dto.response.DrugSearchResponse;
import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import com.piuda.callcare.domain.druginfo.repository.DrugSearchRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DrugSearchQueryService {

    private final DrugSearchRepository drugSearchRepository;
    private final DrugInfoQueryService drugInfoQueryService;
    private final DrugInfoConverter drugInfoConverter;

    /**
     * Elasticsearch 약품명 검색 (자동완성·오타·중간 단어·초성 통합, 관련도 순, 최대 20건).
     * <p>
     * ES 연결 실패·타임아웃 등 저장소 접근 예외({@link DataAccessException})가 나면
     * MySQL {@code LIKE} 검색(폴백)으로 자동 전환한다. 파라미터 검증 실패({@link CallCareException})는
     * 이 catch 대상이 아니므로 그대로 400으로 전파된다.
     */
    public List<DrugSearchResponse> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new CallCareException(ErrorCode.INVALID_PARAMETER);
        }
        String trimmed = keyword.trim();
        try {
            return drugSearchRepository.searchByItemName(trimmed, PageRequest.of(0, 20))
                    .stream()
                    .map(drugInfoConverter::toSearchResponse)
                    .toList();
        } catch (DataAccessException e) {
            log.warn("ES 약품 검색 실패 - MySQL 폴백으로 전환. keyword={}", trimmed, e);
            return drugInfoQueryService.searchByKeyword(trimmed);
        }
    }

    // 선택한 약의 자동 입력 데이터 반환 (drugName, drugType, memo 자동 생성)
    public DrugAutofillResponse getAutofill(String itemSeq) {
        DrugInfo drugInfo = drugInfoQueryService.getByItemSeq(itemSeq);
        return drugInfoConverter.toAutofillResponse(drugInfo);
    }
}
