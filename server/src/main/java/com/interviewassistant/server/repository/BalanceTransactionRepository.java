package com.interviewassistant.server.repository;

import com.interviewassistant.server.entity.BalanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, String> {
    List<BalanceTransaction> findByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsBySourceTypeAndSourceIdAndType(String sourceType, String sourceId, String type);
}
