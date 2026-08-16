package com.piuda.callcare.domain.hospital.dto.response;

import com.piuda.callcare.domain.hospital.enums.HospitalSyncStatus;

import java.time.LocalDateTime;

public record HospitalSyncHistoryResponse(
        Long id,
        HospitalSyncStatus status,
        boolean fullSync,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int lastCompletedPage,
        LocalDateTime lastProgressAt,
        int requestedCount,
        int insertedCount,
        int updatedCount,
        int failedCount,
        String errorMessage
) {
}
