package com.piuda.callcare.domain.druginfo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

// indexName("drug_info")은 물리 인덱스가 아니라 alias다 (DrugIndexManager.ALIAS와 반드시 일치).
// 물리 인덱스는 DrugIndexManager가 타임스탬프로 생성하고 alias를 스왑한다.
// createIndex=false로 두어 Spring Data가 alias 이름의 물리 인덱스를 자동 생성하지 못하게 한다.
// 애널라이저 정의는 @Setting 파일에 있으며, DrugIndexManager.createTimestampedIndex()가 색인 시 적용한다.
@Document(indexName = "drug_info", createIndex = false)
@Setting(settingPath = "/elasticsearch/drug-info-settings.json")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugDocument {

    @Id
    private String itemSeq; // 품목기준코드

    // 약품명: 한 값을 3가지로 색인
    //  - itemName              : standard + lowercase (정확/시작 매칭, 관련도 기준)
    //  - itemName.autocomplete : edge n-gram (앞에서부터 타이핑하는 자동완성)
    //  - itemName.ngram        : n-gram 2~4 (제품명 중간 단어·괄호 속 성분명)
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "drug_search_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword),
                    @InnerField(suffix = "autocomplete", type = FieldType.Text,
                            analyzer = "drug_edge_ngram_analyzer", searchAnalyzer = "drug_search_analyzer"),
                    @InnerField(suffix = "ngram", type = FieldType.Text,
                            analyzer = "drug_ngram_analyzer", searchAnalyzer = "drug_ngram_analyzer")
            }
    )
    private String itemName;

    // 약품명의 초성 문자열 (색인 시 HangulChosungExtractor로 생성). "ㅌㅇㄹㄴ" 같은 초성 검색용.
    // 통짜 토큰(keyword)으로 색인하고 검색은 match_phrase_prefix로 접두 매칭 → 입력 길이 제한 없음.
    @Field(type = FieldType.Text, analyzer = "keyword", searchAnalyzer = "keyword")
    private String itemNameChosung;

    @Field(type = FieldType.Keyword)
    private String entpName; // 제조사명

    @Field(type = FieldType.Keyword)
    private String prductType; // 약 종류

    @Field(type = FieldType.Keyword)
    private String spcltyPblc; // 전문/일반 의약품 구분

    @Field(type = FieldType.Keyword)
    private String itemImage; // 약 이미지 URL
}
