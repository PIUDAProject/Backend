package com.piuda.callcare.global.config;

import com.piuda.callcare.domain.hospital.converter.HospitalConverter;
import com.piuda.callcare.domain.hospital.document.HospitalDocument;
import com.piuda.callcare.domain.hospital.repository.HospitalRepository;
import com.piuda.callcare.domain.hospital.repository.HospitalSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class HospitalIndexingInitializer implements CommandLineRunner {

    private final HospitalRepository hospitalRepository;
    private final HospitalSearchRepository hospitalSearchRepository;
    private final HospitalConverter hospitalConverter;

    @Override
    public void run(String... args) {
        try {
            long dbCount = hospitalRepository.count();
            long esCount = hospitalSearchRepository.count();

            if (esCount > 0 && esCount == dbCount) {
                log.info("ES Hospital 인덱스 최신 상태 - 색인 생략 ({}건)", esCount);
                return;
            }

            log.info("ES Hospital 색인 시작 (DB: {}건, ES: {}건)", dbCount, esCount);

            List<HospitalDocument> documents = hospitalRepository.findAll()
                    .stream()
                    .map(hospitalConverter::toDocument)
                    .toList();

            hospitalSearchRepository.saveAll(documents);
            log.info("ES Hospital 색인 완료: {}건", documents.size());

        } catch (Exception e) {
            log.error("ES Hospital 색인 실패 - 검색 기능이 제한될 수 있습니다: {}", e.getMessage());
        }
    }
}
