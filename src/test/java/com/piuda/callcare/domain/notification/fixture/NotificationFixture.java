package com.piuda.callcare.domain.notification.fixture;

import java.time.LocalDate;

import org.springframework.test.util.ReflectionTestUtils;

import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;
import com.piuda.callcare.domain.drugconflict.enums.ConflictSeverity;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.user.entity.User;
import com.piuda.callcare.domain.user.enums.Provider;

public class NotificationFixture {

    public static final Long USER_ID = 1L;
    public static final Long SENIOR_ID = 10L;
    public static final Long MEDICATION_1_ID = 100L;
    public static final Long MEDICATION_2_ID = 200L;
    public static final Long CONFLICT_ID = 500L;

    public static final String SENIOR_NAME = "김복순";
    public static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    public static User createGuardian() {
        User user = User.builder()
                .email("guardian@callcare.com")
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
                .build();
        ReflectionTestUtils.setField(senior, "id", SENIOR_ID);
        return senior;
    }

    // 소진 알림 대상 약. remainingDays만큼 남도록 endDate를 맞춘다.
    public static Medication createMedication(Long id, Senior senior, String drugName, String nickname,
                                              String hospitalName, long remainingDays) {
        Medication medication = Medication.builder()
                .senior(senior)
                .drugName(drugName)
                .drugNickname(nickname)
                .hospitalName(hospitalName)
                .startDate(TODAY.minusDays(10))
                .endDate(TODAY.plusDays(remainingDays))
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(medication, "id", id);
        return medication;
    }

    public static MedicationSchedule createSchedule(Medication medication, MealTime mealTime) {
        return MedicationSchedule.builder()
                .medication(medication)
                .mealTime(mealTime)
                .build();
    }

    public static DrugConflict createConflict(Senior senior, Medication first, Medication second,
                                              ConflictSeverity severity) {
        DrugConflict conflict = DrugConflict.builder()
                .senior(senior)
                .medication1(first)
                .medication2(second)
                .severity(severity)
                .conflictDescription("병용 시 주의가 필요합니다.")
                .build();
        ReflectionTestUtils.setField(conflict, "id", CONFLICT_ID);
        return conflict;
    }
}
