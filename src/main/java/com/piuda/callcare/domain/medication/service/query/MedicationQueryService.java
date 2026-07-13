package com.piuda.callcare.domain.medication.service.query;

import com.piuda.callcare.domain.medication.converter.MedicationConverter;
import com.piuda.callcare.domain.medication.dto.response.MedicationDetailResponse;
import com.piuda.callcare.domain.medication.dto.response.MedicationGroupItemResponse;
import com.piuda.callcare.domain.medication.dto.response.MedicationNoteGroupResponse;
import com.piuda.callcare.domain.medication.dto.response.MedicationNoteItemResponse;
import com.piuda.callcare.domain.medication.dto.response.MedicationReportGroupResponse;
import com.piuda.callcare.domain.medication.dto.response.MedicationReportItemResponse;
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

    // 약 단건 상세 조회 — 재등록 화면 프리필용
    public MedicationDetailResponse getDetail(Long userId, Long medicationId) {
        if (userId == null) throw new CallCareException(ErrorCode.FORBIDDEN);
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new CallCareException(ErrorCode.MEDICATION_NOT_FOUND));
        seniorRepository.findByIdAndUser_Id(medication.getSenior().getId(), userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.FORBIDDEN));
        return medicationConverter.toDetailResponse(medication);
    }

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

    // 시니어의 약 목록 — isActive: true(복용중) / false(복용완료) / null(전체)
    public List<MedicationNoteGroupResponse> getNoteList(Long userId, Long seniorId, Boolean isActive) {
        if (userId == null) throw new CallCareException(ErrorCode.FORBIDDEN);
        seniorRepository.findByIdAndUser_Id(seniorId, userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));
        List<Medication> medications = medicationRepository.findAllBySeniorId(seniorId, isActive);
        return toNoteGroupResponses(medications);
    }

    // 약 이름/별명/병원명 키워드 검색 + 기간 필터 + 상태 필터
    public List<MedicationNoteGroupResponse> searchNotes(Long userId, Long seniorId, String keyword, String period, Boolean isActive) {
        if (userId == null) throw new CallCareException(ErrorCode.FORBIDDEN);
        seniorRepository.findByIdAndUser_Id(seniorId, userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));
        LocalDate fromDate = resolveFromDate(period);
        List<Medication> medications = medicationRepository.searchByKeyword(seniorId, keyword, fromDate, isActive);
        return toNoteGroupResponses(medications);
    }

    // 최근 90일 복약 기록 리포트 — 연속 처방 합산 후 병원별 그룹 반환
    public List<MedicationReportGroupResponse> getReport(Long userId, Long seniorId) {
        if (userId == null) throw new CallCareException(ErrorCode.FORBIDDEN);
        seniorRepository.findByIdAndUser_Id(seniorId, userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));

        LocalDate fromDate = LocalDate.now().minusDays(90); // 최근 90일
        List<Medication> medications = medicationRepository.findForReport(seniorId, fromDate);

        // (hospitalName|drugName) 단위로 그룹화 — DB 정렬 덕분에 hospitalName·drugName·startDate 순으로 이미 정렬됨
        Map<String, List<Medication>> byDrug = new LinkedHashMap<>();
        for (Medication m : medications) {
            String key = m.getHospitalName() + "|" + m.getDrugName();
            byDrug.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }

        // 약별로 연속 처방 합산 → 병원별 재그룹화
        Map<String, List<MedicationReportItemResponse>> byHospital = new LinkedHashMap<>();
        for (List<Medication> group : byDrug.values()) {
            String hospitalName = group.get(0).getHospitalName();
            byHospital.computeIfAbsent(hospitalName, k -> new ArrayList<>())
                    .addAll(mergeConsecutive(group));
        }

        return byHospital.entrySet().stream()
                .map(e -> new MedicationReportGroupResponse(e.getKey(), e.getValue()))
                .toList();
    }

    private List<MedicationReportItemResponse> mergeConsecutive(List<Medication> sorted) {
        List<MedicationReportItemResponse> result = new ArrayList<>();
        if (sorted.isEmpty()) return result;

        Medication first = sorted.get(0);
        LocalDate mergedStart = first.getStartDate();
        LocalDate mergedEnd = first.getEndDate();
        Integer mergedDays = first.getTotalDays();
        LocalDate reportDate = resolveReportDate(first);

        for (int i = 1; i < sorted.size(); i++) {
            Medication curr = sorted.get(i);
            boolean consecutive = mergedEnd != null && curr.getStartDate() != null
                    && curr.getStartDate().equals(mergedEnd.plusDays(1));
            if (consecutive) {
                mergedEnd = curr.getEndDate();
                mergedDays = (mergedDays != null && curr.getTotalDays() != null)
                        ? mergedDays + curr.getTotalDays() : null;
            } else {
                result.add(new MedicationReportItemResponse(
                        first.getDrugName(), first.getDrugType(),
                        mergedStart, mergedEnd, mergedDays, reportDate));
                mergedStart = curr.getStartDate();
                mergedEnd = curr.getEndDate();
                mergedDays = curr.getTotalDays();
                reportDate = resolveReportDate(curr);
                first = curr;
            }
        }
        result.add(new MedicationReportItemResponse(
                first.getDrugName(), first.getDrugType(),
                mergedStart, mergedEnd, mergedDays, reportDate));
        return result;
    }

    private List<MedicationNoteGroupResponse> toNoteGroupResponses(List<Medication> medications) {
        Map<String, List<Medication>> grouped = new LinkedHashMap<>();
        for (Medication m : medications) {
            LocalDate groupDate = m.getPrescriptionDate() != null ? m.getPrescriptionDate() : m.getStartDate();
            String key = groupDate + "|" + m.getHospitalName();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        return grouped.values().stream()
                .map(group -> {
                    Medication first = group.get(0);
                    LocalDate groupDate = first.getPrescriptionDate() != null ? first.getPrescriptionDate() : first.getStartDate();
                    List<MedicationNoteItemResponse> items = group.stream()
                            .map(medicationConverter::toNoteItemResponse)
                            .toList();
                    return new MedicationNoteGroupResponse(groupDate, first.getHospitalName(), items);
                })
                .toList();
    }

    private LocalDate resolveReportDate(Medication m) {
        return m.getPrescriptionDate() != null
                ? m.getPrescriptionDate()
                : m.getCreatedAt().toLocalDate();
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
