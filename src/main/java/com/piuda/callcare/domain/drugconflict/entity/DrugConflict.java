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
import java.util.Objects;

@Entity
@Table(name = "drug_conflict", uniqueConstraints = @UniqueConstraint(
        name = "uk_drug_conflict_senior_med1_med2",
        columnNames = {"senior_id", "medication_id_1", "medication_id_2"}
))
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

    // 재분석 upsert용: severity/description이 실제로 바뀐 경우에만 갱신한다.
    // 값이 동일하면 dirty checking으로도 UPDATE가 안 나가지만, 의도를 코드로 못박아 불필요한 갱신을 막는다.
    //
    // 반환값은 "등급이 올라갔는가"다 — 조합은 그대로여도 주의 → 금기로 올라간 것은 새 안전 정보라
    // 다시 알려야 하고, 등급이 내려가거나 설명만 바뀐 것은 알림 대상이 아니다.
    // 갱신 여부(changed)가 아니라 상승 여부를 돌려주는 이유는, 호출자가 필요한 판단이 그것뿐이기 때문이다.
    public boolean updateAnalysis(ConflictSeverity severity, String conflictDescription) {
        boolean escalated = severity.getPriority() > this.severity.getPriority();
        boolean changed = this.severity != severity
                || !Objects.equals(this.conflictDescription, conflictDescription);
        if (changed) {
            this.severity = severity;
            this.conflictDescription = conflictDescription;
        }
        return escalated;
    }
}
