package com.interviewassistant.server.service;

import com.interviewassistant.server.config.AssistantProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
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
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException ex) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
