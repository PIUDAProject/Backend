package com.piuda.callcare.domain.druginfo.converter;

import com.piuda.callcare.domain.druginfo.document.DrugDocument;
import com.piuda.callcare.domain.druginfo.dto.response.DrugAutofillResponse;
import com.piuda.callcare.domain.druginfo.dto.response.DrugSearchResponse;
import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import org.springframework.stereotype.Component;

@Component
public class DrugInfoConverter {

    // DrugInfo(MySQL) → DrugDocument(Elasticsearch 색인용)
    public DrugDocument toDocument(DrugInfo drugInfo) {
        return DrugDocument.builder()
                .itemSeq(drugInfo.getItemSeq())
                .itemName(drugInfo.getItemName())
                .entpName(drugInfo.getEntpName())
                .prductType(drugInfo.getPrductType())
                .spcltyPblc(drugInfo.getSpcltyPblc())
                .itemImage(drugInfo.getItemImage())
                .build();
    }

    // DrugDocument(ES 검색 결과) → DrugSearchResponse
    public DrugSearchResponse toSearchResponse(DrugDocument document) {
        return new DrugSearchResponse(
                document.getItemSeq(),
                document.getItemName(),
                document.getEntpName(),
                document.getPrductType(),
                document.getSpcltyPblc(),
                document.getItemImage()
        );
    }

    // DrugInfo(MySQL) → DrugSearchResponse (DB 직접 조회 시)
    public DrugSearchResponse toSearchResponse(DrugInfo drugInfo) {
        return new DrugSearchResponse(
                drugInfo.getItemSeq(),
                drugInfo.getItemName(),
                drugInfo.getEntpName(),
                drugInfo.getPrductType(),
                drugInfo.getSpcltyPblc(),
                drugInfo.getItemImage()
        );
    }

    // DrugInfo(MySQL) → DrugAutofillResponse (약 등록 자동 입력용)
    // memo: useMethodQesitm(복용방법) + depositMethodQesitm(보관방법) 조합 자동 생성
    public DrugAutofillResponse toAutofillResponse(DrugInfo drugInfo) {
        String memo = buildMemo(drugInfo.getUseMethodQesitm(), drugInfo.getDepositMethodQesitm());
        return new DrugAutofillResponse(
                drugInfo.getItemName(),
                drugInfo.getPrductType(),
                memo
        );
    }

    private String buildMemo(String useMethod, String depositMethod) {
        if (useMethod == null && depositMethod == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (useMethod != null) {
            sb.append("[복용방법] ").append(useMethod);
        }
        if (depositMethod != null) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append("[보관방법] ").append(depositMethod);
        }
        return sb.toString();
    }
}
