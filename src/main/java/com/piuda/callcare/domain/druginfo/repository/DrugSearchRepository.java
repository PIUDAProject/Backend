package com.piuda.callcare.domain.druginfo.repository;

import com.piuda.callcare.domain.druginfo.document.DrugDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface DrugSearchRepository extends ElasticsearchRepository<DrugDocument, String> {

    /**
     * 약품명 검색. bool.should 조합으로 자동완성·오타·중간 단어·초성을 한 번에 처리하고
     * boost로 관련도를 정렬한다 (완전/시작 일치가 상단).
     * <ul>
     *   <li>match_phrase_prefix(itemName)          : "입력한 그대로 시작" — 가장 강한 신호 (boost 5)</li>
     *   <li>match(itemName.autocomplete)           : edge n-gram 자동완성 (boost 2)</li>
     *   <li>match(itemName, fuzziness AUTO)         : 짧은 약품명의 1글자 오타 허용 (boost 2)</li>
     *   <li>match(itemName.ngram, 50%)             : 제품명 중간 단어·괄호 속 성분명·끝자리 오타 (boost 1)</li>
     *   <li>match(itemNameChosung)                 : 초성 검색 — 초성이 아니면 매칭 0이라 항상 포함해도 무해 (boost 3)</li>
     * </ul>
     * 한계: 한국어 형태소 분석(nori) 미도입이라 약품명이 통짜 토큰이라 "타이래놀" 같은 중간 음절 오타는 잘 못 잡는다.
     */
    @Query("""
            {
              "bool": {
                "minimum_should_match": 1,
                "should": [
                  { "match_phrase_prefix": { "itemName": { "query": "?0", "boost": 5.0 } } },
                  { "match": { "itemName.autocomplete": { "query": "?0", "boost": 2.0 } } },
                  { "match": { "itemName": { "query": "?0", "fuzziness": "AUTO", "boost": 2.0 } } },
                  { "match": { "itemName.ngram": { "query": "?0", "minimum_should_match": "50%", "boost": 1.0 } } },
                  { "match": { "itemNameChosung": { "query": "?0", "boost": 3.0 } } }
                ]
              }
            }
            """)
    List<DrugDocument> searchByItemName(String keyword, Pageable pageable);
}
