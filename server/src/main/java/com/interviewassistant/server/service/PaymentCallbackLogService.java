package com.interviewassistant.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewassistant.server.dto.PaymentCallbackLogResponse;
import com.interviewassistant.server.dto.PaymentNotifyResult;
import com.interviewassistant.server.entity.PaymentCallbackLog;
import com.interviewassistant.server.repository.PaymentCallbackLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class PaymentCallbackLogService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final PaymentCallbackLogRepository paymentCallbackLogRepository;
    private final ObjectMapper objectMapper;

    public PaymentCallbackLogService(PaymentCallbackLogRepository paymentCallbackLogRepository,
                                     ObjectMapper objectMapper) {
        this.paymentCallbackLogRepository = paymentCallbackLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String paymentChannel, String requestBody, Map<String, String> headers, PaymentNotifyResult result) {
        PaymentCallbackLog log = baseLog(paymentChannel, requestBody, headers);
        log.setStatus("SUCCESS");
        if (result != null) {
            log.setOrderId(result.getOrderId());
            log.setTransactionId(result.getTransactionId());
        }
        paymentCallbackLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String paymentChannel, String requestBody, Map<String, String> headers, Exception exception) {
        PaymentCallbackLog log = baseLog(paymentChannel, requestBody, headers);
        log.setStatus("FAILED");
        log.setErrorMessage(truncate(exception.getMessage(), 500));
        paymentCallbackLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<PaymentCallbackLogResponse> listRecent(String orderId, String status) {
        String normalizedOrderId = orderId == null ? "" : orderId.trim();
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        List<PaymentCallbackLog> logs;
        if (!normalizedOrderId.isBlank()) {
            logs = paymentCallbackLogRepository.findTop100ByOrderIdOrderByCreatedAtDesc(normalizedOrderId);
        } else if (!normalizedStatus.isBlank()) {
            logs = paymentCallbackLogRepository.findTop100ByStatusOrderByCreatedAtDesc(normalizedStatus);
        } else {
            logs = paymentCallbackLogRepository.findTop100ByOrderByCreatedAtDesc();
        }
        return logs.stream().map(this::toResponse).toList();
    }

    private PaymentCallbackLog baseLog(String paymentChannel, String requestBody, Map<String, String> headers) {
        PaymentCallbackLog log = new PaymentCallbackLog();
        log.setPaymentChannel(paymentChannel);
        log.setRequestBody(truncate(requestBody == null ? "" : requestBody, 8000));
        log.setRequestHeaders(toJson(headers));
        return log;
    }

    private PaymentCallbackLogResponse toResponse(PaymentCallbackLog log) {
        return new PaymentCallbackLogResponse(
            log.getId(),
            log.getPaymentChannel(),
            log.getOrderId(),
            log.getTransactionId(),
            log.getStatus(),
            log.getErrorMessage(),
            log.getRequestBody(),
            log.getRequestHeaders(),
            log.getCreatedAt() == null ? null : log.getCreatedAt().format(FORMATTER)
        );
    }

    private String toJson(Map<String, String> headers) {
        try {
            return objectMapper.writeValueAsString(headers == null ? Map.of() : headers);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
