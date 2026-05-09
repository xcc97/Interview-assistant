package com.interviewassistant.server.repository;

import com.interviewassistant.server.entity.CommercialPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommercialPlanRepository extends JpaRepository<CommercialPlan, String> {
    List<CommercialPlan> findByStatusOrderByPriceAsc(String status);

    Optional<CommercialPlan> findByCode(String code);
}
