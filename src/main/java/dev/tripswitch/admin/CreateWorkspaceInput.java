package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Input for creating a workspace. */
public record CreateWorkspaceInput(
        @JsonProperty("name") String name,
        @JsonProperty("slug") String slug
) {}
