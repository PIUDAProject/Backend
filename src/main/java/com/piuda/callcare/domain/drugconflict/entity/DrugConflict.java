package com.piuda.callcare.domain.drugconflict.entity;

import com.piuda.callcare.domain.drugconflict.enums.ConflictSeverity;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.senior.entity.Senior;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "drug_conflict")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class DrugConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "drug_conflict_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Senior senior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id_1", nullable = false)
    private Medication medication1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id_2", nullable = false)
    private Medication medication2;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private ConflictSeverity severity; // 금기/주의 등급 (intrc 경고 문장 말투로 판정)

    @Column(name = "conflict_description", columnDefinition = "TEXT")
    private String conflictDescription;

    @Column(name = "is_resolved", nullable = false)
    private Boolean isResolved;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public DrugConflict(Senior senior, Medication medication1, Medication medication2,
                        ConflictSeverity severity, String conflictDescription) {
        this.senior = senior;
        this.medication1 = medication1;
        this.medication2 = medication2;
        this.severity = severity;
        this.conflictDescription = conflictDescription;
        this.isResolved = false;
        this.createdAt = LocalDateTime.now();
    }

    public void resolve() {
        this.isResolved = true;
    }
}
