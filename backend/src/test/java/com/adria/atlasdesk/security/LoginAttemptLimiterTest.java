package com.adria.atlasdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    @Test
    void limitsAnOrganizationAndEmailPairAndAllowsItAgainAfterSuccess() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
                Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.recordAttempt(41L, "Demo@AtlasDesk.test");
        }
        assertThatThrownBy(() -> limiter.recordAttempt(41L, "demo@atlasdesk.test"))
                .isInstanceOf(LoginAttemptLimiter.TooManyLoginAttemptsException.class)
                .satisfies(exception -> assertThat(
                        ((LoginAttemptLimiter.TooManyLoginAttemptsException) exception).getRetryAfterSeconds())
                        .isEqualTo(60));

        limiter.clear(41L, "demo@atlasdesk.test");
        limiter.recordAttempt(41L, "demo@atlasdesk.test");
        limiter.recordAttempt(42L, "demo@atlasdesk.test");
    }
}
