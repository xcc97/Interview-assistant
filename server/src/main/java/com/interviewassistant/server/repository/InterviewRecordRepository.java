package com.interviewassistant.server.repository;

import com.interviewassistant.server.entity.InterviewRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewRecordRepository extends JpaRepository<InterviewRecord, String> {
    List<InterviewRecord> findTop200ByUserIdOrderByCreatedAtDesc(String userId);

    List<InterviewRecord> findByUserIdAndUsageSessionIdOrderByCreatedAtAsc(String userId, String usageSessionId);
}
