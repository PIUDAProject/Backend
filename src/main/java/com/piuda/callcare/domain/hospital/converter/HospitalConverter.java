package com.piuda.callcare.domain.hospital.converter;

import com.piuda.callcare.domain.hospital.document.HospitalDocument;
import com.piuda.callcare.domain.hospital.dto.response.HospitalSearchResponse;
import com.piuda.callcare.domain.hospital.dto.response.HospitalSyncHistoryResponse;
import com.piuda.callcare.domain.hospital.dto.response.HospitalSyncStartResponse;
import com.piuda.callcare.domain.hospital.entity.Hospital;
import com.piuda.callcare.domain.hospital.entity.HospitalSyncHistory;
import com.piuda.callcare.domain.hospital.service.command.HospitalSyncCommandService;
import org.springframework.stereotype.Component;

@Component
public class HospitalConverter {

    // Hospital(MySQL) → HospitalDocument(Elasticsearch 색인용)
    public HospitalDocument toDocument(Hospital hospital) {
        return HospitalDocument.builder()
                .id(String.valueOf(hospital.getId()))
                .name(hospital.getName())
                .address(hospital.getAddress())
                .phoneNumber(hospital.getPhoneNumber())
                .build();
    }

    // HospitalDocument(ES 검색 결과) → HospitalSearchResponse
    public HospitalSearchResponse toSearchResponse(HospitalDocument document) {
        return new HospitalSearchResponse(
                Long.parseLong(document.getId()),
                document.getName(),
                document.getAddress(),
                document.getPhoneNumber()
        );
    }

    public HospitalSyncStartResponse toSyncStartResponse(HospitalSyncCommandService.SyncStartResult result) {
        return new HospitalSyncStartResponse(
                result.historyId(),
                result.status()
        );
    }

    public HospitalSyncHistoryResponse toSyncHistoryResponse(HospitalSyncHistory history) {
        return new HospitalSyncHistoryResponse(
                history.getId(),
                history.getStatus(),
                history.isFullSync(),
                history.getStartedAt(),
                history.getFinishedAt(),
                history.getLastCompletedPage(),
                history.getLastProgressAt(),
                history.getRequestedCount(),
                history.getInsertedCount(),
                history.getUpdatedCount(),
                history.getFailedCount(),
                history.getErrorMessage()
        );
    }
}
