package dev.tripswitch.admin;

/** Thrown when authentication fails (401). */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String code, String message, String requestId, byte[] body) {
        super(401, code, message, requestId, body, null);
    }
}
