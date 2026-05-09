package com.interviewassistant.server.config;

import com.interviewassistant.server.entity.CommercialPlan;
import com.interviewassistant.server.entity.UserAccount;
import com.interviewassistant.server.repository.CommercialPlanRepository;
import com.interviewassistant.server.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class PlanDataInitializer {
    @Bean
    CommandLineRunner initCommercialPlans(CommercialPlanRepository repository,
                                          UserAccountRepository userAccountRepository,
                                          AssistantProperties assistantProperties) {
        return args -> {
            syncAdminRoles(userAccountRepository, assistantProperties);
            if (repository.count() > 0) {
                ensurePlansActive(repository);
                return;
            }

            repository.saveAll(List.of(
                buildPlan("trial-30", "新人试用包", 30, 7, new BigDecimal("9.90"), "适合快速体验核心功能", false),
                buildPlan("boost-300", "求职冲刺包", 300, 30, new BigDecimal("99.00"), "适合面试密集阶段，主推套餐", true),
                buildPlan("pro-800", "长期准备包", 800, 90, new BigDecimal("199.00"), "适合长期备战与多轮模拟", false)
            ));
        };
    }

    private CommercialPlan buildPlan(String code, String name, int totalMinutes, int validDays,
                                     BigDecimal price, String description, boolean featured) {
        CommercialPlan plan = new CommercialPlan();
        plan.setCode(code);
        plan.setName(name);
        plan.setTotalMinutes(totalMinutes);
        plan.setValidDays(validDays);
        plan.setPrice(price);
        plan.setDescription(description);
        plan.setFeatured(featured);
        plan.setStatus("ACTIVE");
        return plan;
    }

    private void ensurePlansActive(CommercialPlanRepository repository) {
        List<CommercialPlan> plans = repository.findAll();
        boolean changed = false;
        for (CommercialPlan plan : plans) {
            if (plan.getStatus() == null || plan.getStatus().isBlank()) {
                plan.setStatus("ACTIVE");
                changed = true;
            }
        }
        if (changed) {
            repository.saveAll(plans);
        }
    }

    private void syncAdminRoles(UserAccountRepository userAccountRepository, AssistantProperties assistantProperties) {
        String adminPhones = assistantProperties.getAdminPhones();
        if (adminPhones.isBlank()) {
            return;
        }
        for (String phone : adminPhones.split(",")) {
            String normalizedPhone = phone.trim();
            if (normalizedPhone.isBlank()) {
                continue;
            }
            userAccountRepository.findByPhone(normalizedPhone).ifPresent(user -> {
                if (!"ADMIN".equals(user.getRole())) {
                    user.setRole("ADMIN");
                    userAccountRepository.save(user);
                }
            });
        }
    }
}
