package com.piuda.callcare.domain.hospital.scheduler;

import com.piuda.callcare.domain.hospital.service.command.HospitalSyncCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HospitalSyncScheduler {

    private final HospitalSyncCommandService hospitalSyncCommandService;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void syncHospitals() {
        log.info("HospitalSyncScheduler 실행");
        hospitalSyncCommandService.startSync();
    }
}
