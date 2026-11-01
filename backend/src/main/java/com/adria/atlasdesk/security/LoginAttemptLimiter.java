package com.adria.atlasdesk.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final int MAX_TRACKED_IDENTITIES = 4096;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<String, AttemptWindow> attempts = new LinkedHashMap<>();

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void recordAttempt(Long organizationId, String email) {
        Instant now = clock.instant();
        String key = key(organizationId, email);
        AttemptWindow current = attempts.get(key);
        if (current == null || !current.startedAt().plus(WINDOW).isAfter(now)) {
            current = new AttemptWindow(now, 0);
        }

        if (current.count() >= MAX_ATTEMPTS) {
            long retryAfterSeconds = Math.max(1,
                    Duration.between(now, current.startedAt().plus(WINDOW)).toSeconds());
            throw new TooManyLoginAttemptsException(retryAfterSeconds);
        }

        if (!attempts.containsKey(key) && attempts.size() >= MAX_TRACKED_IDENTITIES) {
            attempts.remove(attempts.keySet().iterator().next());
        }
        attempts.put(key, new AttemptWindow(current.startedAt(), current.count() + 1));
    }

    public synchronized void clear(Long organizationId, String email) {
        attempts.remove(key(organizationId, email));
    }

    private static String key(Long organizationId, String email) {
        return organizationId + ":" + email.trim().toLowerCase(Locale.ROOT);
    }

    private record AttemptWindow(Instant startedAt, int count) {
    }

    public static final class TooManyLoginAttemptsException extends AuthenticationException {

        private final long retryAfterSeconds;

        private TooManyLoginAttemptsException(long retryAfterSeconds) {
            super("Too many login attempts");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
