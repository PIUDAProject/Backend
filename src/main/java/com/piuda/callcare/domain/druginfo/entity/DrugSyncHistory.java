package com.piuda.callcare.domain.druginfo.entity;

import com.piuda.callcare.domain.druginfo.enums.DrugSyncStatus;
import com.piuda.callcare.global.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 약품 ES 재색인 배치 이력 (MySQL drug_info → Elasticsearch alias 재색인)
@Entity
@Table(name = "drug_sync_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class DrugSyncHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "drug_sync_history_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DrugSyncStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "indexed_count", nullable = false)
    private int indexedCount;

    // 재색인이 색인한 물리 인덱스명 (예: drug_info-20260904153012)
    @Column(name = "target_index")
    private String targetIndex;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    public static DrugSyncHistory start() {
        DrugSyncHistory history = new DrugSyncHistory();
        history.status = DrugSyncStatus.RUNNING;
        history.startedAt = LocalDateTime.now();
        history.indexedCount = 0;
        return history;
    }

    public void success(int indexedCount, String targetIndex) {
        this.status = DrugSyncStatus.SUCCESS;
        this.finishedAt = LocalDateTime.now();
        this.indexedCount = indexedCount;
        this.targetIndex = targetIndex;
    }

    public void fail(String errorMessage) {
        this.status = DrugSyncStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }
}
