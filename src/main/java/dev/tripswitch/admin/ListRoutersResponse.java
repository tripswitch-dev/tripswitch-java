package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Response from listing routers. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ListRoutersResponse(
        @JsonProperty("routers") List<Router> routers
) {}
