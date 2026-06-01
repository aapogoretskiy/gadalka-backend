package ru.sapa.gadalka_backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
public class JwtService {

    private final Key key;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String userId) {
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000L)) // 24 часа
                .signWith(key)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(
                Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(token)
                        .getBody()
                        .getSubject()
        );
    }

    /**
     * Генерирует Admin JWT для входа в админ-панель.
     * Токен содержит claim "role"="ADMIN" и "telegramId" для двойной проверки в AdminFilter.
     * TTL — 8 часов (рабочая сессия администратора).
     */
    public String generateAdminToken(Long telegramId) {
        return Jwts.builder()
                .setSubject(telegramId.toString())
                .addClaims(Map.of("role", "ADMIN", "telegramId", telegramId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 28800000L)) // 8 часов
                .signWith(key)
                .compact();
    }

    /**
     * Извлекает claims из Admin JWT.
     * Используется в AdminFilter для проверки role и telegramId.
     */
    public Claims getAdminClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
