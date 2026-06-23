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
        if (drugSearchRepository.count() > 0) {
            log.info("ES DrugInfo 인덱스 이미 존재 - 색인 생략");
            return;
        }

        log.info("ES DrugInfo 색인 시작");

        List<DrugDocument> documents = drugInfoRepository.findAll()
                .stream()
                .filter(drug -> drug.getItemSeq() != null)
                .map(drugInfoConverter::toDocument)
                .toList();

        drugSearchRepository.saveAll(documents);
        log.info("ES DrugInfo 색인 완료: {}건", documents.size());
    }
}
