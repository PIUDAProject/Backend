package com.piuda.callcare.domain.calllog.client;

public interface VoiceCallSender {
    String call(String from, String to, String headerMessage, String bodyMessage);
}
