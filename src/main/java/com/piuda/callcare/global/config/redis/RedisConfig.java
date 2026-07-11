package com.piuda.callcare.global.config.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 인프라 공통 설정.
 * <p>
 * 커넥션 팩토리와 {@code StringRedisTemplate}은 Spring Boot 자동 구성에 맡긴다.
 * 직접 빈을 선언하면 {@code RedisAutoConfiguration}({@code @ConditionalOnMissingBean})이 비활성화되어
 * {@code spring.data.redis.password/database/ssl/timeout} 등 나머지 속성이 무시되기 때문이다.
 * 알림 프리미티브(멱등성 키, 지연 큐)는 payload를 문자열로 다루므로 {@code StringRedisTemplate}이면 충분하다.
 * <p>
 * 이 설정 클래스는 커넥션 헬스체크와, 지연 큐 폴링 스케줄러({@link DelayedQueuePoller})를 위한
 * {@code @EnableScheduling}만 담당한다.
 */
@Slf4j
@Configuration
@EnableScheduling
public class RedisConfig {

	@Value("${spring.data.redis.host:localhost}")
	private String host;

	@Value("${spring.data.redis.port:6379}")
	private int port;

	/**
	 * 애플리케이션 기동 시 Redis 커넥션 정상 여부를 PING으로 1회 확인해 로그로 남긴다.
	 * 실패해도 기동은 막지 않고 에러 로그만 남긴다.
	 */
	@Bean
	public ApplicationRunner redisConnectionHealthCheck(RedisConnectionFactory connectionFactory) {
		return args -> {
			try (RedisConnection connection = connectionFactory.getConnection()) {
				String pong = connection.ping();
				log.info("[Redis] 커넥션 확인 성공 (host={}, port={}, ping={})", host, port, pong);
			} catch (Exception e) {
				log.error("[Redis] 커넥션 확인 실패 (host={}, port={}): {}", host, port, e.getMessage());
			}
		};
	}
}