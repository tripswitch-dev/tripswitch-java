package dev.tripswitch.admin;

import dev.tripswitch.TripSwitchException;

import java.time.Duration;

/**
 * Represents an error response from the Tripswitch API.
 * Use {@code instanceof} to check for specific error types.
 */
public class ApiException extends TripSwitchException {

    private final int status;
    private final String code;
    private final String requestId;
    private final byte[] body;
    private final Duration retryAfter;

    public ApiException(int status, String code, String message, String requestId, byte[] body, Duration retryAfter) {
        super(formatMessage(status, code, message));
        this.status = status;
        this.code = code;
        this.requestId = requestId;
        this.body = body;
        this.retryAfter = retryAfter;
    }

    private static String formatMessage(int status, String code, String message) {
        if (code != null && !code.isEmpty()) {
            return "tripswitch: " + message + " (status " + status + ", code " + code + ")";
        }
        return "tripswitch: " + message + " (status " + status + ")";
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
    public String getRequestId() { return requestId; }
    public byte[] getBody() { return body; }
    public Duration getRetryAfter() { return retryAfter; }

    /** Maps an HTTP status code to the appropriate exception subclass. */
    public static ApiException forStatus(int status, String code, String message, String requestId, byte[] body, String retryAfterHeader) {
        Duration retryAfter = parseRetryAfter(retryAfterHeader);

        return switch (status) {
            case 404 -> new NotFoundException(code, message, requestId, body);
            case 401 -> new UnauthorizedException(code, message, requestId, body);
            case 403 -> new ForbiddenException(code, message, requestId, body);
            case 429 -> new RateLimitedException(code, message, requestId, body, retryAfter);
            case 409 -> new ConflictException(code, message, requestId, body);
            case 400, 422 -> new ValidationException(code, message, requestId, body);
            default -> {
                if (status >= 500 && status < 600) {
                    yield new ServerFaultException(status, code, message, requestId, body);
                }
                yield new ApiException(status, code, message, requestId, body, retryAfter);
            }
        };
    }

    private static Duration parseRetryAfter(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            int seconds = Integer.parseInt(value);
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
