package com.piuda.callcare.domain.druginfo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "drug_info")
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
