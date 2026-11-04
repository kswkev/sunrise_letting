package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisRememberMeExchangeGateTest {

    private static final String SERIES = "A".repeat(22);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final RedisRememberMeExchangeGate gate = new RedisRememberMeExchangeGate(redis);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void theGateStaysShutWhenRedisFails() {
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenThrow(new RedisConnectionFailureException("Redis is down"));

        assertFalse(gate.tryEnter(SERIES), "signing out one request is better than a token used twice");

        verify(redis, description("the gate should ask for the value operations")).opsForValue();
        verify(values, description("the gate should have tried Redis")).setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(30)));
    }

    @Test
    void theFirstRequestGetsThrough() {
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        assertTrue(gate.tryEnter(SERIES), "nobody else holds the gate");

        verify(redis, description("the gate should ask for the value operations")).opsForValue();
        verify(values, description("the gate should shut for 30 seconds")).setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(30)));
    }

    @Test
    void aSecondRequestIsTurnedAway() {
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        assertFalse(gate.tryEnter(SERIES), "another request holds the gate");

        verify(redis, description("the gate should ask for the value operations")).opsForValue();
        verify(values, description("the gate should have tried Redis")).setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(30)));
    }

    @Test
    void noAnswerFromRedisCountsAsShut() {
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(null);

        assertFalse(gate.tryEnter(SERIES), "Redis gives no answer inside a pipeline or a transaction");

        verify(redis, description("the gate should ask for the value operations")).opsForValue();
        verify(values, description("the gate should have tried Redis")).setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(30)));
    }

    @Test
    void reopeningDoesNotThrowWhenRedisFails() {
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("Redis is down"));

        assertDoesNotThrow(() -> gate.reopen(SERIES), "the gate reopens by itself when its 30 seconds are up");

        verify(redis, description("the gate should have tried Redis")).delete(anyString());
    }
}
