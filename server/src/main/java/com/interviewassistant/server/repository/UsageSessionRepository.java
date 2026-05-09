package com.interviewassistant.server.repository;

import com.interviewassistant.server.entity.UsageSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsageSessionRepository extends JpaRepository<UsageSession, String> {
    List<UsageSession> findByUserIdOrderByStartedAtDesc(String userId);
}
