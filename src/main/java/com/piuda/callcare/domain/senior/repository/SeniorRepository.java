package com.piuda.callcare.domain.senior.repository;

import com.piuda.callcare.domain.senior.entity.Senior;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeniorRepository extends JpaRepository<Senior, Long> {

    Optional<Senior> findByIdAndUser_Id(Long id, Long userId);
}
