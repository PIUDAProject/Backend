package com.piuda.callcare.domain.senior.dto.request;

import com.piuda.callcare.domain.senior.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "부모님 정보 등록 요청 - 최초 이용 시 생활 시간도 함께 입력받는다")
public record SeniorCreateRequest(

        @Schema(description = "부모님 성함")
        @NotBlank String name,

        @Schema(description = "성별")
        @NotNull Gender gender,

        @Schema(description = "생년월일", example = "1955-03-12")
        @NotNull @Past LocalDate birthDate,

        @Schema(description = "부모님 전화번호 (문자 인증 대상)", example = "01012345678")
        @NotBlank
        @Pattern(regexp = "^01[016789]-?[0-9]{3,4}-?[0-9]{4}$", message = "올바른 휴대폰 번호 형식이 아닙니다.")
        String phoneNumber,

        @Schema(description = "전화번호로 발송된 문자 인증번호")
        @NotBlank String verificationCode,

        @Schema(description = "아침 식사 시간", example = "08:00")
        LocalTime breakfastTime,

        @Schema(description = "점심 식사 시간", example = "12:00")
        LocalTime lunchTime,

        @Schema(description = "저녁 식사 시간", example = "18:00")
        LocalTime dinnerTime
) {
}
