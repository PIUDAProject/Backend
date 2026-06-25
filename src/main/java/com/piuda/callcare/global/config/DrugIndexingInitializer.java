package com.piuda.callcare.global.config;

import com.piuda.callcare.domain.druginfo.converter.DrugInfoConverter;
import com.piuda.callcare.domain.druginfo.document.DrugDocument;
import com.piuda.callcare.domain.druginfo.repository.DrugInfoRepository;
import com.piuda.callcare.domain.druginfo.repository.DrugSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DrugIndexingInitializer implements CommandLineRunner {

    private final DrugInfoRepository drugInfoRepository;
    private final DrugSearchRepository drugSearchRepository;
    private final DrugInfoConverter drugInfoConverter;

    @Override
    public void run(String... args) {
        try {
            long dbCount = drugInfoRepository.count();
            long esCount = drugSearchRepository.count();

            // DB와 ES 건수가 일치하면 색인 완료로 간주
            if (esCount > 0 && esCount == dbCount) {
                log.info("ES DrugInfo 인덱스 최신 상태 - 색인 생략 ({}건)", esCount);
                return;
            }

            log.info("ES DrugInfo 색인 시작 (DB: {}건, ES: {}건)", dbCount, esCount);

            List<DrugDocument> documents = drugInfoRepository.findAll()
                    .stream()
                    .filter(drug -> drug.getItemSeq() != null)
                    .map(drugInfoConverter::toDocument)
                    .toList();

            drugSearchRepository.saveAll(documents);
            log.info("ES DrugInfo 색인 완료: {}건", documents.size());

        } catch (Exception e) {
            // ES 장애 시 앱 기동은 정상 진행, 검색 기능만 비정상
            log.error("ES DrugInfo 색인 실패 - 검색 기능이 제한될 수 있습니다.", e);
        }
    }
}
