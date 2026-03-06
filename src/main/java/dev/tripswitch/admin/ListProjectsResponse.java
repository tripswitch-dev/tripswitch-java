package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Response from listing projects. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ListProjectsResponse(
        @JsonProperty("projects") List<Project> projects
) {}
