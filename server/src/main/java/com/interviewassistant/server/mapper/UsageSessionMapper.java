package com.interviewassistant.server.mapper;

import com.interviewassistant.server.entity.UsageSession;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UsageSessionMapper {
    UsageSession selectById(@Param("id") String id);

    List<UsageSession> selectByUserIdOrderByStartedAtDesc(@Param("userId") String userId);

    int insert(UsageSession session);

    int update(UsageSession session);
}
