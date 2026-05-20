package com.piuda.callcare.domain.ocrresult.repository;

import com.piuda.callcare.domain.ocrresult.entity.OcrResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcrResultRepository extends JpaRepository<OcrResult, Long> {
}
