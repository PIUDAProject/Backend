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

    @Column(name = "is_read", nullable = false)
    private Boolean isRead; // 알림 읽음 여부

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Notification(User user, Senior senior, NotificationType type, String message) {
        this.user = user;
        this.senior = senior;
        this.type = type;
        this.message = message;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public void markAsRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }
}
