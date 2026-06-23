package com.piuda.callcare.domain.druginfo.repository;

import com.piuda.callcare.domain.druginfo.document.DrugDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface DrugSearchRepository extends ElasticsearchRepository<DrugDocument, String> {

    // 약품명 자동완성 검색 (match_phrase_prefix: 입력한 prefix로 시작하는 약품명 반환)
    @Query("{\"match_phrase_prefix\": {\"itemName\": {\"query\": \"?0\", \"max_expansions\": 50}}}")
    List<DrugDocument> searchByItemName(String keyword, Pageable pageable);
}
