package com.interviewassistant.server.mapper;

import com.interviewassistant.server.entity.UserAccount;
import org.apache.ibatis.annotations.Param;

public interface UserAccountMapper {
    UserAccount selectById(@Param("id") String id);

    UserAccount selectByPhone(@Param("phone") String phone);

    int insert(UserAccount user);

    int update(UserAccount user);
}
