package com.interviewassistant.server.repository;

import com.interviewassistant.server.entity.PaymentCallbackLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentCallbackLogRepository extends JpaRepository<PaymentCallbackLog, String> {
    List<PaymentCallbackLog> findTop100ByOrderByCreatedAtDesc();

    List<PaymentCallbackLog> findTop100ByOrderIdOrderByCreatedAtDesc(String orderId);

    List<PaymentCallbackLog> findTop100ByStatusOrderByCreatedAtDesc(String status);
}
