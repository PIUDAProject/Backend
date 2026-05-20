package com.piuda.callcare.domain.druginfo.repository;

import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrugInfoRepository extends JpaRepository<DrugInfo, Long> {
}
