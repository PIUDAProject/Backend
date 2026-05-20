package com.piuda.callcare.domain.druginfo.entity;

import com.piuda.callcare.global.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "drug_info")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class DrugInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "drug_info_id")
    private Long id;

    @Column(name = "item_seq")
    private String itemSeq; // 품목기준코드

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "entp_name")
    private String entpName; // 제조사명

    @Column(name = "efcy_qesitm", columnDefinition = "TEXT")
    private String efcyQesitm; // 효능효과

    @Column(name = "use_method_qesitm", columnDefinition = "TEXT")
    private String useMethodQesitm; // 용법용량(복용 방법)

    @Column(name = "atpn_qesitm", columnDefinition = "TEXT")
    private String atpnQesitm; // 주의사항

    @Column(name = "se_qesitm", columnDefinition = "TEXT")
    private String seQesitm; // 부작용

    @Column(name = "intrc_qesitm", columnDefinition = "TEXT")
    private String intrcQesitm; // 상호작용

    @Column(name = "deposit_method_qesitm", columnDefinition = "TEXT")
    private String depositMethodQesitm; // 보관법

    @Column(name = "item_image")
    private String itemImage; // 약이미지

    @Column(name = "prduct_type")
    private String prductType; // 약 종류

    @Column(name = "spclty_pblc")
    private String spcltyPblc; // 전문/일반 의약품 구분

    @Builder
    public DrugInfo(String itemSeq, String itemName, String entpName,
                    String efcyQesitm, String useMethodQesitm, String atpnQesitm,
                    String seQesitm, String intrcQesitm, String depositMethodQesitm,
                    String itemImage, String prductType, String spcltyPblc) {
        this.itemSeq = itemSeq;
        this.itemName = itemName;
        this.entpName = entpName;
        this.efcyQesitm = efcyQesitm;
        this.useMethodQesitm = useMethodQesitm;
        this.atpnQesitm = atpnQesitm;
        this.seQesitm = seQesitm;
        this.intrcQesitm = intrcQesitm;
        this.depositMethodQesitm = depositMethodQesitm;
        this.itemImage = itemImage;
        this.prductType = prductType;
        this.spcltyPblc = spcltyPblc;
    }
}
