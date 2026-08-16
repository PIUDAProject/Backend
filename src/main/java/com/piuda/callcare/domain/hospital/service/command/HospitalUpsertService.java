package com.piuda.callcare.domain.hospital.service.command;

import com.piuda.callcare.domain.hospital.client.dto.HiraHospitalApiResponse;
import com.piuda.callcare.domain.hospital.converter.HospitalConverter;
import com.piuda.callcare.domain.hospital.document.HospitalDocument;
import com.piuda.callcare.domain.hospital.entity.Hospital;
import com.piuda.callcare.domain.hospital.repository.HospitalRepository;
import com.piuda.callcare.domain.hospital.repository.HospitalSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalUpsertService {

    private final HospitalRepository hospitalRepository;
    private final HospitalSearchRepository hospitalSearchRepository;
    private final HospitalConverter hospitalConverter;

    public record UpsertResult(int inserted, int updated, int failed) {
    }

    @Transactional
    public UpsertResult upsertPage(List<HiraHospitalApiResponse.Item> items, LocalDateTime syncedAt) {
        List<HiraHospitalApiResponse.Item> validItems = new ArrayList<>();
        int validationFailed = 0;

        for (HiraHospitalApiResponse.Item item : items) {
            if (isBlank(item.ykiho()) || isBlank(item.yadmNm())) {
                validationFailed++;
                log.warn("병원 데이터 검증 실패로 스킵 - ykiho: {}, name: {}", item.ykiho(), item.yadmNm());
            } else {
                validItems.add(item);
            }
        }

        if (validItems.isEmpty()) {
            return new UpsertResult(0, 0, validationFailed);
        }

        Map<String, HiraHospitalApiResponse.Item> uniqueItemsByExternalId = validItems.stream()
                .collect(Collectors.toMap(
                        HiraHospitalApiResponse.Item::ykiho,
                        Function.identity(),
                        (first, duplicate) -> {
                            log.warn("한 페이지 내 중복 요양기호를 스킵합니다. ykiho: {}", duplicate.ykiho());
                            return first;
                        },
                        LinkedHashMap::new
                ));
        validItems = new ArrayList<>(uniqueItemsByExternalId.values());

        List<String> externalIds = validItems.stream()
                .map(HiraHospitalApiResponse.Item::ykiho)
                .toList();

        Map<String, Hospital> existingByExternalId = hospitalRepository.findAllByExternalIdIn(externalIds).stream()
                .collect(Collectors.toMap(Hospital::getExternalId, Function.identity()));

        List<Hospital> toInsert = new ArrayList<>();
        List<Hospital> touched = new ArrayList<>(); // ES 색인 대상 (신규 + 갱신)
        int updated = 0;

        for (HiraHospitalApiResponse.Item item : validItems) {
            Hospital existing = existingByExternalId.get(item.ykiho());
            if (existing != null) {
                existing.updateFrom(item.yadmNm(), item.addr(), item.telno(), syncedAt);
                touched.add(existing);
                updated++;
            } else {
                Hospital hospital = Hospital.builder()
                        .externalId(item.ykiho())
                        .name(item.yadmNm())
                        .address(item.addr())
                        .phoneNumber(item.telno())
                        .build();
                toInsert.add(hospital);
            }
        }

        if (!toInsert.isEmpty()) {
            List<Hospital> saved = hospitalRepository.saveAll(toInsert);
            touched.addAll(saved);
        }

        if (!touched.isEmpty()) {
            List<HospitalDocument> documents = touched.stream()
                    .map(hospitalConverter::toDocument)
                    .toList();
            hospitalSearchRepository.saveAll(documents);
        }

        return new UpsertResult(toInsert.size(), updated, validationFailed);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Transactional
    public int deactivateStaleHospitals(LocalDateTime syncStartedAt) {
        List<Hospital> staleHospitals = hospitalRepository.findAllByActiveTrueAndLastSyncedAtBefore(syncStartedAt);
        if (staleHospitals.isEmpty()) {
            return 0;
        }

        staleHospitals.forEach(Hospital::deactivate);

        List<String> staleDocumentIds = staleHospitals.stream()
                .map(hospital -> String.valueOf(hospital.getId()))
                .toList();
        hospitalSearchRepository.deleteAllById(staleDocumentIds);

        return staleHospitals.size();
    }
}
