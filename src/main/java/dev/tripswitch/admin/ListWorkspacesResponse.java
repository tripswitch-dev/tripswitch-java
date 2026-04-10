package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Response from listing workspaces. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ListWorkspacesResponse(
        @JsonProperty("workspaces") List<Workspace> workspaces
) {}
