package com.piuda.callcare.domain.notification.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.piuda.callcare.domain.notification.dto.response.NotificationResponse;
import com.piuda.callcare.domain.notification.service.command.NotificationCommandService;
import com.piuda.callcare.domain.notification.service.query.NotificationQueryService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 알림 센터 API.
 * <p>
 * 이 컨트롤러는 <b>SecurityConfig의 permitAll에 넣지 않는다.</b> 조회 단위인 userId가 곧 인증 주체라,
 * 파라미터로 받으면 값만 바꿔 남의 알림을 읽고 읽음 처리할 수 있다 — seniorId 기반 조회 API들이
 * 소유권 검증을 TODO로 미뤄 둔 것과 성격이 다르다.
 */
@Tag(name = "Notification", description = "알림 센터 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationCommandService notificationCommandService;

    @Operation(summary = "알림 목록 조회",
            description = "로그인한 보호자가 받은 알림을 최신순으로 반환합니다. 돌보는 어르신이 여러 명이면 섞여서 내려갑니다. "
                    + "유형은 전화 미수신·약 부족·약물 충돌 3종이며, 보관 기간(30일)이 지난 알림은 자동 삭제되어 나오지 않습니다. "
                    + "알림이 없으면 빈 배열을 반환합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseUtils.ok(notificationQueryService.getNotifications(userId));
    }

    @Operation(summary = "알림 읽음 처리",
            description = "알림 1건을 읽음으로 표시합니다. 여러 번 호출해도 처음 읽은 시각이 유지됩니다. "
                    + "본인 알림이 아니면 404를 반환합니다.")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        return ResponseUtils.ok(notificationCommandService.markAsRead(userId, notificationId));
    }
}
