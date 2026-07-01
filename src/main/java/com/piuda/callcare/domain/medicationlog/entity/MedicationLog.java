package com.piuda.callcare.domain.medicationlog.entity;

import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.senior.entity.Senior;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "medication_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_medication_log_med_date_meal",
                columnNames = {"medication_id", "taken_date", "meal_time"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class MedicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medication_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Senior senior;

    @Column(name = "taken_date", nullable = false)
    private LocalDate takenDate; // 복용 날짜

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_time", nullable = false)
    private MealTime mealTime; // 식전/식후 구분

    @Column(name = "is_taken", nullable = false)
    private Boolean isTaken; // 복용 여부

    @Column(name = "taken_at")
    private LocalDateTime takenAt; // 복약 완료 시각

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MedicationLog(Medication medication, Senior senior, LocalDate takenDate,
                         MealTime mealTime, Boolean isTaken, LocalDateTime takenAt) {
        this.medication = medication;
        this.senior = senior;
        this.takenDate = takenDate;
        this.mealTime = mealTime;
        this.isTaken = isTaken;
        this.takenAt = takenAt;
        this.createdAt = LocalDateTime.now();
    }

    public void markAsTaken() {
        this.isTaken = true;
        this.takenAt = LocalDateTime.now();
    }

    // 복용 여부를 명시적으로 갱신 (체크 시 taken_at=now, 해제 시 null). 해제도 행을 유지한다.
    public void updateIsTaken(boolean isTaken) {
        this.isTaken = isTaken;
        this.takenAt = isTaken ? LocalDateTime.now() : null;
    }
}
