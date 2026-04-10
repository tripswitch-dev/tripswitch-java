package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Input for updating a workspace. All fields are optional. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateWorkspaceInput(
        @JsonProperty("name") String name,
        @JsonProperty("slug") String slug
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String slug;

        public Builder name(String name) { this.name = name; return this; }
        public Builder slug(String slug) { this.slug = slug; return this; }

        public UpdateWorkspaceInput build() {
            return new UpdateWorkspaceInput(name, slug);
        }
    }
}
