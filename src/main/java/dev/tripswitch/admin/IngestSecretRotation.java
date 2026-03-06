package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Result of rotating an ingest secret. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IngestSecretRotation(
        @JsonProperty("ingest_secret") String ingestSecret
) {}
