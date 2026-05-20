package com.piuda.callcare.domain.medication.entity;

import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import com.piuda.callcare.domain.hospital.entity.Hospital;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

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

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo; // 복용 방법 + 보관법 자동 입력

    @Column(name = "is_active", nullable = false)
    private Boolean isActive; // 현재 복용 중인지 여부 (복용 종료 시 false로 변경)

    @Builder
    public Medication(Senior senior, Hospital hospital, DrugInfo drugInfo,
        String drugName, String drugNickname, String drugType,
        String imageUrl, String dosagePerTime, Integer timesPerDay,
        Integer totalDays, LocalDate startDate, LocalDate endDate,
        LocalDate prescriptionDate, String memo, Boolean isActive) {
        this.senior = senior;
        this.hospital = hospital;
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
        this.memo = memo;
        this.isActive = isActive;
    }

    public void deactivate() {
        this.isActive = false;
    }
}