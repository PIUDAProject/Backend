package com.piuda.callcare.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import com.piuda.callcare.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final CorsConfigurationSource corsConfigurationSource;
	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/test/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/oauth/kakao", "/api/auth/oauth/naver").permitAll()
				.requestMatchers("/actuator/health").permitAll()
				.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/drugs/**", "/api/search/drugs/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/hospitals/search").permitAll()
				// TODO: 인증 필터 도입 시 제거하고, 로그인 사용자의 seniorId 소유권 검증으로 전환
				.requestMatchers(HttpMethod.GET, "/api/home/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/home/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/ocr/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/medications").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/medications/batch").permitAll()
				.anyRequest().authenticated()
			)
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}