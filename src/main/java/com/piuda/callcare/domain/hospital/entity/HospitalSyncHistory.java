package com.piuda.callcare.domain.hospital.entity;

import com.piuda.callcare.domain.hospital.enums.HospitalSyncStatus;
import com.piuda.callcare.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 공공데이터(심평원 병원정보서비스) 배치 수집 이력
@Entity
@Table(name = "hospital_sync_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class HospitalSyncHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hospital_sync_history_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HospitalSyncStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "requested_count")
    private int requestedCount;

    @Column(name = "inserted_count")
    private int insertedCount;

    @Column(name = "updated_count")
    private int updatedCount;

    @Column(name = "failed_count")
    private int failedCount;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    // maxPages 없이(전체) 실행됐는지 여부. 부분 테스트 실행이 stale 병원 판단의 비교 기준으로 섞이지 않도록 구분.
    // 기존 행이 있는 상태에서 컬럼을 추가해도 ALTER TABLE이 실패하지 않도록 DB 레벨 기본값을 명시한다.
    @Column(name = "full_sync", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean fullSync;

    @Column(name = "last_completed_page", nullable = false, columnDefinition = "INT DEFAULT 0")
    private int lastCompletedPage;

    @Column(name = "last_progress_at")
    private LocalDateTime lastProgressAt;

    public static HospitalSyncHistory start(boolean fullSync) {
        HospitalSyncHistory history = new HospitalSyncHistory();
        history.status = HospitalSyncStatus.RUNNING;
        history.startedAt = LocalDateTime.now();
        history.fullSync = fullSync;
        return history;
    }

    public void updateProgress(int lastCompletedPage, int requestedCount, int insertedCount,
                               int updatedCount, int failedCount) {
        this.lastCompletedPage = lastCompletedPage;
        this.lastProgressAt = LocalDateTime.now();
        this.requestedCount = requestedCount;
        this.insertedCount = insertedCount;
        this.updatedCount = updatedCount;
        this.failedCount = failedCount;
    }

    public void complete(HospitalSyncStatus status, int requestedCount, int insertedCount,
                          int updatedCount, int failedCount, String errorMessage) {
        this.status = status;
        this.finishedAt = LocalDateTime.now();
        this.requestedCount = requestedCount;
        this.insertedCount = insertedCount;
        this.updatedCount = updatedCount;
        this.failedCount = failedCount;
        this.errorMessage = errorMessage;
    }

    public void failManually() {
        complete(HospitalSyncStatus.FAILED, requestedCount, insertedCount, updatedCount, failedCount,
                "관리자가 중단된 RUNNING 이력을 수동으로 실패 처리했습니다.");
    }
}
