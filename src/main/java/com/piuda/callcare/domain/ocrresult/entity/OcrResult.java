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

    @Column(name = "is_processed", nullable = false)
    private Boolean isProcessed; // OCR 결과가 처리되었는지 여부 (예: 약 정보 추출 완료 여부)

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public OcrResult(Senior senior, String imageUrl, OcrType ocrType, String rawText) {
        this.senior = senior;
        this.imageUrl = imageUrl;
        this.ocrType = ocrType;
        this.rawText = rawText;
        this.isProcessed = false;
        this.createdAt = LocalDateTime.now();
    }

    public void markAsProcessed() {
        this.isProcessed = true;
    }
}
