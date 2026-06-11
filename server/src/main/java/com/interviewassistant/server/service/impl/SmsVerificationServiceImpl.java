package com.interviewassistant.server.service.impl;

import com.interviewassistant.server.service.SmsVerificationService;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SmsVerificationServiceImpl implements SmsVerificationService {
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, SmsCodeRecord> codes = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> cooldowns = new ConcurrentHashMap<>();

    @Override
    public String sendCode(String phone) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime nextAllowedAt = cooldowns.get(phone);
        if (nextAllowedAt != null && now.isBefore(nextAllowedAt)) {
            long seconds = Duration.between(now, nextAllowedAt).toSeconds();
            throw new IllegalStateException("发送过于频繁，请 " + Math.max(1, seconds) + " 秒后重试");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codes.put(phone, new SmsCodeRecord(code, now.plus(CODE_TTL)));
        cooldowns.put(phone, now.plus(SEND_COOLDOWN));
        return code;
    }

    @Override
    public void verifyCode(String phone, String code) {
        SmsCodeRecord record = codes.get(phone);
        if (record == null) {
            throw new IllegalStateException("验证码不存在或已失效");
        }
        if (OffsetDateTime.now().isAfter(record.expiresAt())) {
            codes.remove(phone);
            throw new IllegalStateException("验证码已过期");
        }
        if (!record.code().equals(code)) {
            throw new IllegalArgumentException("验证码错误");
        }
        codes.remove(phone);
    }

    private record SmsCodeRecord(String code, OffsetDateTime expiresAt) {
    }
}
