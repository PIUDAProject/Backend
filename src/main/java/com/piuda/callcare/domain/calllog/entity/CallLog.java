package com.piuda.callcare.domain.calllog.entity;

import com.piuda.callcare.domain.calllog.enums.CallStatus;
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
        name = "call_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_call_log_senior_meal_date",
                columnNames = {"senior_id", "meal_time", "call_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "call_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Senior senior;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_time", nullable = false)
    private MealTime mealTime;

    @Column(name = "called_at", nullable = false)
    private LocalDateTime calledAt;

    @Column(name = "call_date", nullable = false)
    private LocalDate callDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private CallStatus status;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "is_notified", nullable = false)
    private Boolean isNotified;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public CallLog(Senior senior, MealTime mealTime, LocalDateTime calledAt,
                   LocalDate callDate, CallStatus status, Integer retryCount, String messageId, Boolean isNotified) {
        this.senior = senior;
        this.mealTime = mealTime;
        this.calledAt = calledAt;
        this.callDate = callDate != null ? callDate : calledAt.toLocalDate();
        this.status = status;
        this.retryCount = retryCount;
        this.messageId = messageId;
        this.isNotified = isNotified;
        this.createdAt = LocalDateTime.now();
    }

    public void markSent(String messageId, LocalDateTime calledAt) {
        this.messageId = messageId;
        this.calledAt = calledAt;
    }

    public boolean markAnswered() {
        if (this.status.isTerminal()) {
            return false;
        }
        this.status = CallStatus.ANSWERED;
        return true;
    }

    public boolean markNoAnswer() {
        if (this.status.isTerminal()) {
            return false;
        }
        this.status = CallStatus.NO_ANSWER;
        return true;
    }

    public boolean markFailed() {
        if (this.status.isTerminal()) {
            return false;
        }
        this.status = CallStatus.FAILED;
        return true;
    }

    public void markAsNotified() {
        this.isNotified = true;
    }

    // 재발신 선점 — 발신 "전에" 상태를 확정한다. retryCount를 먼저 1로 올려 다음 스윕이
    // 같은 row를 다시 집지 않게 한다(중복 전화 방지). messageId는 2차 발신 결과로 다시 채워진다.
    // 1차 결과가 NO_ANSWER/FAILED(terminal)여도 PENDING으로 되돌려야 하므로 isTerminal 가드를 두지 않는다.
    public void markRetryPreempted(LocalDateTime calledAt) {
        this.status = CallStatus.PENDING;
        this.retryCount = 1;
        this.calledAt = calledAt;
        this.messageId = null;
    }

    // 재발신 직전 그 시간대가 이미 복약 완료라 발신을 생략한 경우.
    // retryCount를 올리지 않고 상태만 바꿔, 재발신 스윕에 매분 다시 걸리는 것을 막는다.
    public void markSkipped() {
        this.status = CallStatus.SKIPPED;
    }

    // 2차(마지막) 콜까지 진행한 row인지 — 웹훅 즉시 통보 경로의 게이트
    public boolean hasRetried() {
        return this.retryCount != null && this.retryCount >= 1;
    }

}
