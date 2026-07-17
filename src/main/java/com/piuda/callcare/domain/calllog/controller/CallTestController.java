package com.piuda.callcare.domain.calllog.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.piuda.callcare.domain.calllog.service.CallReminderCommandService;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Call Test", description = "로컬 전화 알림 테스트 API")
@Profile("local")
@RestController
@RequestMapping("/api/test/calls")
@RequiredArgsConstructor
public class CallTestController {

    private final CallReminderCommandService callReminderCommandService;

    @Operation(
            summary = "전화 알림 즉시 발신 테스트",
            description = "local 프로필에서만 활성화됩니다. 스케줄러와 동일한 발신/CallLog 저장 로직을 즉시 실행하고 messageId를 반환합니다."
    )
    @PostMapping("/trigger")
    public ResponseEntity<ApiResponse<CallTriggerTestResponse>> trigger(
            @RequestParam Long seniorId,
            @RequestParam MealTime mealTime
    ) {
        String messageId = callReminderCommandService.triggerMedicationCallForTest(seniorId, mealTime);
        return ResponseUtils.ok(new CallTriggerTestResponse(messageId));
    }

    public record CallTriggerTestResponse(String messageId) {
    }
}
