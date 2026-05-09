package com.interviewassistant.server.controller;

import com.interviewassistant.server.config.AssistantProperties;
import com.interviewassistant.server.dto.BalanceTransactionResponse;
import com.interviewassistant.server.dto.CreateOrderRequest;
import com.interviewassistant.server.dto.CreatePaymentRequest;
import com.interviewassistant.server.dto.FinishUsageSessionRequest;
import com.interviewassistant.server.dto.MockPaymentCallbackRequest;
import com.interviewassistant.server.dto.OrderResponse;
import com.interviewassistant.server.dto.PaymentCreateResponse;
import com.interviewassistant.server.dto.PaymentNotifyResult;
import com.interviewassistant.server.dto.PlanResponse;
import com.interviewassistant.server.dto.StartUsageSessionRequest;
import com.interviewassistant.server.dto.UsageSessionResponse;
import com.interviewassistant.server.dto.UserProfileResponse;
import com.interviewassistant.server.service.CommercialFacadeService;
import com.interviewassistant.server.service.CurrentUserService;
import com.interviewassistant.server.service.PaymentCallbackLogService;
import com.interviewassistant.server.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CommercialController {
    private final CommercialFacadeService commercialFacadeService;
    private final CurrentUserService currentUserService;
    private final PaymentService paymentService;
    private final PaymentCallbackLogService paymentCallbackLogService;
    private final AssistantProperties assistantProperties;

    public CommercialController(CommercialFacadeService commercialFacadeService,
                                CurrentUserService currentUserService,
                                PaymentService paymentService,
                                PaymentCallbackLogService paymentCallbackLogService,
                                AssistantProperties assistantProperties) {
        this.commercialFacadeService = commercialFacadeService;
        this.currentUserService = currentUserService;
        this.paymentService = paymentService;
        this.paymentCallbackLogService = paymentCallbackLogService;
        this.assistantProperties = assistantProperties;
    }

    @GetMapping("/plans")
    public List<PlanResponse> listPlans() {
        return commercialFacadeService.listPlans();
    }

    @GetMapping("/user/profile")
    public UserProfileResponse profile() {
        return commercialFacadeService.getProfile(currentUserService.requireCurrentUserId());
    }

    @PostMapping("/orders")
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return commercialFacadeService.createOrder(currentUserService.requireCurrentUserId(), request);
    }

    @GetMapping("/orders")
    public List<OrderResponse> listOrders() {
        return commercialFacadeService.listOrders(currentUserService.requireCurrentUserId());
    }

    @PostMapping("/payment/create")
    public PaymentCreateResponse createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        try {
            return paymentService.createPayment(commercialFacadeService.resolveOwnedPendingOrder(
                currentUserService.requireCurrentUserId(),
                request.getOrderId(),
                request.getPaymentChannel()
            ));
        } catch (Exception exception) {
            throw new IllegalStateException(exception.getMessage() == null ? "创建支付失败" : exception.getMessage(), exception);
        }
    }

    @PostMapping("/payment/mock-paid")
    public OrderResponse mockPaymentPaid(@Valid @RequestBody MockPaymentCallbackRequest request) {
        if (!assistantProperties.getPayment().isMockPaymentEnabled()) {
            throw new SecurityException("模拟支付未启用");
        }
        return commercialFacadeService.markOrderPaid(currentUserService.requireCurrentUserId(), request);
    }

    @PostMapping("/payment/wechat/notify")
    public ResponseEntity<Map<String, String>> wechatNotify(@RequestBody String notifyBody,
                                                            HttpServletRequest request) {
        Map<String, String> headers = extractHeaders(request);
        try {
            PaymentNotifyResult notifyResult = paymentService.parseWechatPaidNotify(notifyBody, headers);
            commercialFacadeService.markOrderPaid(notifyResult);
            paymentCallbackLogService.recordSuccess("WECHAT", notifyBody, headers, notifyResult);
            Map<String, String> response = new HashMap<>();
            response.put("code", "SUCCESS");
            response.put("message", "成功");
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            paymentCallbackLogService.recordFailure("WECHAT", notifyBody, headers, exception);
            Map<String, String> response = new HashMap<>();
            response.put("code", "FAIL");
            response.put("message", exception.getMessage() == null ? "支付回调处理失败" : exception.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/payment/alipay/notify")
    public String alipayNotify(@RequestBody String body,
                               HttpServletRequest request) {
        Map<String, String> headers = extractHeaders(request);
        try {
            Map<String, String> params = parseFormBody(body);
            PaymentNotifyResult notifyResult = paymentService.parseAlipayPaidNotify(params);
            commercialFacadeService.markOrderPaid(notifyResult);
            paymentCallbackLogService.recordSuccess("ALIPAY", body, headers, notifyResult);
            return "success";
        } catch (Exception exception) {
            paymentCallbackLogService.recordFailure("ALIPAY", body, headers, exception);
            return "fail";
        }
    }

    @PostMapping("/usage/start")
    public UsageSessionResponse startUsageSession(@Valid @RequestBody StartUsageSessionRequest request) {
        return commercialFacadeService.startUsageSession(currentUserService.requireCurrentUserId(), request);
    }

    @GetMapping("/usage")
    public List<UsageSessionResponse> listUsageSessions() {
        return commercialFacadeService.listUsageSessions(currentUserService.requireCurrentUserId());
    }

    @PostMapping("/usage/finish")
    public UsageSessionResponse finishUsageSession(@Valid @RequestBody FinishUsageSessionRequest request) {
        return commercialFacadeService.finishUsageSession(currentUserService.requireCurrentUserId(), request);
    }

    @GetMapping("/balance/transactions")
    public List<BalanceTransactionResponse> listBalanceTransactions() {
        return commercialFacadeService.listBalanceTransactions(currentUserService.requireCurrentUserId());
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }

    private Map<String, String> parseFormBody(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) {
            return params;
        }
        for (String pair : body.split("&")) {
            int splitIndex = pair.indexOf('=');
            if (splitIndex <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, splitIndex), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(splitIndex + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }
}
