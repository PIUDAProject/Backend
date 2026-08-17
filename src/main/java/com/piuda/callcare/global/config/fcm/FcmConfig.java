package com.piuda.callcare.global.config.fcm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import lombok.extern.slf4j.Slf4j;

/**
 * Firebase Admin SDK 초기화 설정.
 * <p>
 * 서비스 계정 키 경로({@code fcm.service-account-key-path})만 설정값으로 받고, 키 파일 자체는 커밋하지 않는다.
 * 키가 비어 있거나 파일이 없으면 초기화를 건너뛰고 경고만 남긴다 —
 * 즉 <b>키 없이도 앱은 정상 기동</b>되며, 발송 시점에만 "FCM 미설정"으로 처리된다({@link FcmSendService}).
 * <p>
 * 키가 없을 때 이 빈은 {@code null}(NullBean)이 되므로, 소비자는 반드시
 * {@code ObjectProvider<FirebaseMessaging>}로 접근해야 한다.
 */
@Slf4j
@Configuration
public class FcmConfig {

    @Value("${fcm.service-account-key-path:}")
    private String serviceAccountKeyPath;

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        if (!StringUtils.hasText(serviceAccountKeyPath)) {
            log.warn("[FCM] fcm.service-account-key-path 가 비어 있어 초기화를 건너뜁니다. 푸시 발송은 비활성화됩니다.");
            return null;
        }

        Path keyPath = Path.of(serviceAccountKeyPath);
        if (!Files.exists(keyPath)) {
            log.warn("[FCM] 서비스 계정 키 파일이 없어 초기화를 건너뜁니다 (path={}). 푸시 발송은 비활성화됩니다.",
                serviceAccountKeyPath);
            return null;
        }

        try (InputStream serviceAccount = Files.newInputStream(keyPath)) {
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build())
                : FirebaseApp.getInstance();
            log.info("[FCM] Firebase 초기화 성공 (path={})", serviceAccountKeyPath);
            return FirebaseMessaging.getInstance(app);
        } catch (IOException e) {
            log.error("[FCM] Firebase 초기화 실패 (path={}): {}", serviceAccountKeyPath, e.getMessage(), e);
            return null;
        }
    }
}
