package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Input for updating a project. All fields are optional. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateProjectInput(
        @JsonProperty("name") String name,
        @JsonProperty("slack_webhook_url") String slackWebhookUrl,
        @JsonProperty("trace_id_url_template") String traceIdUrlTemplate,
        @JsonProperty("enable_signed_ingest") Boolean enableSignedIngest
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String slackWebhookUrl;
        private String traceIdUrlTemplate;
        private Boolean enableSignedIngest;

        public Builder name(String name) { this.name = name; return this; }
        public Builder slackWebhookUrl(String url) { this.slackWebhookUrl = url; return this; }
        public Builder traceIdUrlTemplate(String template) { this.traceIdUrlTemplate = template; return this; }
        public Builder enableSignedIngest(boolean enable) { this.enableSignedIngest = enable; return this; }

        public UpdateProjectInput build() {
            return new UpdateProjectInput(name, slackWebhookUrl, traceIdUrlTemplate, enableSignedIngest);
        }
    }
}
