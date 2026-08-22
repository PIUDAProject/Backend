package com.piuda.callcare;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
@EnableFeignClients
public class CallcareApplication {

	// 스케줄러 cron이 전부 Asia/Seoul로 고정돼 있는데 JVM 기본 시간대가 UTC면
	// LocalDate.now()·LocalTime.now()가 하루/9시간 어긋난다 — 기준을 한 곳에서 맞춘다.
	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(CallcareApplication.class, args);
	}

}
