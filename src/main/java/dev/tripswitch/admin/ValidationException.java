package dev.tripswitch.admin;

/** Thrown when input validation fails (400 or 422). */
public class ValidationException extends ApiException {
    public ValidationException(String code, String message, String requestId, byte[] body) {
        super(400, code, message, requestId, body, null);
    }
}
