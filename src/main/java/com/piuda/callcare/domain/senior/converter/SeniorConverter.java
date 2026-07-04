package com.piuda.callcare.domain.senior.converter;

import com.piuda.callcare.domain.senior.dto.request.SeniorCreateRequest;
import com.piuda.callcare.domain.senior.dto.response.SeniorResponse;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.user.entity.User;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
public class SeniorConverter {

    private static final LocalTime DEFAULT_BREAKFAST_TIME = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_LUNCH_TIME = LocalTime.of(12, 0);
    private static final LocalTime DEFAULT_DINNER_TIME = LocalTime.of(18, 0);

    public Senior toEntity(User user, SeniorCreateRequest request) {
        return Senior.builder()
                .user(user)
                .name(request.name())
                .gender(request.gender())
                .birthDate(request.birthDate())
                .phoneNumber(request.phoneNumber())
                .breakfastTime(defaultIfNull(request.breakfastTime(), DEFAULT_BREAKFAST_TIME))
                .lunchTime(defaultIfNull(request.lunchTime(), DEFAULT_LUNCH_TIME))
                .dinnerTime(defaultIfNull(request.dinnerTime(), DEFAULT_DINNER_TIME))
                .build();
    }

    public SeniorResponse toResponse(Senior senior) {
        return new SeniorResponse(
                senior.getId(),
                senior.getName(),
                senior.getGender(),
                senior.getBirthDate(),
                senior.getPhoneNumber(),
                senior.getBreakfastTime(),
                senior.getLunchTime(),
                senior.getDinnerTime()
        );
    }

    private LocalTime defaultIfNull(LocalTime value, LocalTime defaultValue) {
        return value != null ? value : defaultValue;
    }
}
