package com.interviewassistant.server.service;

import com.interviewassistant.server.config.AssistantProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {
    private final AssistantProperties properties;

    public JwtTokenService(AssistantProperties properties) {
        this.properties = properties;
    }

    public String generateToken(String userId, String phone) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getJwtExpireSeconds());
        return Jwts.builder()
            .subject(userId)
            .claim("phone", phone)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(getSigningKey())
            .compact();
    }

    public String parseUserId(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return claims.getSubject();
    }

    public long getExpireSeconds() {
        return properties.getJwtExpireSeconds();
    }

    private SecretKey getSigningKey() {
        String secret = properties.getJwtSecret();
        byte[] bytes = decodeBase64SecretIfClearlyEncoded(secret);
        if (bytes == null) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            bytes = sha256(bytes);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    private byte[] decodeBase64SecretIfClearlyEncoded(String secret) {
        String normalized = secret == null ? "" : secret.trim();
        if (normalized.isEmpty() || normalized.length() % 4 != 0) {
            return null;
        }
        if (!normalized.matches("^[A-Za-z0-9+/]+={0,2}$")) {
            return null;
        }
        try {
            byte[] decoded = Decoders.BASE64.decode(normalized);
            return decoded.length >= 32 ? decoded : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
