package com.interviewassistant.server.service;

import com.interviewassistant.server.dto.PaymentCreateResponse;
import com.interviewassistant.server.dto.PaymentNotifyResult;
import com.interviewassistant.server.entity.CommercialOrder;

import java.util.Map;

public interface PaymentService {
    PaymentCreateResponse createPayment(CommercialOrder order) throws Exception;

    PaymentNotifyResult queryPaidOrder(CommercialOrder order) throws Exception;

    PaymentNotifyResult parseWechatPaidNotify(String notifyBody, Map<String, String> headers) throws Exception;

    PaymentNotifyResult parseAlipayPaidNotify(Map<String, String> params) throws Exception;
}
