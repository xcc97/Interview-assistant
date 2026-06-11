package com.interviewassistant.server.mapper;

import com.interviewassistant.server.entity.InterviewRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface InterviewRecordMapper {
    List<InterviewRecord> selectTop200ByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    List<InterviewRecord> selectByUserIdAndUsageSessionIdOrderByCreatedAtAsc(
        @Param("userId") String userId,
        @Param("usageSessionId") String usageSessionId
    );

    int deleteByUserIdAndUsageSessionId(
        @Param("userId") String userId,
        @Param("usageSessionId") String usageSessionId
    );

    int insert(InterviewRecord record);
}
