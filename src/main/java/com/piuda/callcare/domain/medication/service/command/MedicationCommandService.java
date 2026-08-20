package com.piuda.callcare.domain.medication.service.command;

import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import com.piuda.callcare.domain.druginfo.repository.DrugInfoRepository;
import com.piuda.callcare.domain.medication.converter.MedicationConverter;
import com.piuda.callcare.domain.medication.dto.request.MedicationCreateRequest;
import com.piuda.callcare.domain.medication.dto.request.MedicationUpdateRequest;
import com.piuda.callcare.domain.medication.dto.response.MedicationResponse;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.event.MedicationChangedEvent;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    // 약 1건 이상 일괄 등록 — 스케줄 자동 생성, 하나라도 실패 시 전체 롤백
    @Transactional
    public List<MedicationResponse> registerBatch(Long userId, List<MedicationCreateRequest> requests) {
        List<MedicationResponse> responses = requests.stream()
                .map(request -> doRegister(userId, request))
                .toList();

        // 약이 늘면 새 충돌이 생길 수 있다 — 어르신 단위로 한 번만 재분석하도록 중복을 제거해 발행한다.
        // 실제 분석은 이 트랜잭션이 커밋된 뒤에 돈다(MedicationChangedListener).
        requests.stream()
                .map(MedicationCreateRequest::seniorId)
                .distinct()
                .forEach(seniorId -> eventPublisher.publishEvent(new MedicationChangedEvent(seniorId)));

        return responses;
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

    // 약 정보 수정 — timesPerDay 변경 시 스케줄 삭제 후 재생성
    @Transactional
    public void update(Long userId, Long medicationId, MedicationUpdateRequest request) {
        Medication medication = medicationRepository.findByIdAndDeletedAtIsNull(medicationId)
                .orElseThrow(() -> new CallCareException(ErrorCode.MEDICATION_NOT_FOUND));
        seniorRepository.findByIdAndUser_Id(medication.getSenior().getId(), userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.FORBIDDEN));

        // startDate 또는 totalDays 가 바뀌면 endDate 재계산
        LocalDate newEndDate = null;
        if (request.startDate() != null || request.totalDays() != null) {
            LocalDate effectiveStart = request.startDate() != null ? request.startDate() : medication.getStartDate();
            if (effectiveStart != null) {
                Integer effectiveDays = request.totalDays() != null ? request.totalDays() : medication.getTotalDays();
                newEndDate = effectiveDays != null ? effectiveStart.plusDays(effectiveDays - 1) : null;
            }
        }

        medication.update(request.drugName(), request.dosagePerTime(), request.timesPerDay(),
                request.totalDays(), request.startDate(), newEndDate, request.hospitalName(), request.memo());

        // 복용 횟수 변경 시 스케줄 삭제 후 재생성( 3회→2회로 바꾸면 점심 스케줄 삭제, 2회→3회로 바꾸면 점심 스케줄 새로 생성)
        if (request.timesPerDay() != null) {
            medicationScheduleRepository.deleteAllByMedication_Id(medicationId);
            List<MedicationSchedule> newSchedules = createSchedules(medication, request.timesPerDay());
            medicationScheduleRepository.saveAll(newSchedules);
        }

        // 약 이름이 바뀌면 연결된 DrugInfo 해석이 달라져 충돌 결과도 달라질 수 있다.
        // 삭제(delete)에는 발행하지 않는다 — 약이 빠지는 것은 새 충돌을 만들지 않는다.
        eventPublisher.publishEvent(new MedicationChangedEvent(medication.getSenior().getId()));
    }

    // 약 소프트 삭제 — deleted_at만 채우고 스케줄·복약 로그·충돌 행은 그대로 남긴다(이력 보존 + FK 위반 회피).
    // 삭제일 당일부터 약물노트·상세·홈카드·충돌 목록에서 빠지고, 그 이전 날짜 조회와 리포트에는 계속 남는다.
    @Transactional
    public void delete(Long userId, Long medicationId) {
        Medication medication = medicationRepository.findByIdAndDeletedAtIsNull(medicationId)
                .orElseThrow(() -> new CallCareException(ErrorCode.MEDICATION_NOT_FOUND));
        seniorRepository.findByIdAndUser_Id(medication.getSenior().getId(), userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.FORBIDDEN));
        medication.softDelete();
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
