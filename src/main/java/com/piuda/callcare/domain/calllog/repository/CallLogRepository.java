package com.piuda.callcare.domain.calllog.repository;

import com.piuda.callcare.domain.calllog.entity.CallLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallLogRepository extends JpaRepository<CallLog, Long> {
}
