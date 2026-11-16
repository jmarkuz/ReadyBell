package org.readybell.platform;

import org.junit.jupiter.api.Test;
import org.readybell.platform.auth.InMemoryTokenRevocationStore;
import org.readybell.platform.auth.TokenSessions;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenSessionsTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final MutableClock clock = new MutableClock();
    private final TokenSessions sessions =
            new TokenSessions(new InMemoryTokenRevocationStore(clock), clock, Duration.ofHours(30));

    private Jwt token(String jti, String sid) {
        var builder = Jwt.withTokenValue("t-" + jti).header("alg", "HS256").subject("bob").jti(jti)
                .issuedAt(clock.instant()).expiresAt(clock.instant().plus(Duration.ofHours(1)));
        if (sid != null) builder.claim("sid", sid);
        return builder.build();
    }

    @Test
    void aFreshTokenIsUsable() {
        sessions.assertUsable(token("a", "s1"));
    }

    @Test
    void signOutEndsEveryTokenOfTheChainButNotOtherChains() {
        var first = token("a", "s1");
        var refreshed = token("b", "s1");
        var otherDevice = token("c", "s2");

        sessions.signOut(refreshed);

        assertThatThrownBy(() -> sessions.assertUsable(first)).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> sessions.assertUsable(refreshed)).isInstanceOf(BadJwtException.class);
        sessions.assertUsable(otherDevice);
    }

    @Test
    void aRefreshedTokenKeepsWorkingForAShortOverlapThenStops() {
        var old = token("a", "s1");

        assertThat(sessions.allowRefresh(old)).isTrue();
        sessions.retire(old);

        sessions.assertUsable(old);                                    // requests already in flight still succeed
        clock.advance(Duration.ofSeconds(61));
        assertThatThrownBy(() -> sessions.assertUsable(old)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void aRetiredTokenUsedToRefreshAgainEndsTheWholeChain() {
        var old = token("a", "s1");
        var current = token("b", "s1");
        assertThat(sessions.allowRefresh(old)).isTrue();
        sessions.retire(old);
        clock.advance(Duration.ofMinutes(5));

        assertThat(sessions.allowRefresh(old)).isFalse();             // the thief (or the owner) replays the old token
        assertThatThrownBy(() -> sessions.assertUsable(current)).isInstanceOf(BadJwtException.class);   // and the chain is over
        assertThat(sessions.allowRefresh(current)).isFalse();
    }

    @Test
    void refreshingDuringTheOverlapIsNotMistakenForTheft() {
        var old = token("a", "s1");
        assertThat(sessions.allowRefresh(old)).isTrue();
        sessions.retire(old);
        clock.advance(Duration.ofSeconds(10));                         // a second tab refreshes with the same token

        assertThat(sessions.allowRefresh(old)).isTrue();
    }

    @Test
    void tokensWithoutASessionIdAreTheirOwnChain() {
        var legacy = token("legacy-1", null);

        assertThat(TokenSessions.sessionIdOf(legacy)).isEqualTo("legacy-1");
        sessions.signOut(legacy);
        assertThatThrownBy(() -> sessions.assertUsable(legacy)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void revocationsAreForgottenOnceNoTokenCouldStillBeUsed() {
        var t = token("a", "s1");
        sessions.signOut(t);
        clock.advance(Duration.ofHours(31));

        sessions.assertUsable(t);                                      // the token itself expired long ago anyway
    }
}
