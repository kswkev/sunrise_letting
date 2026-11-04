package com.sunriseletting.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps the gate in Redis, so it holds across replicas. It stores a hash of the series for a short while and no
 * token. If Redis can't answer the gate stays shut: signing out one request is better than a token used twice.
 */
@Component
class RedisRememberMeExchangeGate implements RememberMeExchangeGate {

    private static final Logger log = LoggerFactory.getLogger(RedisRememberMeExchangeGate.class);

    private static final String KEY_PREFIX = "sunrise:web:remember-me-exchange:";

    /** Longer than identity's time-outs, and than a browser takes to store the new cookie while other requests are in flight. */
    private static final Duration SHUT_FOR = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;

    RedisRememberMeExchangeGate(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryEnter(String series) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(series), "1", SHUT_FOR));
        } catch (DataAccessException e) {
            log.warn("Remember-me exchange gate failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public void reopen(String series) {
        try {
            redis.delete(key(series));
        } catch (DataAccessException e) {
            log.warn("Remember-me exchange gate failed: {}", e.getClass().getSimpleName());
        }
    }

    private String key(String series) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(series.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
