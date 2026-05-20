package com.piuda.callcare.domain.ocrresult.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OcrType {
    PRESCRIPTION("처방전"),
    DRUG_BAG("약봉투"),
    DRUG_BOX("약곽");

    private final String description;
}
