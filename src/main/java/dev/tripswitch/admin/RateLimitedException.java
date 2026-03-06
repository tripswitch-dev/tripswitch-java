package dev.tripswitch.admin;

import java.time.Duration;

/** Thrown when the request is rate limited (429). */
public class RateLimitedException extends ApiException {
    public RateLimitedException(String code, String message, String requestId, byte[] body, Duration retryAfter) {
        super(429, code, message, requestId, body, retryAfter);
    }
}
