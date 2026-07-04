package com.piuda.callcare.domain.senior.service.sms;

public interface SmsSender {
    void send(String from, String to, String text);
}
