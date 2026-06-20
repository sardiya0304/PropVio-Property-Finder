package com.propvio.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiry.days:7}")
    private int expiryDays;

    @Value("${jwt.expiry.remember-me.days:30}")
    private int rememberMeExpiryDays;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, boolean rememberMe) {
        int days = rememberMe ? rememberMeExpiryDays : expiryDays;
        return Jwts.builder()
            .claim("id", userId.toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + (long) days * 24 * 60 * 60 * 1000))
            .signWith(getKey())
            .compact();
    }

    public Long extractUserId(String token) {
        String id = parseClaims(token).get("id", String.class);
        if (id == null || id.startsWith("admin:")) return null;
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(getKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
