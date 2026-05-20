package com.piuda.callcare.domain.medicationreport.entity;

import com.piuda.callcare.domain.senior.entity.Senior;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medication_report")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class MedicationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medication_report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Senior senior;

    @Column(name = "report_date")
    private LocalDate reportDate; // 라포트 생성 날짜

    @Column(name = "total_medications")
    private Integer totalMedications; // 총 복용해야 하는 약 개수

    @Column(name = "taken_count")
    private Integer takenCount; // 복약 완료 횟수

    @Column(name = "missed_count")
    private Integer missedCount; // 미복약 횟수

    @Column(name = "pdf_url")
    private String pdfUrl; // PDF URL

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MedicationReport(Senior senior, LocalDate reportDate, Integer totalMedications,
                             Integer takenCount, Integer missedCount) {
        this.senior = senior;
        this.reportDate = reportDate;
        this.totalMedications = totalMedications;
        this.takenCount = takenCount;
        this.missedCount = missedCount;
        this.createdAt = LocalDateTime.now();
    }

    public void updatePdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }
}
