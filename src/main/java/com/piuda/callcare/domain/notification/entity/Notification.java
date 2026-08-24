package com.piuda.callcare.domain.notification.entity;

import com.piuda.callcare.domain.notification.enums.NotificationType;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.user.entity.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Senior senior;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type; // 일림 유형(전화 미수신, 약 부족, 약물 충돌 등)

    @Column(name = "message", columnDefinition = "TEXT")
    private String message; // 알림 내용

    // 이 알림이 가리키는 약 — 소진(LOW_STOCK) 알림의 딥링크 조회키다. 다른 유형은 null이다
    // (충돌은 리포트 화면, 전화 미수신은 전용 상세 화면이 없어 어르신 홈으로 간다).
    // FK가 아니라 식별자만 남긴다(Medication.ocrResultId와 같은 방식): 알림은 30일 뒤 사라지는 이력이라
    // 약의 생명주기에 묶을 이유가 없고, 가리키던 약이 삭제돼도 이력은 원본 그대로 남아야 한다.
    @Column(name = "medication_id")
    private Long medicationId;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead; // 알림 읽음 여부

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Notification(User user, Senior senior, NotificationType type, String message, Long medicationId) {
        this.user = user;
        this.senior = senior;
        this.type = type;
        this.message = message;
        this.medicationId = medicationId;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    // 이미 읽은 알림의 읽은 시각은 덮어쓰지 않는다 — 같은 알림을 여러 번 열어도
    // "언제 처음 확인했는가"가 남아야 하고, 읽음 처리는 여러 번 불려도 안전해야 한다.
    public void markAsRead() {
        if (Boolean.TRUE.equals(this.isRead)) {
            return;
        }
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }
}
