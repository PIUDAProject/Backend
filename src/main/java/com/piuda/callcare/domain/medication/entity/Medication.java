package com.piuda.callcare.domain.medication.entity;

import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.global.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "medication")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Medication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medication_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Senior senior;

    @Column(name = "hospital_name")
    private String hospitalName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drug_info_id")
    private DrugInfo drugInfo;

    @Column(name = "drug_name", nullable = false)
    private String drugName;

    @Column(name = "drug_nickname")
    private String drugNickname; // 약 별명 (소화제, 진통제 등)

    @Column(name = "drug_type")
    private String drugType; // 약 종류

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "dosage_per_time")
    private String dosagePerTime; // 1회 복용량

    @Column(name = "times_per_day")
    private Integer timesPerDay; // 1일 복용 횟수

    @Column(name = "total_days")
    private Integer totalDays; // 총 복용 일수

    @Column(name = "start_date")
    private LocalDate startDate; // 복용 시작 날짜

    @Column(name = "end_date")
    private LocalDate endDate; // 복용 종료 날짜

    @Column(name = "prescription_date")
    private LocalDate prescriptionDate; // 처방 날짜

    @Column(name = "usage_storage_info", columnDefinition = "TEXT")
    private String usageStorageInfo; // DrugInfo 연결 시 자동 생성 (복용법 + 보관법), OCR 등록 시 null

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo; // 사용자 자유 입력, 항상 빈값으로 시작

    @Column(name = "is_active", nullable = false)
    private Boolean isActive; // 현재 복용 중인지 여부 (복용 종료 시 false로 변경)

    @Column(name = "ocr_result_id")
    private Long ocrResultId; // OCR 경로로 등록된 경우 연결 ID (직접 등록이면 null)

    @Builder
    public Medication(Senior senior, String hospitalName, DrugInfo drugInfo,
        String drugName, String drugNickname, String drugType,
        String imageUrl, String dosagePerTime, Integer timesPerDay,
        Integer totalDays, LocalDate startDate, LocalDate endDate,
        LocalDate prescriptionDate, String usageStorageInfo, String memo,
        Boolean isActive, Long ocrResultId) {
        this.senior = senior;
        this.hospitalName = hospitalName;
        this.drugInfo = drugInfo;
        this.drugName = drugInfo != null ? drugInfo.getItemName() : drugName;
        this.drugNickname = drugNickname;
        this.drugType = drugInfo != null ? drugInfo.getPrductType() : drugType;
        this.imageUrl = drugInfo != null ? drugInfo.getItemImage() : imageUrl;
        this.dosagePerTime = dosagePerTime;
        this.timesPerDay = timesPerDay;
        this.totalDays = totalDays;
        this.startDate = startDate;
        this.endDate = endDate;
        this.prescriptionDate = prescriptionDate;
        this.usageStorageInfo = usageStorageInfo;
        this.memo = memo;
        this.isActive = isActive;
        this.ocrResultId = ocrResultId;
    }

    public void deactivate() {
        this.isActive = false;
    }
}