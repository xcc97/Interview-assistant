package com.interviewassistant.server.config;

import com.interviewassistant.server.service.CommercialFacadeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderMaintenanceScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrderMaintenanceScheduler.class);

    private final CommercialFacadeService commercialFacadeService;
    private final AssistantProperties assistantProperties;

    public OrderMaintenanceScheduler(CommercialFacadeService commercialFacadeService,
                                     AssistantProperties assistantProperties) {
        this.commercialFacadeService = commercialFacadeService;
        this.assistantProperties = assistantProperties;
    }

    @Scheduled(fixedDelayString = "${interview-assistant.payment.order-maintenance-fixed-delay-ms:60000}")
    public void closeExpiredPendingOrders() {
        int closedCount = commercialFacadeService.closeExpiredPendingOrders(
            assistantProperties.getPayment().getOrderTimeoutMinutes()
        );
        if (closedCount > 0) {
            log.info("Closed {} expired pending payment orders", closedCount);
        }
    }
}
