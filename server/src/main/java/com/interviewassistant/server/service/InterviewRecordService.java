package com.interviewassistant.server.service;

import com.interviewassistant.server.dto.InterviewRecordRequest;
import com.interviewassistant.server.dto.InterviewRecordResponse;
import com.interviewassistant.server.dto.InterviewSessionSummaryResponse;
import com.interviewassistant.server.entity.InterviewRecord;
import com.interviewassistant.server.repository.InterviewRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InterviewRecordService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final InterviewRecordRepository interviewRecordRepository;

    public InterviewRecordService(InterviewRecordRepository interviewRecordRepository) {
        this.interviewRecordRepository = interviewRecordRepository;
    }

    @Transactional
    public InterviewRecordResponse create(String userId, InterviewRecordRequest request) {
        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setUsageSessionId(trimToNull(request.getUsageSessionId()));
        record.setQuestion(limitText(request.getQuestion(), 8000));
        record.setAnswer(limitText(request.getAnswer(), 12000));
        return toResponse(interviewRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public List<InterviewSessionSummaryResponse> listSessionSummaries(String userId) {
        Map<String, SessionGroup> groups = new LinkedHashMap<>();
        for (InterviewRecord record : interviewRecordRepository.findTop200ByUserIdOrderByCreatedAtDesc(userId)) {
            String sessionId = resolveSessionId(record);
            groups.computeIfAbsent(sessionId, ignored -> new SessionGroup(sessionId)).add(record);
        }
        List<InterviewSessionSummaryResponse> summaries = new ArrayList<>();
        for (SessionGroup group : groups.values()) {
            summaries.add(group.toResponse());
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public List<InterviewRecordResponse> listSessionRecords(String userId, String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return List.of();
        }
        return interviewRecordRepository.findByUserIdAndUsageSessionIdOrderByCreatedAtAsc(userId, sessionId.trim()).stream()
            .map(this::toResponse)
            .toList();
    }

    private InterviewRecordResponse toResponse(InterviewRecord record) {
        return new InterviewRecordResponse(
            record.getId(),
            record.getUsageSessionId(),
            record.getQuestion(),
            record.getAnswer(),
            record.getCreatedAt() == null ? "" : record.getCreatedAt().format(FORMATTER)
        );
    }

    private String limitText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String resolveSessionId(InterviewRecord record) {
        String usageSessionId = record.getUsageSessionId();
        if (usageSessionId != null && !usageSessionId.trim().isEmpty()) {
            return usageSessionId.trim();
        }
        return record.getId();
    }

    private class SessionGroup {
        private final String sessionId;
        private OffsetDateTime startedAt;
        private OffsetDateTime lastUpdatedAt;
        private int recordCount;
        private String previewQuestion = "";

        private SessionGroup(String sessionId) {
            this.sessionId = sessionId;
        }

        private void add(InterviewRecord record) {
            OffsetDateTime createdAt = record.getCreatedAt();
            if (createdAt != null && (startedAt == null || createdAt.isBefore(startedAt))) {
                startedAt = createdAt;
            }
            if (createdAt != null && (lastUpdatedAt == null || createdAt.isAfter(lastUpdatedAt))) {
                lastUpdatedAt = createdAt;
                previewQuestion = record.getQuestion() == null ? "" : record.getQuestion();
            }
            recordCount += 1;
        }

        private InterviewSessionSummaryResponse toResponse() {
            return new InterviewSessionSummaryResponse(
                sessionId,
                startedAt == null ? "" : startedAt.format(FORMATTER),
                lastUpdatedAt == null ? "" : lastUpdatedAt.format(FORMATTER),
                recordCount,
                previewQuestion
            );
        }
    }
}
