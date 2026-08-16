package com.piuda.callcare.global.config.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.google.firebase.messaging.FirebaseMessaging;

/**
 * 키 미설정 방어 로직 검증.
 * <p>
 * 서비스 계정 키 경로가 비어 있어도 컨텍스트가 정상 로드되어야 하고(앱 기동됨),
 * FirebaseMessaging 빈은 존재하지 않아야 한다(NullBean → ObjectProvider로 null).
 */
@ActiveProfiles("test")
@SpringBootTest(classes = FcmConfig.class, properties = "fcm.service-account-key-path=")
@DisplayName("FcmConfig 방어 로직 테스트")
class FcmConfigDefensiveTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("서비스 계정 키가 없어도 컨텍스트가 로드되고 FirebaseMessaging 빈은 없다")
    void contextLoads_withoutKey_andNoFirebaseMessagingBean() {
        FirebaseMessaging messaging = context.getBeanProvider(FirebaseMessaging.class).getIfAvailable();
        assertThat(messaging).isNull();
    }
}
