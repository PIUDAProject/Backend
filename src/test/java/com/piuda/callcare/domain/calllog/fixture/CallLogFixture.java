package com.piuda.callcare.domain.calllog.fixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.test.util.ReflectionTestUtils;

import com.piuda.callcare.domain.calllog.entity.CallLog;
import com.piuda.callcare.domain.calllog.enums.CallStatus;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.user.entity.User;
import com.piuda.callcare.domain.user.enums.Provider;

public class CallLogFixture {

    public static final Long USER_ID = 1L;
    public static final Long SENIOR_ID = 10L;
    public static final Long CALL_LOG_ID = 100L;

    public static final String SENIOR_NAME = "김복순";
    public static final String SENIOR_PHONE = "01011112222";
    public static final String GUARDIAN_PHONE = "01033334444";
    public static final String SENDER_NUMBER = "0212345678";

    public static final MealTime MEAL_TIME = MealTime.DINNER;
    public static final LocalDate CALL_DATE = LocalDate.of(2026, 8, 19);
    // 저녁 18:00 + 30분 = 최초 발신 시각
    public static final LocalDateTime FIRST_CALLED_AT = LocalDateTime.of(2026, 8, 19, 18, 30);
    // 최초 발신 + 10분 = 재발신 스윕이 도는 시각
    public static final LocalDateTime RETRY_NOW = FIRST_CALLED_AT.plusMinutes(10);

    public static User createGuardian() {
        User user = User.builder()
                .email("guardian@callcare.com")
                .phoneNumber(GUARDIAN_PHONE)
                .provider(Provider.KAKAO)
                .providerId("kakao-1")
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    public static Senior createSenior() {
        Senior senior = Senior.builder()
                .user(createGuardian())
                .name(SENIOR_NAME)
                .phoneNumber(SENIOR_PHONE)
                .breakfastTime(LocalTime.of(8, 0))
                .lunchTime(LocalTime.of(12, 0))
                .dinnerTime(LocalTime.of(18, 0))
                .build();
        ReflectionTestUtils.setField(senior, "id", SENIOR_ID);
        return senior;
    }

    // 1차 콜만 나간 상태 (retryCount = 0) — 재발신 스윕의 대상
    public static CallLog createFirstCallLog(CallStatus status) {
        return createCallLog(status, 0);
    }

    // 재발신까지 나간 상태 (retryCount = 1) — 통보 스윕의 대상
    public static CallLog createRetriedCallLog(CallStatus status) {
        return createCallLog(status, 1);
    }

    private static CallLog createCallLog(CallStatus status, int retryCount) {
        CallLog callLog = CallLog.builder()
                .senior(createSenior())
                .mealTime(MEAL_TIME)
                .calledAt(FIRST_CALLED_AT)
                .callDate(CALL_DATE)
                .status(status)
                .retryCount(retryCount)
                .messageId("message-1")
                .isNotified(false)
                .build();
        ReflectionTestUtils.setField(callLog, "id", CALL_LOG_ID);
        return callLog;
    }

    public static MedicationSchedule createSchedule(Senior senior, String drugName) {
        Medication medication = Medication.builder()
                .senior(senior)
                .drugName(drugName)
                .startDate(CALL_DATE.minusDays(1))
                .endDate(CALL_DATE.plusDays(5))
                .isActive(true)
                .build();
        return MedicationSchedule.builder()
                .medication(medication)
                .mealTime(MEAL_TIME)
                .build();
    }
}
