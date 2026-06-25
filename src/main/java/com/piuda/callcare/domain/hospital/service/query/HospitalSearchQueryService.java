package com.piuda.callcare.domain.hospital.service.query;

import com.piuda.callcare.domain.hospital.converter.HospitalConverter;
import com.piuda.callcare.domain.hospital.dto.response.HospitalSearchResponse;
import com.piuda.callcare.domain.hospital.repository.HospitalSearchRepository;
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
public class HospitalSearchQueryService {

    private final HospitalSearchRepository hospitalSearchRepository;
    private final HospitalConverter hospitalConverter;

    // Elasticsearch로 병원명 자동완성 검색 (match_phrase_prefix, 최대 20건)
    public List<HospitalSearchResponse> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new CallCareException(ErrorCode.INVALID_PARAMETER);
        }
        return hospitalSearchRepository.searchByName(keyword.trim(), PageRequest.of(0, 20))
                .stream()
                .map(hospitalConverter::toSearchResponse)
                .toList();
    }
}
