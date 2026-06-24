package com.piuda.callcare.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.piuda.callcare.domain.user.entity.User;
import com.piuda.callcare.domain.user.enums.Provider;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

	boolean existsByProviderAndProviderId(Provider provider, String providerId);
}
