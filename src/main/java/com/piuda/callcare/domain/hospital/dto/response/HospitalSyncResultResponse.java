package com.piuda.callcare.domain.hospital.dto.response;

import com.piuda.callcare.domain.hospital.enums.HospitalSyncStatus;

public record HospitalSyncResultResponse(
        HospitalSyncStatus status,
        int requestedCount,
        int insertedCount,
        int updatedCount,
        int failedCount
) {
}
