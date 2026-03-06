package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Input for creating a project key. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateProjectKeyInput(
        @JsonProperty("name") String name
) {}
