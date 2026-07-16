package com.piuda.callcare.domain.calllog.entity;

import com.piuda.callcare.domain.calllog.enums.CallStatus;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.senior.entity.Senior;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "call_log")
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
                   CallStatus status, Integer retryCount, String messageId, Boolean isNotified) {
        this.senior = senior;
        this.mealTime = mealTime;
        this.calledAt = calledAt;
        this.status = status;
        this.retryCount = retryCount;
        this.messageId = messageId;
        this.isNotified = isNotified;
        this.createdAt = LocalDateTime.now();
    }

    public void updateStatus(CallStatus status) {
        this.status = status;
    }

    public void markAnswered() {
        this.status = CallStatus.ANSWERED;
    }

    public void markNoAnswer() {
        this.status = CallStatus.NO_ANSWER;
    }

    public void markFailed() {
        this.status = CallStatus.FAILED;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public void markAsNotified() {
        this.isNotified = true;
    }

    // 재시도 발신 시 호출: 새 messageId/발신 시각으로 갱신하고 결과 대기 상태로 되돌린다
    public void markRetried(String messageId, LocalDateTime calledAt) {
        this.messageId = messageId;
        this.calledAt = calledAt;
        this.status = CallStatus.PENDING;
        this.retryCount = this.retryCount + 1;
    }

    public boolean isAnswered() {
        return this.status == CallStatus.ANSWERED;
    }
}
