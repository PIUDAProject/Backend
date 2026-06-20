package com.piuda.callcare.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import com.piuda.callcare.domain.druginfo.repository.DrugInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DrugInfoDataInitializer implements CommandLineRunner {

    private final DrugInfoRepository drugInfoRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (drugInfoRepository.count() > 0) {
            log.info("DrugInfo 데이터 이미 존재 - 초기화 생략");
            return;
        }

        log.info("DrugInfo 초기 데이터 로딩 시작");

        InputStream inputStream = new ClassPathResource("data/drug_merged.json").getInputStream();
        JsonNode rootNode = objectMapper.readTree(inputStream);

        List<DrugInfo> drugInfoList = new ArrayList<>();
        for (JsonNode node : rootNode) {
            drugInfoList.add(DrugInfo.builder()
                    .itemSeq(getText(node, "itemSeq"))
                    .itemName(getText(node, "itemName"))
                    .entpName(getText(node, "entpName"))
                    .efcyQesitm(getText(node, "efcy"))
                    .useMethodQesitm(getText(node, "useMethod"))
                    .atpnQesitm(getText(node, "atpnQesitm"))
                    .seQesitm(getText(node, "sideEffect"))
                    .intrcQesitm(getText(node, "intrcQesitm"))
                    .depositMethodQesitm(getText(node, "depositMethodQesitm"))
                    .itemImage(getText(node, "itemImage"))
                    .prductType(getText(node, "prductType"))
                    .spcltyPblc(getText(node, "spcltyPblc"))
                    .build());
        }

        drugInfoRepository.saveAll(drugInfoList);
        log.info("DrugInfo 초기 데이터 로딩 완료: {}건", drugInfoList.size());
    }

    private String getText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        String value = field.asText().trim();
        return value.isEmpty() ? null : value;
    }
}
