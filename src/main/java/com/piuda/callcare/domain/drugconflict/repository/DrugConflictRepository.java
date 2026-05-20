package com.piuda.callcare.domain.drugconflict.repository;

import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrugConflictRepository extends JpaRepository<DrugConflict, Long> {
}
