package ru.sapa.gadalka_backend.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.exception.RateLimitExceededException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter для AI-запросов на основе алгоритма Token Bucket.
 *
 * <p>Каждый пользователь получает персональное "ведро" с токенами:
 * <ul>
 *   <li>Ёмкость: {@value #MAX_REQUESTS} токенов</li>
 *   <li>Восполнение: {@value #MAX_REQUESTS} токенов за {@value #PERIOD_MINUTES} минут (плавно, ~1 токен/мин)</li>
 * </ul>
 *
 * <p>Используется {@link Refill#greedy} — токены восстанавливаются непрерывно,
 * а не пакетом раз в период. Это даёт плавный пользовательский опыт:
 * легитимный пользователь практически никогда не видит ограничений,
 * а спамер упирается в лимит почти сразу.
 *
 * <p>Хранилище: {@link ConcurrentHashMap} в памяти — достаточно для одного инстанса.
 * При масштабировании на несколько инстансов заменить на Bucket4j + Redis.
 */
@Slf4j
@Service
public class AiRateLimitService {

    private static final int MAX_REQUESTS = 10;
    private static final int PERIOD_MINUTES = 10;

    /** userId → персональное ведро */
    private final ConcurrentHashMap<Long, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Проверяет лимит для пользователя и списывает 1 токен.
     *
     * @param userId идентификатор пользователя
     * @throws RateLimitExceededException если токены исчерпаны
     */
    public void checkLimit(Long userId) {
        Bucket bucket = buckets.computeIfAbsent(userId, this::createBucket);
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded: userId={}, лимит={} запросов за {} минут", userId, MAX_REQUESTS, PERIOD_MINUTES);
            throw new RateLimitExceededException("Звёзды говорят: вы слишком торопитесь. Попробуйте чуть позже");
        }
    }

    private Bucket createBucket(Long userId) {
        Bandwidth limit = Bandwidth.classic(MAX_REQUESTS, Refill.greedy(MAX_REQUESTS, Duration.ofMinutes(PERIOD_MINUTES)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
