package com.piuda.callcare.domain.hospital.repository;

import com.piuda.callcare.domain.hospital.document.HospitalDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface HospitalSearchRepository extends ElasticsearchRepository<HospitalDocument, String> {

    // 병원명 자동완성 검색 (match_phrase_prefix: 입력한 prefix로 시작하는 병원명 반환)
    @Query("{\"match_phrase_prefix\": {\"name\": {\"query\": \"?0\", \"max_expansions\": 50}}}")
    List<HospitalDocument> searchByName(String keyword, Pageable pageable);
}
