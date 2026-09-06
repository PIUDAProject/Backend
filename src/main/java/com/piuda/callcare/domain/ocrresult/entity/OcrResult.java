package com.piuda.callcare.domain.ocrresult.entity;

import com.piuda.callcare.domain.ocrresult.enums.OcrType;
import com.piuda.callcare.domain.senior.entity.Senior;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ocr_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OcrResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ocr_result_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Senior senior;

    @Column(name = "image_url")
    private String imageUrl; // OCR 처리된 이미지 URL

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_type")
    private OcrType ocrType; // OCR 유형 (예: 처방전, 약 봉투, 약곽)

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText; // OCR로 추출된 원본 텍스트

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse; // Naver OCR 응답 원문 JSON (좌표 포함, 실패 재현·회귀 테스트용)

    @Column(name = "parsed_drug_name")
    private String parsedDrugName; // OCR 결과에서 추출된 약 이름

    @Column(name = "parsed_dosage_per_time")
    private String parsedDosagePerTime; // OCR 결과에서 추출된 1회 복용량 (예: "1정", "5ml")

    @Column(name = "parsed_times_per_day")
    private Integer parsedTimesPerDay; // OCR 결과에서 추출된 1일 복용 횟수 (예: 3회)

    @Column(name = "parsed_total_days")
    private Integer parsedTotalDays; // OCR 결과에서 추출된 총 복용 일수 (예: 7일)

    @Column(name = "is_processed", nullable = false)
    private Boolean isProcessed; // OCR 결과가 처리되었는지 여부 (예: 약 정보 추출 완료 여부)

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public OcrResult(Senior senior, String imageUrl, OcrType ocrType, String rawText, String rawResponse) {
        this.senior = senior;
        this.imageUrl = imageUrl;
        this.ocrType = ocrType;
        this.rawText = rawText;
        this.rawResponse = rawResponse;
        this.isProcessed = false;
        this.createdAt = LocalDateTime.now();
    }

    public void markAsProcessed() {
        this.isProcessed = true;
    }

    public void saveParsedData(String drugName, String dosagePerTime, Integer timesPerDay, Integer totalDays) {
        this.parsedDrugName = drugName;
        this.parsedDosagePerTime = dosagePerTime;
        this.parsedTimesPerDay = timesPerDay;
        this.parsedTotalDays = totalDays;
    }
}
