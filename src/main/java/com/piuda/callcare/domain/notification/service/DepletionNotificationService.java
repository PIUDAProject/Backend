package com.piuda.callcare.domain.notification.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.piuda.callcare.domain.home.service.DepletionCalculator;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
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
 * 소진 임박 알림 — 잔여 0~3일인 약을 매일 1회 스캔해 보호자에게 푸시한다.
 * <p>
 * <b>약 단위로 개별 발송</b>한다. 한 어르신에게 임박한 약이 3개면 푸시도 3건이다 —
 * 알림을 탭했을 때 어느 약 리포트로 갈지 특정할 수 있어야 하므로 묶을 수 없다.
 * <p>
 * <b>클래스 레벨 {@code @Transactional}을 두지 않는다.</b> {@code FcmSendService}가 트랜잭션 밖 호출을
 * 계약으로 하고 있고(푸시는 회수 불가), 이 서비스는 조회만 하므로 트랜잭션이 필요 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepletionNotificationService {

    // 멱등키를 하루 넘게 유지해, 날짜 경계에서 배치가 두 번 도는 경우에도 같은 약이 두 번 나가지 않게 한다.
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(2);

    private final MedicationRepository medicationRepository;
    private final DepletionCalculator depletionCalculator;
    private final IdempotencyKeyStore idempotencyKeyStore;
    private final FcmSendService fcmSendService;

    /**
     * 오늘 기준 잔여 0~3일인 약 전체에 대해 보호자에게 소진 알림을 보낸다.
     * <p>
     * 한 건의 실패가 스캔 전체를 멈추지 않도록 건별로 예외를 가둔다.
     *
     * @param today 기준 날짜 — 테스트와 시간대 제어를 위해 호출자가 넘긴다
     */
    public void notifyDepletingMedications(LocalDate today) {
        LocalDate until = today.plusDays(DepletionCalculator.DEPLETION_THRESHOLD_DAYS);

        for (Medication medication : medicationRepository.findDepletingForNotification(today, until)) {
            try {
                notifyOne(medication, today);
            } catch (Exception e) {
                log.error("소진 알림 발송 실패 - medicationId={}, seniorId={}",
                        medication.getId(), medication.getSenior().getId(), e);
            }
        }
    }

    // 멱등키를 발송 "전에" 잡는다 — 푸시는 회수할 수 없으므로, 실패해 한 번 놓치는 쪽이
    // 같은 알림을 두 번 보내는 쪽보다 낫다(재시도는 후속 단계 범위).
    private void notifyOne(Medication medication, LocalDate today) {
        String key = "notify:depletion:med:" + medication.getId() + ":" + today;
        if (!idempotencyKeyStore.tryAcquire(key, IDEMPOTENCY_TTL)) {
            return;
        }

        Senior senior = medication.getSenior();
        long remainingDays = depletionCalculator.remainingDays(medication.getEndDate(), today);

        FcmSendResult result = fcmSendService.send(new FcmSendRequest(
                NotificationType.LOW_STOCK,
                "약 소진 임박",
                buildBody(senior, medication, remainingDays),
                new FcmRecipient(senior.getUser().getId(), senior.getId()),
                Map.of(
                        NotificationDataKeys.SENIOR_ID, String.valueOf(senior.getId()),
                        NotificationDataKeys.MEDICATION_ID, String.valueOf(medication.getId())
                )
        ));

        // 명세상 이 알림은 SMS 폴백이 없다 — 실패는 원인을 구분해 남기는 것까지가 이 단계의 책임이다.
        log.info("소진 알림 발송 - medicationId={}, seniorId={}, remainingDays={}, status={}",
                medication.getId(), senior.getId(), remainingDays, result.status());
    }

    // 잔여 0일은 "0일 남음"이 아니라 오늘이 마지막 복용일이라는 뜻이다(종료일 포함 컨벤션).
    // 약 설명 뒤에 쉼표를 두는 이유는 조사 때문이다 — 괄호로 끝나는 이름에 "이(가)"를 붙이면 어색하다.
    private String buildBody(Senior senior, Medication medication, long remainingDays) {
        String drugLabel = describeMedication(medication);
        String remaining = (remainingDays == 0) ? "오늘로 끝납니다" : remainingDays + "일 남았습니다";
        return senior.getName() + "님의 " + drugLabel + ", " + remaining + ". 처방을 준비해 주세요.";
    }

    // "혈압약(암로디핀 · 서울내과)" 형태.
    // 이 알림의 목적은 "약을 타러 가세요"라서 별명만으로는 부족하다 — 같은 별명을 여러 병원에서 받았으면
    // 어느 처방인지 구분되지 않고, 정작 병원·약국에서 필요한 것은 실제 약 이름이다.
    // 별명이 없거나 약 이름과 같으면 중복을 피해 약 이름만 앞에 낸다.
    private String describeMedication(Medication medication) {
        String nickname = medication.getDrugNickname();
        String drugName = medication.getDrugName();
        String hospitalName = medication.getHospitalName();

        boolean hasNickname = nickname != null && !nickname.isBlank() && !nickname.equals(drugName);

        List<String> details = new ArrayList<>();
        if (hasNickname && drugName != null && !drugName.isBlank()) {
            details.add(drugName);
        }
        if (hospitalName != null && !hospitalName.isBlank()) {
            details.add(hospitalName);
        }

        String head = hasNickname ? nickname : drugName;
        return details.isEmpty() ? head : head + "(" + String.join(" · ", details) + ")";
    }
}
