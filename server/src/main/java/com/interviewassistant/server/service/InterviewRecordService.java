package com.interviewassistant.server.service;

import com.interviewassistant.server.dto.InterviewRecordRequest;
import com.interviewassistant.server.dto.InterviewRecordResponse;
import com.interviewassistant.server.entity.InterviewRecord;
import com.interviewassistant.server.repository.InterviewRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

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
    public List<InterviewRecordResponse> listRecent(String userId) {
        return interviewRecordRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
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
}
