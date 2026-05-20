package com.piuda.callcare.domain.medication.entity;

import com.piuda.callcare.domain.medication.enums.MealTime;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "medication_schedule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class MedicationSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medication_schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_time", nullable = false)
    private MealTime mealTime; // 식전, 식후, 식간 등

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MedicationSchedule(Medication medication, MealTime mealTime) {
        this.medication = medication;
        this.mealTime = mealTime;
        this.createdAt = LocalDateTime.now();
    }
}
