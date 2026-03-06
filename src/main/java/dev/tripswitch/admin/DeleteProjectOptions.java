package dev.tripswitch.admin;

/** Options for deleting a project. */
public final class DeleteProjectOptions {

    private final String confirmName;
    private final RequestOptions requestOptions;

    private DeleteProjectOptions(String confirmName, RequestOptions requestOptions) {
        this.confirmName = confirmName;
        this.requestOptions = requestOptions;
    }

    public String getConfirmName() { return confirmName; }
    public RequestOptions getRequestOptions() { return requestOptions; }

    public static Builder builder(String confirmName) {
        return new Builder(confirmName);
    }

    public static class Builder {
        private final String confirmName;
        private RequestOptions requestOptions;

        Builder(String confirmName) { this.confirmName = confirmName; }
        public Builder requestOptions(RequestOptions opts) { this.requestOptions = opts; return this; }

        public DeleteProjectOptions build() {
            return new DeleteProjectOptions(confirmName, requestOptions);
        }
    }
}
