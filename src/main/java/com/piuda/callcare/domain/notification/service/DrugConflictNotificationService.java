package com.piuda.callcare.domain.notification.service;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;
import com.piuda.callcare.domain.drugconflict.enums.ConflictSeverity;
import com.piuda.callcare.domain.drugconflict.event.DrugConflictDetectedEvent;
import com.piuda.callcare.domain.drugconflict.repository.DrugConflictRepository;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.notification.constant.NotificationDataKeys;
import com.piuda.callcare.domain.notification.enums.NotificationType;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.global.config.fcm.FcmRecipient;
import com.piuda.callcare.global.config.fcm.FcmSendRequest;
import com.piuda.callcare.global.config.fcm.FcmSendResult;
import com.piuda.callcare.global.config.fcm.FcmSendService;
import com.piuda.callcare.global.config.redis.IdempotencyKeyStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 약물 상호작용 알림 — 분석이 새 조합을 찾았거나 등급이 올라갔을 때 보호자에게 푸시한다.
 * <p>
 * <b>AFTER_COMMIT 리스너인 이유</b>: 충돌 행이 확정된 뒤에만 알려야 한다. 분석 트랜잭션이 롤백됐는데
 * 푸시가 나가면 회수할 방법이 없다. 이 메서드에 {@code @Transactional}이 없는 것도 의도로,
 * {@code FcmSendService}의 "트랜잭션 밖 호출" 계약을 그대로 지킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugConflictNotificationService {

    // 알림 보관 기간과 맞춘다 — 그보다 짧으면 사용자에게 아직 보이는 알림이 다시 발송될 수 있다.
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(30);

    // 푸시 본문이 길면 기기에서 잘린다. 이 길이를 넘으면 병원명을 빼서 다시 만든다.
    private static final int MAX_BODY_LENGTH = 100;

    private final DrugConflictRepository drugConflictRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final IdempotencyKeyStore idempotencyKeyStore;
    private final FcmSendService fcmSendService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyGuardian(DrugConflictDetectedEvent event) {
        try {
            doNotify(event);
        } catch (Exception e) {
            // 발송 실패가 이미 커밋된 분석 결과를 되돌리지 않도록 여기서 가둔다.
            log.error("약물 충돌 알림 발송 실패 - drugConflictId={}", event.drugConflictId(), e);
        }
    }

    private void doNotify(DrugConflictDetectedEvent event) {
        DrugConflict conflict = drugConflictRepository.findForNotificationById(event.drugConflictId())
                .orElse(null);
        if (conflict == null) {
            // 분석 커밋과 발송 사이에 약이 삭제·비활성화된 경우. 이미 유효하지 않은 경고라 보내지 않는다.
            log.info("약물 충돌 알림 대상 없음(약 삭제·비활성) - drugConflictId={}", event.drugConflictId());
            return;
        }

        // 멱등키는 충돌 행 id가 아니라 "약 쌍 + 등급"으로 잡는다.
        // 사용자가 겪는 사건은 "같은 두 약의 같은 위험"이고, 그 사건이 같으면 행 id가 달라져도 같은 알림이다.
        // 행 id가 바뀌는 경로가 실제로 있다 — 약 정보가 바뀌어 매칭이 사라지면 재분석의 stale 정리가 행을
        // 지우고, 정보가 돌아오면 새 id로 다시 저장된다. 행 id를 키로 쓰면 그때 같은 경고가 다시 나간다.
        // (약을 잠시 비활성화했다 되돌리는 경우는 여기 해당하지 않는다 — 비활성 약이 낀 행은 stale 정리
        //  대상이 아니라 id가 그대로 유지된다. deleteStaleConflicts 주석 참고.)
        // 등급을 함께 넣어 "같은 조합 1회"와 "등급이 오르면 다시"를 한 규칙으로 표현한다.
        String key = "notify:conflict:" + conflict.getMedication1().getId()
                + ":" + conflict.getMedication2().getId()
                + ":" + conflict.getSeverity().name();
        if (!idempotencyKeyStore.tryAcquire(key, IDEMPOTENCY_TTL)) {
            return;
        }

        Senior senior = conflict.getSenior();
        Medication first = conflict.getMedication1();
        Medication second = conflict.getMedication2();

        Map<Long, List<MealTime>> mealTimesByMedication = loadMealTimes(first, second);
        List<MealTime> firstMealTimes = mealTimesByMedication.getOrDefault(first.getId(), List.of());
        List<MealTime> secondMealTimes = mealTimesByMedication.getOrDefault(second.getId(), List.of());

        FcmSendResult result = fcmSendService.send(new FcmSendRequest(
                NotificationType.DRUG_CONFLICT,
                title(conflict.getSeverity()),
                buildBody(senior, first, firstMealTimes, second, secondMealTimes),
                new FcmRecipient(senior.getUser().getId(), senior.getId()),
                Map.of(
                        NotificationDataKeys.SENIOR_ID, String.valueOf(senior.getId()),
                        NotificationDataKeys.DRUG_CONFLICT_ID, String.valueOf(conflict.getId())
                )
        ));

        // 이 알림도 SMS 폴백이 없다 — 원인 구분 로깅까지가 이 단계의 책임이다.
        log.info("약물 충돌 알림 발송 - drugConflictId={}, seniorId={}, severity={}, escalated={}, status={}",
                conflict.getId(), senior.getId(), conflict.getSeverity(), event.escalated(), result.status());
    }

    // 시간대는 enum 선언 순서(아침→점심→저녁)로 정렬한다. DB 정렬은 STRING 알파벳순이라 쓸 수 없다.
    private Map<Long, List<MealTime>> loadMealTimes(Medication first, Medication second) {
        return medicationScheduleRepository.findAllByMedication_IdIn(List.of(first.getId(), second.getId())).stream()
                .collect(Collectors.groupingBy(
                        schedule -> schedule.getMedication().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                schedules -> schedules.stream()
                                        .map(MedicationSchedule::getMealTime)
                                        .distinct()
                                        .sorted(Comparator.naturalOrder())
                                        .toList())));
    }

    private String title(ConflictSeverity severity) {
        return severity == ConflictSeverity.CONTRAINDICATED ? "함께 복용하면 안 되는 약" : "약물 상호작용 주의";
    }

    // 두 약 모두의 별명·병원명·복용 시간대를 담는다. 길어서 잘릴 것 같으면 병원명을 뺀 축약본으로 바꾼다.
    private String buildBody(Senior senior, Medication first, List<MealTime> firstMealTimes,
                             Medication second, List<MealTime> secondMealTimes) {
        String body = composeBody(senior, first, firstMealTimes, second, secondMealTimes, true);
        if (body.length() <= MAX_BODY_LENGTH) {
            return body;
        }
        return composeBody(senior, first, firstMealTimes, second, secondMealTimes, false);
    }

    // 위험을 먼저 말하고 약 정보를 뒤에 붙인다 — 잠금화면 접힌 상태에서는 앞부분만 보이는데,
    // 약 이름·병원명이 앞을 차지하면 잘리는 지점까지 "위험하다"는 말이 한 번도 나오지 않는다.
    //
    // 시간대가 겹치면 그 시각을 짚어주고, 겹치지 않아도 조합 자체는 알린다 —
    // 성분이 몸에 남아 복용 시각이 달라도 상호작용할 수 있으므로 놓치지 않는 쪽을 택한다.
    private String composeBody(Senior senior, Medication first, List<MealTime> firstMealTimes,
                               Medication second, List<MealTime> secondMealTimes, boolean withHospital) {
        List<MealTime> overlapped = overlap(firstMealTimes, secondMealTimes);
        String lead = overlapped.isEmpty()
                ? "복용 시간은 다르지만 함께 복용 시 주의"
                : joinMealTimes(overlapped) + "에 함께 복용 위험";

        return lead + " — " + senior.getName() + "님의 "
                + describe(first, firstMealTimes, withHospital) + " + "
                + describe(second, secondMealTimes, withHospital);
    }

    // "혈압약(서울내과 · 아침·저녁)" 형태. 병원명·시간대는 없을 수 있어 있는 것만 괄호에 넣는다.
    private String describe(Medication medication, List<MealTime> mealTimes, boolean withHospital) {
        String name = displayName(medication);
        String hospital = withHospital ? medication.getHospitalName() : null;
        String times = mealTimes.isEmpty() ? null : joinMealTimes(mealTimes);

        if (hospital != null && !hospital.isBlank() && times != null) {
            return name + "(" + hospital + " · " + times + ")";
        }
        if (hospital != null && !hospital.isBlank()) {
            return name + "(" + hospital + ")";
        }
        if (times != null) {
            return name + "(" + times + ")";
        }
        return name;
    }

    private List<MealTime> overlap(List<MealTime> a, List<MealTime> b) {
        Set<MealTime> intersection = new LinkedHashSet<>(a);
        intersection.retainAll(b);
        return List.copyOf(intersection);
    }

    private String joinMealTimes(List<MealTime> mealTimes) {
        return mealTimes.stream().map(MealTime::getDescription).collect(Collectors.joining("·"));
    }

    // 별명이 있으면 보호자가 알아보기 쉽다. 비어 있으면 실제 약 이름으로 폴백한다.
    private String displayName(Medication medication) {
        String nickname = medication.getDrugNickname();
        return (nickname == null || nickname.isBlank()) ? medication.getDrugName() : nickname;
    }
}
