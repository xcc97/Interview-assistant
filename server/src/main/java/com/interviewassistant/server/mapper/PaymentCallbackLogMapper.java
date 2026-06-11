package com.interviewassistant.server.mapper;

import com.interviewassistant.server.entity.PaymentCallbackLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PaymentCallbackLogMapper {
    List<PaymentCallbackLog> selectTop100OrderByCreatedAtDesc();

    List<PaymentCallbackLog> selectTop100ByOrderIdOrderByCreatedAtDesc(@Param("orderId") String orderId);

    List<PaymentCallbackLog> selectTop100ByStatusOrderByCreatedAtDesc(@Param("status") String status);

    int insert(PaymentCallbackLog log);
}
