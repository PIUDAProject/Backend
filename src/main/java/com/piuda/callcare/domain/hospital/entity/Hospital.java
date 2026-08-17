package com.piuda.callcare.domain.hospital.entity;

import com.piuda.callcare.global.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "hospital")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Hospital extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hospital_id")
    private Long id;

    // 건강보험심사평가원이 1:1로 매칭해 제공하는 암호화된 요양기호
    @Column(name = "external_id", unique = true, length = 100)
    private String externalId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "active", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private boolean active = true;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Builder
    public Hospital(String externalId, String name, String address, String phoneNumber) {
        this.externalId = externalId;
        this.name = name;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.active = true;
        this.lastSyncedAt = LocalDateTime.now();
    }

    public void updateFrom(String name, String address, String phoneNumber, LocalDateTime syncedAt) {
        this.name = name;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.active = true;
        this.lastSyncedAt = syncedAt;
    }

    // 전체 동기화에서 더 이상 발견되지 않는 병원(휴·폐업 추정) 비활성화
    public void deactivate() {
        this.active = false;
    }
}
