package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Input for creating a project. */
public record CreateProjectInput(
        @JsonProperty("name") String name
) {}
