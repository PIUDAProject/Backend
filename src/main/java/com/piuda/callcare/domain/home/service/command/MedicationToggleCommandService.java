package com.piuda.callcare.domain.home.service.command;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.home.dto.response.MedicationLogToggleResponse;
import com.piuda.callcare.domain.home.enums.CompletedStatus;
import com.piuda.callcare.domain.home.enums.HomeCardMode;
import com.piuda.callcare.domain.home.service.MealTimeCompletionCalculator;
import com.piuda.callcare.domain.home.service.MealTimeCompletionCalculator.TakenKey;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.medicationlog.entity.MedicationLog;
import com.piuda.callcare.domain.medicationlog.repository.MedicationLogRepository;
import com.piuda.callcare.domain.medicationlog.service.command.MedicationLogCommandService;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class MedicationToggleCommandService {

    private final MedicationRepository medicationRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final MedicationLogRepository medicationLogRepository;
    private final MedicationLogCommandService medicationLogCommandService;
    private final MealTimeCompletionCalculator completionCalculator;

    // 수동 체크: 오늘 (약, 시간대) 복약 로그의 isTaken을 토글하고, 그 시간대의 완료 상태를 재계산해 반환
    public MedicationLogToggleResponse toggle(Long medicationId, MealTime mealTime, LocalDate date) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();

        // 1) TODAY만 허용 (PAST/FUTURE 차단)
        if (HomeCardMode.from(targetDate) != HomeCardMode.TODAY) {
            throw new CallCareException(ErrorCode.MEDICATION_LOG_TOGGLE_NOT_TODAY);
        }

        // 2) 약 존재 검증
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new CallCareException(ErrorCode.MEDICATION_NOT_FOUND));

        // 3) 그 약에 해당 시간대의 오늘 활성 스케줄이 실제 존재하는지 검증 (완료 재계산과 동일 기준)
        if (!medicationScheduleRepository.existsActiveScheduleForToggle(medicationId, mealTime, targetDate)) {
            throw new CallCareException(ErrorCode.MEDICATION_SCHEDULE_NOT_FOUND);
        }

        // TODO: 인증 도입 후 seniorId 소유권 검증 추가 (medication.getSenior()가 로그인 사용자 소유인지)

        // 4) 현재 상태를 읽어 반대값으로 토글 (로그가 없으면 첫 체크 → true)
        boolean currentlyTaken = medicationLogRepository
                .findByMedication_IdAndTakenDateAndMealTime(medicationId, targetDate, mealTime)
                .map(MedicationLog::getIsTaken)
                .orElse(false);
        boolean newTaken = !currentlyTaken;

        // 5) 공유 쓰기 프리미티브로 로그 1건 upsert (해제 시에도 행 유지·is_taken=false)
        medicationLogCommandService.writeLog(medication, targetDate, mealTime, newTaken);

        // 6) 그 시간대 완료 상태 재계산 (홈카드 1단계와 동일 규칙을 공유하는 파생값)
        CompletedStatus completedStatus =
                recalculateMealTimeStatus(medication.getSenior().getId(), targetDate, mealTime);

        return new MedicationLogToggleResponse(medicationId, mealTime, newTaken, completedStatus);
    }

    // 홈카드와 동일한 완료 규칙으로 해당 시간대 완료 여부를 산출 → CompletedStatus로 변환
    private CompletedStatus recalculateMealTimeStatus(Long seniorId, LocalDate date, MealTime mealTime) {
        List<MedicationSchedule> slotSchedules = medicationScheduleRepository
                .findActiveSchedulesForHomeCards(seniorId, date).stream()
                .filter(schedule -> schedule.getMealTime() == mealTime)
                .toList();

        Set<TakenKey> takenKeys = completionCalculator.toTakenKeys(
                medicationLogRepository.findBySenior_IdAndTakenDate(seniorId, date));

        return completionCalculator.isMealTimeCompleted(slotSchedules, takenKeys)
                ? CompletedStatus.COMPLETED
                : CompletedStatus.INCOMPLETE;
    }
}