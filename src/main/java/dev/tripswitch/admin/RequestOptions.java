package dev.tripswitch.admin;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Per-request configuration options. */
public final class RequestOptions {

    private final String idempotencyKey;
    private final Duration timeout;
    private final String requestId;
    private final Map<String, String> headers;

    private RequestOptions(String idempotencyKey, Duration timeout, String requestId, Map<String, String> headers) {
        this.idempotencyKey = idempotencyKey;
        this.timeout = timeout;
        this.requestId = requestId;
        this.headers = headers;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public Duration getTimeout() { return timeout; }
    public String getRequestId() { return requestId; }
    public Map<String, String> getHeaders() { return headers; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String idempotencyKey;
        private Duration timeout;
        private String requestId;
        private Map<String, String> headers;

        public Builder idempotencyKey(String key) { this.idempotencyKey = key; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }
        public Builder requestId(String id) { this.requestId = id; return this; }
        public Builder header(String key, String value) {
            if (this.headers == null) this.headers = new HashMap<>();
            this.headers.put(key, value);
            return this;
        }

        public RequestOptions build() {
            return new RequestOptions(idempotencyKey, timeout, requestId,
                    headers != null ? Map.copyOf(headers) : Map.of());
        }
    }
}
