package com.piuda.callcare.domain.medication.service.command;

import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import com.piuda.callcare.domain.druginfo.repository.DrugInfoRepository;
import com.piuda.callcare.domain.medication.converter.MedicationConverter;
import com.piuda.callcare.domain.medication.dto.request.MedicationCreateRequest;
import com.piuda.callcare.domain.medication.dto.response.MedicationResponse;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationCommandService {

    private final MedicationRepository medicationRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final SeniorRepository seniorRepository;
    private final DrugInfoRepository drugInfoRepository;
    private final MedicationConverter medicationConverter;

    @Transactional
    public List<MedicationResponse> registerBatch(Long userId, List<MedicationCreateRequest> requests) {
        return requests.stream()
                .map(request -> doRegister(userId, request))
                .toList();
    }

    private MedicationResponse doRegister(Long userId, MedicationCreateRequest request) {
        // 어르신 소유권 검증: userId가 실제로 이 senior를 관리하는지 확인
        Senior senior = seniorRepository.findByIdAndUser_Id(request.seniorId(), userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));

        // DrugInfo — ES 검색으로 선택한 경우, 없으면 null (약 이름은 request.drugName() 사용)
        DrugInfo drugInfo = resolveDrugInfo(request.drugInfoId());

        // 종료일 계산: startDate + (totalDays - 1)
        LocalDate endDate = (request.totalDays() != null)
                ? request.startDate().plusDays(request.totalDays() - 1)
                : null;

        Medication medication = Medication.builder()
                .senior(senior)
                .hospitalName(request.hospitalName())
                .drugInfo(drugInfo)
                .drugName(request.drugName())
                .dosagePerTime(request.dosagePerTime())
                .timesPerDay(request.timesPerDay())
                .totalDays(request.totalDays())
                .startDate(request.startDate())
                .endDate(endDate)
                .prescriptionDate(request.prescriptionDate())
                .usageStorageInfo(buildUsageStorageInfo(drugInfo))
                .memo(request.memo())
                .isActive(true)
                .ocrResultId(request.ocrResultId())
                .build();

        Medication saved = medicationRepository.save(medication);

        // timesPerDay 기준으로 MedicationSchedule 자동 생성
        List<MedicationSchedule> schedules = createSchedules(saved, request.timesPerDay());
        medicationScheduleRepository.saveAll(schedules);

        log.info("약 등록 완료 - medicationId: {}, schedules: {}", saved.getId(),
                schedules.stream().map(s -> s.getMealTime().name()).toList());

        return medicationConverter.toResponse(saved, schedules);
    }

    private List<MedicationSchedule> createSchedules(Medication medication, Integer timesPerDay) {
        if (timesPerDay == null) return List.of();

        // 1회 → 아침 / 2회 → 아침+저녁 / 3회 → 아침+점심+저녁 / 4회 이상 → 전체
        List<MealTime> mealTimes = switch (timesPerDay) {
            case 1 -> List.of(MealTime.BREAKFAST);
            case 2 -> List.of(MealTime.BREAKFAST, MealTime.DINNER);
            case 3 -> List.of(MealTime.BREAKFAST, MealTime.LUNCH, MealTime.DINNER);
            default -> List.of(MealTime.BREAKFAST, MealTime.LUNCH, MealTime.DINNER, MealTime.BEDTIME);
        };

        return mealTimes.stream()
                .map(mealTime -> MedicationSchedule.builder()
                        .medication(medication)
                        .mealTime(mealTime)
                        .build())
                .toList();
    }

    private DrugInfo resolveDrugInfo(Long drugInfoId) {
        if (drugInfoId == null) return null;
        return drugInfoRepository.findById(drugInfoId)
                .orElseThrow(() -> new CallCareException(ErrorCode.DRUG_NOT_FOUND));
    }

    private String buildUsageStorageInfo(DrugInfo drugInfo) {
        if (drugInfo == null) return null;
        String usage = drugInfo.getUseMethodQesitm();
        String deposit = drugInfo.getDepositMethodQesitm();
        if (usage == null && deposit == null) return null;
        if (usage == null) return deposit;
        if (deposit == null) return usage;
        return usage + "\n\n" + deposit;
    }
}
