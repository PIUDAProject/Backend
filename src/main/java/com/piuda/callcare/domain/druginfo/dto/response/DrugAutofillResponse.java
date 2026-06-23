package com.piuda.callcare.domain.druginfo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "약 자동 입력 데이터")
public record DrugAutofillResponse(

        @Schema(description = "약품명")
        String drugName,

        @Schema(description = "약 종류")
        String drugType,

        @Schema(description = "메모 (복용방법 + 보관방법 자동 생성, 데이터 없으면 null)")
        String memo
) {
}
