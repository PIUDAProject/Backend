package com.piuda.callcare.domain.calllog.controller;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class CallResultWebhookController {

    private final CallReminderCommandService callReminderCommandService;

    @PostMapping("/results")
    public ResponseEntity<Void> receiveCallResults(@RequestBody List<CallResultWebhookRequest> requests) {
        log.info("SOLAPI 웹훅 수신 - {}건", requests.size());
        requests.forEach(request -> callReminderCommandService.applyCallResult(
                request.resolvedMessageId(),
                request.resolvedStatus()
        ));
        return ResponseEntity.ok().build();
    }
}
