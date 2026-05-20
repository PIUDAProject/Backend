package com.piuda.callcare.domain.fcmtoken.repository;

import com.piuda.callcare.domain.fcmtoken.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {
}
