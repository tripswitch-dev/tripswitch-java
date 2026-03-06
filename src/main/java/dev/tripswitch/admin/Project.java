package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A Tripswitch project. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Project(
        @JsonProperty("project_id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("slack_webhook_url") String slackWebhookUrl,
        @JsonProperty("trace_id_url_template") String traceIdUrlTemplate,
        @JsonProperty("enable_signed_ingest") boolean enableSignedIngest
) {}
