package com.piuda.callcare.global.config.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis 프리미티브 통합 테스트 베이스.
 * <p>
 * redis:7.2 컨테이너를 띄우고, Redis 관련 빈만 담은 최소 컨텍스트를 올린다
 * (전체 앱을 부팅하지 않으므로 MySQL/Elasticsearch가 없어도 실행된다).
 * {@code @Tag("integration")}으로 분리되어 {@code ./gradlew test -Dgroups=integration}에서 실행된다.
 * <p>
 * 컨테이너는 <b>싱글턴 패턴</b>으로 JVM당 한 번만 기동하고 종료하지 않는다(Ryuk이 JVM 종료 시 정리).
 * 여러 IT 클래스가 동일 설정의 Spring 컨텍스트를 캐시 공유하므로, 컨테이너 포트도 테스트 내내
 * 고정되어야 캐시된 컨텍스트가 끊긴 포트를 바라보는 문제를 피할 수 있다.
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest(classes = {RedisConfig.class, IdempotencyKeyStore.class, DelayedQueue.class})
abstract class AbstractRedisIntegrationTest {

	static final GenericContainer<?> REDIS =
		new GenericContainer<>(DockerImageName.parse("redis:7.2")).withExposedPorts(6379);

	static {
		REDIS.start();
	}

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
	}

	@Autowired
	protected StringRedisTemplate stringRedisTemplate;

	// 각 테스트가 깨끗한 상태에서 시작하도록 키를 모두 비운다
	@BeforeEach
	void flushRedis() {
		stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
			connection.serverCommands().flushDb();
			return null;
		});
	}
}