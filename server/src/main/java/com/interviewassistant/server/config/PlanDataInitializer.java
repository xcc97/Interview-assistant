package com.interviewassistant.server.config;

import com.interviewassistant.server.entity.CommercialPlan;
import com.interviewassistant.server.entity.UserAccount;
import com.interviewassistant.server.mapper.CommercialPlanMapper;
import com.interviewassistant.server.mapper.UserAccountMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class PlanDataInitializer {
    @Bean
    CommandLineRunner initCommercialPlans(CommercialPlanMapper commercialPlanMapper,
                                          UserAccountMapper userAccountMapper,
                                          AssistantProperties assistantProperties) {
        return args -> {
            syncAdminRoles(userAccountMapper, assistantProperties);
            if (commercialPlanMapper.countAll() > 0) {
                ensurePlansActive(commercialPlanMapper);
                return;
            }

            List.of(
                buildPlan("trial-30", "新人试用包", 30, 7, new BigDecimal("9.90"), "适合快速体验核心功能", false),
                buildPlan("boost-300", "求职冲刺包", 400, 30, new BigDecimal("99.00"), "适合面试密集阶段，主推套餐", true),
                buildPlan("pro-800", "长期准备包", 1000, 90, new BigDecimal("199.00"), "适合长期备战与多轮模拟", false)
            ).forEach(commercialPlanMapper::insert);
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
        plan.prePersist();
        return plan;
    }

    private void ensurePlansActive(CommercialPlanMapper commercialPlanMapper) {
        List<CommercialPlan> plans = commercialPlanMapper.selectAll();
        for (CommercialPlan plan : plans) {
            boolean changed = false;
            if (plan.getStatus() == null || plan.getStatus().isBlank()) {
                plan.setStatus("ACTIVE");
                changed = true;
            }
            if ("boost-300".equals(plan.getCode()) && plan.getTotalMinutes() != 400) {
                plan.setTotalMinutes(400);
                changed = true;
            }
            if ("pro-800".equals(plan.getCode()) && plan.getTotalMinutes() != 1000) {
                plan.setTotalMinutes(1000);
                changed = true;
            }
            if (changed) {
                commercialPlanMapper.update(plan);
            }
        }
    }

    private void syncAdminRoles(UserAccountMapper userAccountMapper, AssistantProperties assistantProperties) {
        String adminPhones = assistantProperties.getAdminPhones();
        if (adminPhones.isBlank()) {
            return;
        }
        for (String phone : adminPhones.split(",")) {
            String normalizedPhone = phone.trim();
            if (normalizedPhone.isBlank()) {
                continue;
            }
            UserAccount user = userAccountMapper.selectByPhone(normalizedPhone);
            if (user != null && !"ADMIN".equals(user.getRole())) {
                user.setRole("ADMIN");
                userAccountMapper.update(user);
            }
        }
    }
}
