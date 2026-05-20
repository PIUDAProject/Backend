package com.piuda.callcare.domain.senior.entity;

import com.piuda.callcare.domain.senior.enums.Gender;
import com.piuda.callcare.domain.user.entity.User;
import com.piuda.callcare.global.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "senior")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Senior extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "senior_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "breakfast_time")
    private LocalTime breakfastTime; // 디폴트 08:00

    @Column(name = "lunch_time")
    private LocalTime lunchTime; // 디폴트 12:00

    @Column(name = "dinner_time")
    private LocalTime dinnerTime; // 디폴트 18:00

    @Builder
    public Senior(User user, String name, Gender gender, LocalDate birthDate,
                  String phoneNumber, LocalTime breakfastTime, LocalTime lunchTime, LocalTime dinnerTime) {
        this.user = user;
        this.name = name;
        this.gender = gender;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
        this.breakfastTime = breakfastTime;
        this.lunchTime = lunchTime;
        this.dinnerTime = dinnerTime;
    }

    public void updateMealTime(LocalTime breakfastTime, LocalTime lunchTime, LocalTime dinnerTime) {
        this.breakfastTime = breakfastTime;
        this.lunchTime = lunchTime;
        this.dinnerTime = dinnerTime;
    }

    public void updateInfo(String name, Gender gender, LocalDate birthDate, String phoneNumber) {
        this.name = name;
        this.gender = gender;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
    }
}
