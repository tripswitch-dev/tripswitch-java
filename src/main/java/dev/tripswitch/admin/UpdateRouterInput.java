package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Input for updating a router. All fields are optional. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateRouterInput(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("mode") RouterMode mode,
        @JsonProperty("enabled") Boolean enabled,
        @JsonProperty("metadata") Map<String, String> metadata
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private RouterMode mode;
        private Boolean enabled;
        private Map<String, String> metadata;

        public Builder name(String v) { this.name = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder mode(RouterMode v) { this.mode = v; return this; }
        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder metadata(Map<String, String> v) { this.metadata = v; return this; }

        public UpdateRouterInput build() {
            return new UpdateRouterInput(name, description, mode, enabled, metadata);
        }
    }
}
