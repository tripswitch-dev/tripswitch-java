package dev.tripswitch.admin;

/** Thrown when the request is forbidden (403). */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String code, String message, String requestId, byte[] body) {
        super(403, code, message, requestId, body, null);
    }
}
