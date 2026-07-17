package com.piuda.callcare.domain.calllog.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.piuda.callcare.domain.calllog.dto.request.CallResultWebhookRequest;
import com.piuda.callcare.domain.calllog.service.CallReminderCommandService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/calllogs/webhook")
@RequiredArgsConstructor
public class CallResultWebhookController {

    private final CallReminderCommandService callReminderCommandService;

    @PostMapping("/results")
    public ResponseEntity<Void> receiveCallResult(@RequestBody CallResultWebhookRequest request) {
        callReminderCommandService.applyCallResult(request.resolvedMessageId(), request.resolvedStatus());
        return ResponseEntity.ok().build();
    }
}
