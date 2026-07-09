package com.piuda.callcare.domain.medication.service.query;

import com.piuda.callcare.domain.medication.converter.MedicationConverter;
import com.piuda.callcare.domain.medication.dto.response.MedicationGroupItemResponse;
import com.piuda.callcare.domain.medication.dto.response.MedicationNoteGroupResponse;
import com.piuda.callcare.domain.medication.dto.response.MedicationNoteItemResponse;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MedicationQueryService {

    private final MedicationRepository medicationRepository;
    private final SeniorRepository seniorRepository;
    private final MedicationConverter medicationConverter;

    // 병원+처방일 그룹의 약 상세 목록 조회
    public List<MedicationGroupItemResponse> getGroup(Long userId, Long seniorId, String hospitalName, LocalDate prescriptionDate) {
        if (userId == null) {
            throw new CallCareException(ErrorCode.FORBIDDEN);
        }
        seniorRepository.findByIdAndUser_Id(seniorId, userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));
        List<Medication> medications = medicationRepository.findByGroup(seniorId, hospitalName, prescriptionDate);
        return medications.stream()
                .map(medicationConverter::toGroupItemResponse)
                .toList();
    }

    // 시니어의 약 전체 목록 (활성/비활성 모두) — startDate+병원 기준 그룹화
    public List<MedicationNoteGroupResponse> getNoteList(Long userId, Long seniorId) {
        if (userId == null) throw new CallCareException(ErrorCode.FORBIDDEN);
        seniorRepository.findByIdAndUser_Id(seniorId, userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));
        List<Medication> medications = medicationRepository.findAllBySeniorId(seniorId);
        return toNoteGroupResponses(medications);
    }

    // 약 이름/별명/병원명 키워드 검색 + 기간 필터 (1w·1m·3m·1y)
    public List<MedicationNoteGroupResponse> searchNotes(Long userId, Long seniorId, String keyword, String period) {
        if (userId == null) throw new CallCareException(ErrorCode.FORBIDDEN);
        seniorRepository.findByIdAndUser_Id(seniorId, userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));
        LocalDate fromDate = resolveFromDate(period);
        List<Medication> medications = medicationRepository.searchByKeyword(seniorId, keyword, fromDate);
        return toNoteGroupResponses(medications);
    }

    private List<MedicationNoteGroupResponse> toNoteGroupResponses(List<Medication> medications) {
        Map<String, List<Medication>> grouped = new LinkedHashMap<>();
        for (Medication m : medications) {
            String key = m.getStartDate() + "|" + m.getHospitalName();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        return grouped.values().stream()
                .map(group -> {
                    Medication first = group.get(0);
                    List<MedicationNoteItemResponse> items = group.stream()
                            .map(medicationConverter::toNoteItemResponse)
                            .toList();
                    return new MedicationNoteGroupResponse(first.getStartDate(), first.getHospitalName(), items);
                })
                .toList();
    }

    private LocalDate resolveFromDate(String period) {
        LocalDate today = LocalDate.now();
        return switch (period == null ? "1y" : period.toLowerCase()) {
            case "1w" -> today.minusWeeks(1);
            case "1m" -> today.minusMonths(1);
            case "3m" -> today.minusMonths(3);
            default -> today.minusYears(1);
        };
    }
}
