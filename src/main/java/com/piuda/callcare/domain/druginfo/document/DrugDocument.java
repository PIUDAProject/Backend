package com.piuda.callcare.domain.druginfo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

// indexName("drug_info")은 물리 인덱스가 아니라 alias다 (DrugIndexManager.ALIAS와 반드시 일치).
// 물리 인덱스는 DrugIndexManager가 타임스탬프로 생성하고 alias를 스왑한다.
// createIndex=false로 두어 Spring Data가 alias 이름의 물리 인덱스를 자동 생성하지 못하게 한다.
@Document(indexName = "drug_info", createIndex = false)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugDocument {

    @Id
    private String itemSeq; // 품목기준코드

    @Field(type = FieldType.Text)
    private String itemName; // 약품명

    @Field(type = FieldType.Keyword)
    private String entpName; // 제조사명

    @Field(type = FieldType.Keyword)
    private String prductType; // 약 종류

    @Field(type = FieldType.Keyword)
    private String spcltyPblc; // 전문/일반 의약품 구분

    @Field(type = FieldType.Keyword)
    private String itemImage; // 약 이미지 URL
}
