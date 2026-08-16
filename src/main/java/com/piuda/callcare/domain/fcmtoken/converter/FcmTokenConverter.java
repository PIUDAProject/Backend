package com.piuda.callcare.domain.fcmtoken.converter;

import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.fcmtoken.dto.response.FcmTokenResponse;
import com.piuda.callcare.domain.fcmtoken.entity.FcmToken;

@Component
public class FcmTokenConverter {

    // FcmToken → FcmTokenResponse (등록 응답용)
    public FcmTokenResponse toResponse(FcmToken fcmToken) {
        return new FcmTokenResponse(fcmToken.getId(), fcmToken.getIsActive());
    }
}
