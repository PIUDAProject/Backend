package com.piuda.callcare.domain.hospital.dto.response;

import com.piuda.callcare.domain.hospital.enums.HospitalSyncStatus;

public record HospitalSyncStartResponse(
        Long historyId,
        HospitalSyncStatus status
) {
}
