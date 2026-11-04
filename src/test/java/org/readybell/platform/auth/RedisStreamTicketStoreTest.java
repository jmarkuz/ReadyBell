package org.readybell.platform.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisStreamTicketStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisStreamTicketStore store;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        store = new RedisStreamTicketStore(redis, "t:", Clock.systemUTC());
    }

    @Test
    void issueStoresTheJwtUnderThePrefixedTicketWithATtl() {
        String ticket = store.issue("jwt", Duration.ofSeconds(30));

        verify(values).set("t:" + ticket, "jwt", Duration.ofSeconds(30));
    }

    @Test
    void consumeReadsAndDeletesInOneStep() {
        when(values.getAndDelete("t:abc")).thenReturn("jwt");

        assertThat(store.consume("abc")).contains("jwt");
        verify(values).getAndDelete("t:abc");
    }

    @Test
    void unknownTicketIsEmpty() {
        when(values.getAndDelete(anyString())).thenReturn(null);

        assertThat(store.consume("nope")).isEmpty();
    }

    @Test
    void blankOrNullTicketNeverReachesRedis() {
        assertThat(store.consume(null)).isEmpty();
        assertThat(store.consume("  ")).isEmpty();
        verify(values, never()).getAndDelete(anyString());
    }

    @Test
    void redisDownOnIssueFallsBackToAPerNodeTicket() {
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                .when(values).set(anyString(), eq("jwt"), org.mockito.ArgumentMatchers.any(Duration.class));

        String ticket = store.issue("jwt", Duration.ofSeconds(30));

        assertThat(store.consume(ticket)).contains("jwt");        // served from the local fallback...
        assertThat(store.consume(ticket)).isEmpty();              // ...and still single use
    }

    @Test
    void redisDownOnConsumeIsAnEmptyResultNotAnError() {
        when(values.getAndDelete(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(store.consume("abc")).isEmpty();
    }
}
