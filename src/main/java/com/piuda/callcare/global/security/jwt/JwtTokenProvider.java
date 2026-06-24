package com.piuda.callcare.global.security.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

// 토큰 발급, 검증
@Component
public class JwtTokenProvider {

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.access-expiration-ms}")
	private long accessExpMs;

	@Value("${jwt.refresh-expiration-ms}")
	private long refreshExpMs;

	@Value("${jwt.issuer}")
	private String issuer;

	private SecretKey key;

	@PostConstruct
	void init() {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public String createAccessToken(Long userId) {
		Date now = new Date();
		return Jwts.builder()
			.issuer(issuer)
			.subject(String.valueOf(userId))
			.issuedAt(now)
			.expiration(new Date(now.getTime() + accessExpMs))
			.signWith(key)
			.compact();
	}

	public String createRefreshToken(Long userId) {
		Date now = new Date();
		return Jwts.builder()
			.issuer(issuer)
			.subject(String.valueOf(userId))
			.issuedAt(now)
			.expiration(new Date(now.getTime() + refreshExpMs))
			.claim("type", "refresh")
			.signWith(key)
			.compact();
	}

	public boolean validateToken(String token) {
		try {
			parseClaims(token);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public boolean validateRefreshToken(String token) {
		try {
			Claims claims = parseClaims(token);
			return "refresh".equals(claims.get("type"));
		} catch (Exception e) {
			return false;
		}
	}

	public Long getUserId(String token) {
		return Long.valueOf(parseClaims(token).getSubject());
	}

	public long getAccessTokenExpireTime() {
		return accessExpMs;
	}

	public long getRefreshTokenExpireTime() {
		return refreshExpMs;
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
			.verifyWith(key)
			.requireIssuer(issuer)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}
}
