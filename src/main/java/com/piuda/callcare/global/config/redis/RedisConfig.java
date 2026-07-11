package com.piuda.callcare.global.config.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 인프라 공통 설정.
 * <p>
 * 알림 프리미티브(멱등성 키, 지연 큐)는 payload를 JSON 문자열로 다루므로
 * 문자열 직렬화({@link StringRedisTemplate})면 충분하다.
 * <p>
 * {@code @EnableScheduling}은 지연 큐 폴링 스케줄러({@link DelayedQueuePoller})를 위해 활성화한다.
 */
@Slf4j
@Configuration
@EnableScheduling
public class RedisConfig {

	@Value("${spring.data.redis.host:localhost}")
	private String host;

	@Value("${spring.data.redis.port:6379}")
	private int port;

	@Bean
	public RedisConnectionFactory redisConnectionFactory() {
		return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
	}

	@Bean
	public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
		return new StringRedisTemplate(connectionFactory);
	}

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