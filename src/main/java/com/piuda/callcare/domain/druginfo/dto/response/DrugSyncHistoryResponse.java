package com.piuda.callcare.domain.druginfo.dto.response;

import com.piuda.callcare.domain.druginfo.enums.DrugSyncStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "약품 재색인 이력")
public record DrugSyncHistoryResponse(

        @Schema(description = "이력 ID")
        Long id,

        @Schema(description = "상태 (RUNNING / SUCCESS / FAILED)")
        DrugSyncStatus status,

        @Schema(description = "시작 시각")
        LocalDateTime startedAt,

        @Schema(description = "종료 시각 (진행 중이면 null)")
        LocalDateTime finishedAt,

        @Schema(description = "색인된 문서 수")
        int indexedCount,

        @Schema(description = "색인 대상 물리 인덱스명 (성공 시)")
        String targetIndex,

        @Schema(description = "실패 사유 (실패 시)")
        String errorMessage
) {
}
