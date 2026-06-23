package com.piuda.callcare.domain.druginfo.repository;

import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DrugInfoRepository extends JpaRepository<DrugInfo, Long> {

    // 품목기준코드로 단건 조회
    Optional<DrugInfo> findByItemSeq(String itemSeq);

    // 약품명 포함 검색 (대소문자 무시, 이름 오름차순)
    List<DrugInfo> findByItemNameContainingIgnoreCaseOrderByItemNameAsc(String keyword, Pageable pageable);
}
