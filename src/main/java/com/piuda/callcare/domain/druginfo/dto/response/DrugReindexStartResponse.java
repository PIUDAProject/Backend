package com.piuda.callcare.domain.druginfo.dto.response;

import com.piuda.callcare.domain.druginfo.enums.DrugSyncStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "약품 재색인 시작 응답")
public record DrugReindexStartResponse(

        @Schema(description = "재색인 이력 ID - 진행 상태 조회에 사용")
        Long historyId,

        @Schema(description = "재색인 상태 (요청 접수 시 RUNNING)")
        DrugSyncStatus status
) {
}
