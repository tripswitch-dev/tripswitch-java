package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Input for creating a project. */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record CreateProjectInput(
        @JsonProperty("name") String name,
        @JsonProperty("workspace_id") String workspaceId
) {
    public CreateProjectInput(String name) {
        this(name, null);
    }
}
