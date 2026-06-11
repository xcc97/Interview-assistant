package com.interviewassistant.server.mapper;

import com.interviewassistant.server.entity.CommercialOrder;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface CommercialOrderMapper {
    CommercialOrder selectById(@Param("id") String id);

    List<CommercialOrder> selectByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    List<CommercialOrder> selectByStatusAndCreatedAtBefore(@Param("status") String status, @Param("createdBefore") OffsetDateTime createdBefore);

    List<CommercialOrder> selectTop100OrderByCreatedAtDesc();

    List<CommercialOrder> selectTop100ByStatusOrderByCreatedAtDesc(@Param("status") String status);

    boolean existsByPaymentChannelAndPaymentTransactionIdAndIdNot(
        @Param("paymentChannel") String paymentChannel,
        @Param("paymentTransactionId") String paymentTransactionId,
        @Param("excludedOrderId") String excludedOrderId
    );

    int insert(CommercialOrder order);

    int update(CommercialOrder order);

    int updateBatch(@Param("orders") List<CommercialOrder> orders);
}
