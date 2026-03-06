package dev.tripswitch.admin;

/** Thrown when the server returns a 5xx error. */
public class ServerFaultException extends ApiException {
    public ServerFaultException(int status, String code, String message, String requestId, byte[] body) {
        super(status, code, message, requestId, body, null);
    }
}
