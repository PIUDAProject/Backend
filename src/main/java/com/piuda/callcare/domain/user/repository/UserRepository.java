package com.piuda.callcare.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.piuda.callcare.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
