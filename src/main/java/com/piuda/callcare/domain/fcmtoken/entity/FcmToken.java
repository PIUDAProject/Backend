package com.piuda.callcare.domain.fcmtoken.entity;

import com.piuda.callcare.domain.fcmtoken.enums.DeviceType;
import com.piuda.callcare.domain.user.entity.User;
import com.piuda.callcare.global.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fcm_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class FcmToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fcm_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 같은 토큰이 여러 행으로 쌓이면 같은 기기에 중복 발송되므로 unique. FCM 토큰은 기본 255자를 넘길 수 있어 512
    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type")
    private DeviceType deviceType;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Builder
    public FcmToken(User user, String token, DeviceType deviceType) {
        this.user = user;
        this.token = token;
        this.deviceType = deviceType;
        this.isActive = true;
    }

    // 이미 등록된 토큰의 재등록 — 기기 계정 전환 시 소유자가 바뀌므로 user까지 갱신하고 다시 활성화한다
    public void renew(User user, DeviceType deviceType) {
        this.user = user;
        this.deviceType = deviceType;
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
