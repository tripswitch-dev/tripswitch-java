package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Input for creating a router. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateRouterInput(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("mode") RouterMode mode,
        @JsonProperty("enabled") Boolean enabled,
        @JsonProperty("metadata") Map<String, String> metadata
) {
    public CreateRouterInput(String name, RouterMode mode) {
        this(name, null, mode, null, null);
    }

    public static Builder builder(String name, RouterMode mode) {
        return new Builder(name, mode);
    }

    public static class Builder {
        private final String name;
        private final RouterMode mode;
        private String description;
        private Boolean enabled;
        private Map<String, String> metadata;

        Builder(String name, RouterMode mode) { this.name = name; this.mode = mode; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder metadata(Map<String, String> v) { this.metadata = v; return this; }

        public CreateRouterInput build() {
            return new CreateRouterInput(name, description, mode, enabled, metadata);
        }
    }
}
