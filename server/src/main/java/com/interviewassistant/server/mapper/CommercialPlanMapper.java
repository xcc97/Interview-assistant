package com.interviewassistant.server.mapper;

import com.interviewassistant.server.entity.CommercialPlan;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommercialPlanMapper {
    CommercialPlan selectById(@Param("id") String id);

    CommercialPlan selectByCode(@Param("code") String code);

    List<CommercialPlan> selectAll();

    List<CommercialPlan> selectByStatusOrderByPriceAsc(@Param("status") String status);

    long countAll();

    int insert(CommercialPlan plan);

    int update(CommercialPlan plan);
}
