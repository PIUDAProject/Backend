package com.piuda.callcare.domain.calllog.controller;

import com.piuda.callcare.domain.calllog.service.CallReminderCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CallResultWebhookControllerTest {

    @Mock
    private CallReminderCommandService callReminderCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CallResultWebhookController(callReminderCommandService))
                .build();
    }

    @Test
    void receivesArrayPayloadAndAppliesEveryResult() throws Exception {
        String body = """
                [
                  {"messageId":"message-1","status":"ANSWERED"},
                  {"payload":{"messageId":"message-2","status":"FAILED"}}
                ]
                """;

        mockMvc.perform(post("/api/calllogs/webhook/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(callReminderCommandService).applyCallResult("message-1", "ANSWERED");
        verify(callReminderCommandService).applyCallResult("message-2", "FAILED");
    }
}
