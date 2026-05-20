package com.piuda.callcare.domain.user.entity;

import com.piuda.callcare.domain.user.enums.Provider;
import com.piuda.callcare.global.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class User extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long id;

	@Column(name = "email")
	private String email;

	@Column(name = "phone_number", length = 20)
	private String phoneNumber;

	@Column(name = "provider", nullable = false)
	private Provider provider;

	@Column(name = "provider_id", nullable = false)
	private String providerId;


	@Builder
	public User(String email, String phoneNumber, Provider provider, String providerId) {
		this.email = email;
		this.phoneNumber = phoneNumber;
		this.provider = provider;
		this.providerId = providerId;

	}
}