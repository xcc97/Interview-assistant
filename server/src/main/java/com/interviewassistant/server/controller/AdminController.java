package com.interviewassistant.server.controller;

import com.interviewassistant.server.dto.AdminCloseOrderRequest;
import com.interviewassistant.server.dto.AdminGrantOrderRequest;
import com.interviewassistant.server.dto.OrderResponse;
import com.interviewassistant.server.dto.PaymentCallbackLogResponse;
import com.interviewassistant.server.dto.ReadinessResponse;
import com.interviewassistant.server.service.CommercialFacadeService;
import com.interviewassistant.server.service.CurrentUserService;
import com.interviewassistant.server.service.PaymentCallbackLogService;
import com.interviewassistant.server.service.ReadinessService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final ReadinessService readinessService;
    private final CommercialFacadeService commercialFacadeService;
    private final CurrentUserService currentUserService;
    private final PaymentCallbackLogService paymentCallbackLogService;

    public AdminController(ReadinessService readinessService,
                           CommercialFacadeService commercialFacadeService,
                           CurrentUserService currentUserService,
                           PaymentCallbackLogService paymentCallbackLogService) {
        this.readinessService = readinessService;
        this.commercialFacadeService = commercialFacadeService;
        this.currentUserService = currentUserService;
        this.paymentCallbackLogService = paymentCallbackLogService;
    }

    @GetMapping("/readiness")
    public ReadinessResponse readiness() {
        requireAdmin();
        return readinessService.check();
    }

    @GetMapping("/orders")
    public List<OrderResponse> listOrders(@RequestParam(required = false) String status) {
        requireAdmin();
        return commercialFacadeService.listRecentAdminOrders(status);
    }

    @PostMapping("/orders/{orderId}/close")
    public OrderResponse closeOrder(@PathVariable String orderId,
                                    @Valid @RequestBody AdminCloseOrderRequest request) {
        requireAdmin();
        return commercialFacadeService.adminCloseOrder(orderId, request.getReason());
    }

    @PostMapping("/orders/{orderId}/grant-paid")
    public OrderResponse grantPaidOrder(@PathVariable String orderId,
                                        @Valid @RequestBody AdminGrantOrderRequest request) {
        requireAdmin();
        return commercialFacadeService.adminGrantPaidOrder(orderId, request.getTransactionId());
    }

    @GetMapping("/payment-callback-logs")
    public List<PaymentCallbackLogResponse> listPaymentCallbackLogs(@RequestParam(required = false) String orderId,
                                                                    @RequestParam(required = false) String status) {
        requireAdmin();
        return paymentCallbackLogService.listRecent(orderId, status);
    }

    private void requireAdmin() {
        if (!commercialFacadeService.isAdmin(currentUserService.requireCurrentUserId())) {
            throw new SecurityException("仅管理员可访问");
        }
    }
}
