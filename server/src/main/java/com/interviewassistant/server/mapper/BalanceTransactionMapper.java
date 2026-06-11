package com.interviewassistant.server.mapper;

import com.interviewassistant.server.entity.BalanceTransaction;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BalanceTransactionMapper {
    List<BalanceTransaction> selectByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    boolean existsBySourceTypeAndSourceIdAndType(
        @Param("sourceType") String sourceType,
        @Param("sourceId") String sourceId,
        @Param("type") String type
    );

    int insert(BalanceTransaction transaction);
}
