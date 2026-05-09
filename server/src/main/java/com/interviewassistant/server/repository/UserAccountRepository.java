package com.interviewassistant.server.repository;

import com.interviewassistant.server.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByPhone(String phone);
}
