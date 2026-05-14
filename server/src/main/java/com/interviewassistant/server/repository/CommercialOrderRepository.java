package com.interviewassistant.server.repository;

import com.interviewassistant.server.entity.CommercialOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface CommercialOrderRepository extends JpaRepository<CommercialOrder, String> {
    List<CommercialOrder> findByUserIdOrderByCreatedAtDesc(String userId);

    List<CommercialOrder> findByStatusAndCreatedAtBefore(String status, OffsetDateTime createdBefore);

    boolean existsByPaymentChannelAndPaymentTransactionIdAndIdNot(String paymentChannel, String paymentTransactionId, String excludedOrderId);

    List<CommercialOrder> findTop100ByOrderByCreatedAtDesc();

    List<CommercialOrder> findTop100ByStatusOrderByCreatedAtDesc(String status);
}
