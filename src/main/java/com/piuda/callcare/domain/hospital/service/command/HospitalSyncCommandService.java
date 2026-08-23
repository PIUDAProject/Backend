package com.piuda.callcare.domain.hospital.service.command;

import com.piuda.callcare.domain.hospital.client.HiraHospitalClient;
import com.piuda.callcare.domain.hospital.client.dto.HiraHospitalApiResponse;
import com.piuda.callcare.domain.hospital.entity.HospitalSyncHistory;
import com.piuda.callcare.domain.hospital.enums.HospitalSyncStatus;
import com.piuda.callcare.domain.hospital.repository.HospitalSyncHistoryRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class HospitalSyncCommandService {

    private static final int NUM_OF_ROWS = 100;
    private static final double MIN_COMPLETENESS_RATIO = 0.99;
    private static final double MIN_BASELINE_RATIO = 0.8;

    private final HiraHospitalClient hiraHospitalClient;
    private final HospitalUpsertService hospitalUpsertService;
    private final HospitalSyncHistoryRepository hospitalSyncHistoryRepository;
    private final TaskExecutor taskExecutor;

    public HospitalSyncCommandService(
            HiraHospitalClient hiraHospitalClient,
            HospitalUpsertService hospitalUpsertService,
            HospitalSyncHistoryRepository hospitalSyncHistoryRepository,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.hiraHospitalClient = hiraHospitalClient;
        this.hospitalUpsertService = hospitalUpsertService;
        this.hospitalSyncHistoryRepository = hospitalSyncHistoryRepository;
        this.taskExecutor = taskExecutor;
    }

    public record SyncStartResult(Long historyId, HospitalSyncStatus status) {
    }

    public SyncStartResult startSync() {
        return startSync(null);
    }

    public SyncStartResult startSync(Integer maxPages) {
        if (hospitalSyncHistoryRepository.existsByStatus(HospitalSyncStatus.RUNNING)) {
            log.warn("이미 실행 중인 병원 동기화가 있어 요청을 거부합니다.");
            throw new CallCareException(ErrorCode.HOSPITAL_SYNC_ALREADY_RUNNING);
        }

        boolean isFullSync = (maxPages == null);

        LocalDateTime syncStartedAt = LocalDateTime.now();
        HospitalSyncHistory history = hospitalSyncHistoryRepository.save(HospitalSyncHistory.start(isFullSync));
        log.info("병원 정보 데이터 수집 요청 접수 (historyId: {}, maxPages: {})", history.getId(), maxPages);

        try {
            taskExecutor.execute(() -> executeSync(history.getId(), maxPages, syncStartedAt));
        } catch (TaskRejectedException e) {
            history.complete(HospitalSyncStatus.FAILED, 0, 0, 0, 0, "동기화 비동기 작업 제출에 실패했습니다.");
            hospitalSyncHistoryRepository.save(history);
            throw new CallCareException(ErrorCode.HOSPITAL_SYNC_FAILED, "동기화 작업을 시작할 수 없습니다.");
        }

        return new SyncStartResult(history.getId(), HospitalSyncStatus.RUNNING);
    }

    private void executeSync(Long historyId, Integer maxPages, LocalDateTime syncStartedAt) {
        HospitalSyncHistory history = hospitalSyncHistoryRepository.findById(historyId)
                .orElseThrow(() -> new IllegalStateException("병원 동기화 이력을 찾을 수 없습니다. historyId=" + historyId));
        boolean isFullSync = history.isFullSync();
        log.info("병원 정보 데이터 수집 시작 (historyId: {}, maxPages: {})", historyId, maxPages);

        int requested = 0;
        int inserted = 0;
        int updated = 0;
        int failed = 0;

        try {
            int pageNo = 1;
            Integer expectedTotalCount = null;

            do {
                HiraHospitalApiResponse response = hiraHospitalClient.fetchHospitals(pageNo, NUM_OF_ROWS);

                if (!response.header().isSuccess()) {
                    throw new IllegalStateException(
                            "HIRA API 오류 응답 - resultCode: %s, resultMsg: %s"
                                    .formatted(response.header().resultCode(), response.header().resultMsg()));
                }

                List<HiraHospitalApiResponse.Item> items = response.body().itemList();
                requested += items.size();

                HospitalUpsertService.UpsertResult upsertResult = hospitalUpsertService.upsertPage(items, syncStartedAt);
                inserted += upsertResult.inserted();
                updated += upsertResult.updated();
                failed += upsertResult.failed();

                history.updateProgress(pageNo, requested, inserted, updated, failed);
                hospitalSyncHistoryRepository.save(history);

                int responseTotalCount = response.body().totalCount();
                if (expectedTotalCount == null) {
                    expectedTotalCount = responseTotalCount;
                } else if (expectedTotalCount != responseTotalCount) {
                    log.warn("HIRA totalCount가 수집 도중 변경됐습니다 - 최초: {}, 현재: {}, pageNo: {}",
                            expectedTotalCount, responseTotalCount, pageNo);
                }

                int totalPages = (int) Math.ceil((double) expectedTotalCount / NUM_OF_ROWS);
                log.info("병원 정보 수집 진행 중 - {}/{} 페이지 (누적 신규 {}건, 갱신 {}건)",
                        pageNo, totalPages, inserted, updated);

                pageNo++;

                if (maxPages != null && pageNo > maxPages) {
                    log.info("maxPages({}) 도달로 수집을 중단합니다 (테스트 모드)", maxPages);
                    isFullSync = false;
                    break;
                }
            } while ((long) (pageNo - 1) * NUM_OF_ROWS < expectedTotalCount);

            if (isFullSync) {
                int processed = inserted + updated;
                if (isAbnormalDrop(requested, processed, expectedTotalCount)) {
                    log.warn("이번 수집을 폐업 병원 판단에 신뢰할 수 없어 비활성화를 건너뜁니다. " +
                            "(requested: {}, processed: {}, HIRA totalCount: {})",
                            requested, processed, expectedTotalCount);
                } else {
                    int deactivated = hospitalUpsertService.deactivateStaleHospitals(syncStartedAt);
                    if (deactivated > 0) {
                        log.info("공공데이터에서 더 이상 확인되지 않는 병원 {}건을 비활성화하고 검색 색인에서 제외했습니다.", deactivated);
                    }
                }
            }

            HospitalSyncStatus finalStatus = failed > 0 ? HospitalSyncStatus.PARTIAL_SUCCESS : HospitalSyncStatus.SUCCESS;
            history.complete(finalStatus, requested, inserted, updated, failed, null);
            hospitalSyncHistoryRepository.save(history);

            log.info("병원 정보 공공데이터 수집 완료 - 상태: {}, 조회: {}건, 신규: {}건, 갱신: {}건, 실패: {}건",
                    finalStatus, requested, inserted, updated, failed);

        } catch (Exception e) {
            log.error("병원 정보 공공데이터 수집 실패 - 기존 DB 데이터는 유지됩니다. (지금까지 반영: 신규 {}건, 갱신 {}건)",
                    inserted, updated, e);
            history.complete(HospitalSyncStatus.FAILED, requested, inserted, updated, failed, e.getMessage());
            hospitalSyncHistoryRepository.save(history);
        }
    }

    public void failRunningHistory(Long historyId) {
        HospitalSyncHistory history = hospitalSyncHistoryRepository.findById(historyId)
                .orElseThrow(() -> new CallCareException(ErrorCode.NOT_FOUND, "병원 동기화 이력을 찾을 수 없습니다."));

        if (history.getStatus() != HospitalSyncStatus.RUNNING) {
            throw new CallCareException(ErrorCode.DATA_CONFLICT, "RUNNING 상태인 병원 동기화 이력만 실패 처리할 수 있습니다.");
        }

        history.failManually();
        hospitalSyncHistoryRepository.save(history);
        log.warn("병원 동기화 RUNNING 이력을 관리자가 수동 실패 처리했습니다. (historyId: {}, lastCompletedPage: {})",
                historyId, history.getLastCompletedPage());
    }

    private boolean isAbnormalDrop(int requested, int processed, int expectedTotalCount) {
        if (requested == 0 || processed == 0) {
            return true;
        }
        if (expectedTotalCount > 0) {
            double completeness = (double) processed / expectedTotalCount;
            if (completeness < MIN_COMPLETENESS_RATIO) {
                log.warn("실제 DB 반영 비율이 기준 미달입니다. (processed: {}, expected: {}, ratio: {})",
                        processed, expectedTotalCount, completeness);
                return true;
            }
        }

        List<HospitalSyncHistory> previous = hospitalSyncHistoryRepository.findTop1ByStatusInAndFullSyncTrueOrderByStartedAtDesc(
                List.of(HospitalSyncStatus.SUCCESS, HospitalSyncStatus.PARTIAL_SUCCESS));

        if (previous.isEmpty()) {
            log.warn("이전 정상 동기화 이력이 없어 이번 실행은 기준선(baseline)으로만 사용하고 stale 비활성화는 수행하지 않습니다.");
            return true;
        }

        HospitalSyncHistory previousHistory = previous.get(0);
        int previousProcessed = previousHistory.getInsertedCount() + previousHistory.getUpdatedCount();
        if (previousProcessed <= 0) {
            log.warn("직전 정상 전체 동기화 DB 반영량이 {}건이라 stale 비활성화를 수행하지 않습니다.", previousProcessed);
            return true;
        }

        double ratio = (double) processed / previousProcessed;
        return ratio < MIN_BASELINE_RATIO;
    }
}
