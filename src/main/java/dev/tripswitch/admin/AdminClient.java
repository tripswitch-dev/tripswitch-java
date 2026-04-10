package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Client for the Tripswitch admin API.
 *
 * <pre>{@code
 * AdminClient client = AdminClient.builder()
 *     .apiKey("eb_admin_...")
 *     .build();
 *
 * Project project = client.getProject("proj_123");
 * }</pre>
 */
public class AdminClient implements Closeable {

    private static final String DEFAULT_BASE_URL = "https://api.tripswitch.dev";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    private final String apiKey;
    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final boolean ownsClient;

    private AdminClient(String apiKey, String baseUrl, OkHttpClient httpClient, boolean ownsClient) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.ownsClient = ownsClient;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static Builder builder() {
        return new Builder();
    }

    // ---- HTTP plumbing ----

    private <T> T execute(String method, String path, Object body, Map<String, String> query,
                          RequestOptions opts, Class<T> responseType) {
        return executeInternal(method, path, body, query, opts, responseType, null);
    }

    private <T> T execute(String method, String path, Object body, Map<String, String> query,
                          RequestOptions opts, TypeReference<T> typeRef) {
        return executeInternal(method, path, body, query, opts, null, typeRef);
    }

    @SuppressWarnings("unchecked")
    private <T> T executeInternal(String method, String path, Object body, Map<String, String> query,
                                   RequestOptions opts, Class<T> responseType, TypeReference<T> typeRef) {
        // Build URL
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(baseUrl + path)).newBuilder();
        if (query != null) {
            query.forEach(urlBuilder::addQueryParameter);
        }

        // Build request body
        RequestBody requestBody = null;
        if (body != null) {
            try {
                byte[] jsonBytes = mapper.writeValueAsBytes(body);
                requestBody = RequestBody.create(jsonBytes, JSON_MEDIA_TYPE);
            } catch (Exception e) {
                throw new TransportException("failed to serialize request body", e);
            }
        }

        if (requestBody == null && ("DELETE".equals(method) || "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
            requestBody = RequestBody.create(new byte[0], null);
        }

        // Build request
        Request.Builder reqBuilder = new Request.Builder()
                .url(urlBuilder.build())
                .method(method, requestBody)
                .header("Accept", "application/json");

        if (apiKey != null && !apiKey.isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        if (opts != null) {
            if (opts.getIdempotencyKey() != null) {
                reqBuilder.header("Idempotency-Key", opts.getIdempotencyKey());
            }
            if (opts.getRequestId() != null) {
                reqBuilder.header("X-Request-ID", opts.getRequestId());
            }
            opts.getHeaders().forEach(reqBuilder::header);
        }

        // Apply per-request timeout
        OkHttpClient callClient = httpClient;
        if (opts != null && opts.getTimeout() != null) {
            callClient = httpClient.newBuilder()
                    .callTimeout(opts.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .build();
        }

        // Execute
        try (Response response = callClient.newCall(reqBuilder.build()).execute()) {
            byte[] responseBody = response.body() != null ? response.body().bytes() : new byte[0];

            if (response.code() >= 400) {
                throw parseError(response, responseBody);
            }

            if (responseType == Void.class || responseType == void.class) {
                return null;
            }

            if (responseBody.length == 0) {
                return null;
            }

            if (responseType != null) {
                return mapper.readValue(responseBody, responseType);
            } else if (typeRef != null) {
                return mapper.readValue(responseBody, typeRef);
            }
            return null;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new TransportException(e.getMessage(), e);
        }
    }

    private ApiException parseError(Response response, byte[] body) {
        String code = null;
        String message = null;

        try {
            var errorResp = mapper.readValue(body, ErrorResponse.class);
            code = errorResp.code();
            message = errorResp.message();
        } catch (Exception ignored) {
        }

        if (message == null || message.isEmpty()) {
            message = response.message();
        }

        String requestId = response.header("X-Request-ID");
        String retryAfter = response.header("Retry-After");

        return ApiException.forStatus(response.code(), code, message, requestId, body, retryAfter);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorResponse(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message
    ) {}

    // Wrapper for single breaker responses
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BreakerResponse(
            @JsonProperty("breaker") Breaker breaker,
            @JsonProperty("router_ids") List<String> routerIds
    ) {}

    // ---- Projects ----

    public ListProjectsResponse listProjects(RequestOptions... opts) {
        return execute("GET", "/v1/projects", null, null, firstOr(opts), ListProjectsResponse.class);
    }

    public Project createProject(CreateProjectInput input, RequestOptions... opts) {
        return execute("POST", "/v1/projects", input, null, firstOr(opts), Project.class);
    }

    public Project getProject(String projectId, RequestOptions... opts) {
        return execute("GET", "/v1/projects/" + projectId, null, null, firstOr(opts), Project.class);
    }

    public Project updateProject(String projectId, UpdateProjectInput input, RequestOptions... opts) {
        return execute("PATCH", "/v1/projects/" + projectId, input, null, firstOr(opts), Project.class);
    }

    public void deleteProject(String projectId, DeleteProjectOptions options) {
        if (options == null || options.getConfirmName() == null || options.getConfirmName().isEmpty()) {
            throw new dev.tripswitch.TripSwitchException("tripswitch: delete confirmation failed: confirmName is required");
        }

        // Fetch project to verify name
        RequestOptions reqOpts = options.getRequestOptions();
        Project project = getProject(projectId, reqOpts != null ? new RequestOptions[]{reqOpts} : new RequestOptions[0]);

        if (!project.name().equals(options.getConfirmName())) {
            throw new dev.tripswitch.TripSwitchException(
                    "tripswitch: delete confirmation failed: project name \"" + project.name() +
                    "\" does not match confirmation name \"" + options.getConfirmName() + "\"");
        }

        execute("DELETE", "/v1/projects/" + projectId, null, null, reqOpts, Void.class);
    }

    public IngestSecretRotation rotateIngestSecret(String projectId, RequestOptions... opts) {
        return execute("POST", "/v1/projects/" + projectId + "/ingest_secret/rotate", null, null,
                firstOr(opts), IngestSecretRotation.class);
    }

    // ---- Breakers ----

    public ListBreakersResponse listBreakers(String projectId, ListParams params, RequestOptions... opts) {
        Map<String, String> query = new java.util.HashMap<>();
        if (params.cursor() != null && !params.cursor().isEmpty()) query.put("cursor", params.cursor());
        if (params.limit() > 0) query.put("limit", String.valueOf(params.limit()));
        return execute("GET", "/v1/projects/" + projectId + "/breakers", null, query.isEmpty() ? null : query,
                firstOr(opts), ListBreakersResponse.class);
    }

    public Breaker createBreaker(String projectId, CreateBreakerInput input, RequestOptions... opts) {
        BreakerResponse resp = execute("POST", "/v1/projects/" + projectId + "/breakers", input, null,
                firstOr(opts), BreakerResponse.class);
        return unwrapBreaker(resp);
    }

    public Breaker getBreaker(String projectId, String breakerId, RequestOptions... opts) {
        BreakerResponse resp = execute("GET", "/v1/projects/" + projectId + "/breakers/" + breakerId, null, null,
                firstOr(opts), BreakerResponse.class);
        return unwrapBreaker(resp);
    }

    public Breaker updateBreaker(String projectId, String breakerId, UpdateBreakerInput input, RequestOptions... opts) {
        BreakerResponse resp = execute("PATCH", "/v1/projects/" + projectId + "/breakers/" + breakerId, input, null,
                firstOr(opts), BreakerResponse.class);
        return unwrapBreaker(resp);
    }

    private static Breaker unwrapBreaker(BreakerResponse resp) {
        if (resp != null && resp.breaker() != null && resp.routerIds() != null) {
            return resp.breaker().withRouterIds(resp.routerIds());
        }
        return resp != null ? resp.breaker() : null;
    }

    public void deleteBreaker(String projectId, String breakerId, RequestOptions... opts) {
        execute("DELETE", "/v1/projects/" + projectId + "/breakers/" + breakerId, null, null,
                firstOr(opts), Void.class);
    }

    public List<Breaker> syncBreakers(String projectId, SyncBreakersInput input, RequestOptions... opts) {
        return execute("PUT", "/v1/projects/" + projectId + "/breakers", input, null,
                firstOr(opts), new TypeReference<>() {});
    }

    public BreakerState getBreakerState(String projectId, String breakerId, RequestOptions... opts) {
        return execute("GET", "/v1/projects/" + projectId + "/breakers/" + breakerId + "/state", null, null,
                firstOr(opts), BreakerState.class);
    }

    public List<BreakerState> batchGetBreakerStates(String projectId, BatchGetBreakerStatesInput input, RequestOptions... opts) {
        return execute("POST", "/v1/projects/" + projectId + "/breakers/state:batch", input, null,
                firstOr(opts), new TypeReference<>() {});
    }

    // ---- Routers ----

    public ListRoutersResponse listRouters(String projectId, ListParams params, RequestOptions... opts) {
        Map<String, String> query = new java.util.HashMap<>();
        if (params.cursor() != null && !params.cursor().isEmpty()) query.put("cursor", params.cursor());
        if (params.limit() > 0) query.put("limit", String.valueOf(params.limit()));
        return execute("GET", "/v1/projects/" + projectId + "/routers", null, query.isEmpty() ? null : query,
                firstOr(opts), ListRoutersResponse.class);
    }

    public Router createRouter(String projectId, CreateRouterInput input, RequestOptions... opts) {
        return execute("POST", "/v1/projects/" + projectId + "/routers", input, null,
                firstOr(opts), Router.class);
    }

    public Router getRouter(String projectId, String routerId, RequestOptions... opts) {
        return execute("GET", "/v1/projects/" + projectId + "/routers/" + routerId, null, null,
                firstOr(opts), Router.class);
    }

    public Router updateRouter(String projectId, String routerId, UpdateRouterInput input, RequestOptions... opts) {
        return execute("PATCH", "/v1/projects/" + projectId + "/routers/" + routerId, input, null,
                firstOr(opts), Router.class);
    }

    public void deleteRouter(String projectId, String routerId, RequestOptions... opts) {
        execute("DELETE", "/v1/projects/" + projectId + "/routers/" + routerId, null, null,
                firstOr(opts), Void.class);
    }

    public void linkBreaker(String projectId, String routerId, LinkBreakerInput input, RequestOptions... opts) {
        execute("POST", "/v1/projects/" + projectId + "/routers/" + routerId + "/breakers", input, null,
                firstOr(opts), Void.class);
    }

    public void unlinkBreaker(String projectId, String routerId, String breakerId, RequestOptions... opts) {
        execute("DELETE", "/v1/projects/" + projectId + "/routers/" + routerId + "/breakers/" + breakerId, null, null,
                firstOr(opts), Void.class);
    }

    // ---- Events ----

    public ListEventsResponse listEvents(String projectId, ListEventsParams params, RequestOptions... opts) {
        Map<String, String> query = new java.util.HashMap<>();
        if (params.breakerId() != null && !params.breakerId().isEmpty()) query.put("breaker_id", params.breakerId());
        if (params.startTime() != null) query.put("start_time", params.startTime().toString());
        if (params.endTime() != null) query.put("end_time", params.endTime().toString());
        if (params.cursor() != null && !params.cursor().isEmpty()) query.put("cursor", params.cursor());
        if (params.limit() > 0) query.put("limit", String.valueOf(params.limit()));
        return execute("GET", "/v1/projects/" + projectId + "/events", null, query.isEmpty() ? null : query,
                firstOr(opts), ListEventsResponse.class);
    }

    // ---- Notification Channels ----

    public ListNotificationChannelsResponse listNotificationChannels(String projectId, ListParams params, RequestOptions... opts) {
        Map<String, String> query = new java.util.HashMap<>();
        if (params.cursor() != null && !params.cursor().isEmpty()) query.put("cursor", params.cursor());
        if (params.limit() > 0) query.put("limit", String.valueOf(params.limit()));
        return execute("GET", "/v1/projects/" + projectId + "/notification-channels", null, query.isEmpty() ? null : query,
                firstOr(opts), ListNotificationChannelsResponse.class);
    }

    public NotificationChannel createNotificationChannel(String projectId, CreateNotificationChannelInput input, RequestOptions... opts) {
        return execute("POST", "/v1/projects/" + projectId + "/notification-channels", input, null,
                firstOr(opts), NotificationChannel.class);
    }

    public NotificationChannel getNotificationChannel(String projectId, String channelId, RequestOptions... opts) {
        return execute("GET", "/v1/projects/" + projectId + "/notification-channels/" + channelId, null, null,
                firstOr(opts), NotificationChannel.class);
    }

    public NotificationChannel updateNotificationChannel(String projectId, String channelId,
                                                          UpdateNotificationChannelInput input, RequestOptions... opts) {
        return execute("PATCH", "/v1/projects/" + projectId + "/notification-channels/" + channelId, input, null,
                firstOr(opts), NotificationChannel.class);
    }

    public void deleteNotificationChannel(String projectId, String channelId, RequestOptions... opts) {
        execute("DELETE", "/v1/projects/" + projectId + "/notification-channels/" + channelId, null, null,
                firstOr(opts), Void.class);
    }

    public void testNotificationChannel(String projectId, String channelId, RequestOptions... opts) {
        execute("POST", "/v1/projects/" + projectId + "/notification-channels/" + channelId + "/test", null, null,
                firstOr(opts), Void.class);
    }

    // ---- Project Keys ----

    public ListProjectKeysResponse listProjectKeys(String projectId, RequestOptions... opts) {
        return execute("GET", "/v1/projects/" + projectId + "/keys", null, null,
                firstOr(opts), ListProjectKeysResponse.class);
    }

    public CreateProjectKeyResponse createProjectKey(String projectId, CreateProjectKeyInput input, RequestOptions... opts) {
        return execute("POST", "/v1/projects/" + projectId + "/keys", input, null,
                firstOr(opts), CreateProjectKeyResponse.class);
    }

    public void deleteProjectKey(String projectId, String keyId, RequestOptions... opts) {
        execute("DELETE", "/v1/projects/" + projectId + "/keys/" + keyId, null, null,
                firstOr(opts), Void.class);
    }

    // ---- Metadata ----

    public void updateBreakerMetadata(String projectId, String breakerId, Map<String, String> metadata, RequestOptions... opts) {
        execute("PATCH", "/v1/projects/" + projectId + "/breakers/" + breakerId + "/metadata", metadata, null,
                firstOr(opts), Void.class);
    }

    public void updateRouterMetadata(String projectId, String routerId, Map<String, String> metadata, RequestOptions... opts) {
        execute("PATCH", "/v1/projects/" + projectId + "/routers/" + routerId + "/metadata", metadata, null,
                firstOr(opts), Void.class);
    }

    // ---- Pagers ----

    public Pager<Breaker> breakerPager(String projectId, ListParams params, RequestOptions... opts) {
        RequestOptions reqOpts = firstOr(opts);
        return new Pager<>() {
            private List<Breaker> items;
            private int index;
            private boolean done;
            private dev.tripswitch.TripSwitchException error;
            private boolean started;
            private String cursor;

            @Override
            public boolean hasNext() {
                if (error != null) throw error;
                if (items != null && index < items.size()) return true;
                if (done) return false;
                try {
                    ListParams p = started ? new ListParams(cursor, params.limit()) : params;
                    started = true;
                    ListBreakersResponse resp = listBreakers(projectId, p, reqOpts != null ? new RequestOptions[]{reqOpts} : new RequestOptions[0]);
                    items = resp.breakers();
                    index = 0;
                    cursor = resp.nextCursor();
                    done = cursor == null || cursor.isEmpty();
                    return items != null && !items.isEmpty();
                } catch (dev.tripswitch.TripSwitchException e) {
                    error = e;
                    throw e;
                }
            }

            @Override
            public Breaker next() {
                if (items != null && index < items.size()) return items.get(index++);
                throw new java.util.NoSuchElementException();
            }

            @Override
            public dev.tripswitch.TripSwitchException getError() { return error; }
        };
    }

    public Pager<Router> routerPager(String projectId, ListParams params, RequestOptions... opts) {
        RequestOptions reqOpts = firstOr(opts);
        return new Pager<>() {
            private List<Router> items;
            private int index;
            private boolean done;
            private dev.tripswitch.TripSwitchException error;
            private boolean started;
            private String cursor;

            @Override
            public boolean hasNext() {
                if (error != null) throw error;
                if (items != null && index < items.size()) return true;
                if (done) return false;
                try {
                    ListParams p = started ? new ListParams(cursor, params.limit()) : params;
                    started = true;
                    ListRoutersResponse resp = listRouters(projectId, p, reqOpts != null ? new RequestOptions[]{reqOpts} : new RequestOptions[0]);
                    items = resp.routers();
                    index = 0;
                    cursor = resp.nextCursor();
                    done = cursor == null || cursor.isEmpty();
                    return items != null && !items.isEmpty();
                } catch (dev.tripswitch.TripSwitchException e) {
                    error = e;
                    throw e;
                }
            }

            @Override
            public Router next() {
                if (items != null && index < items.size()) return items.get(index++);
                throw new java.util.NoSuchElementException();
            }

            @Override
            public dev.tripswitch.TripSwitchException getError() { return error; }
        };
    }

    public Pager<Event> eventPager(String projectId, ListEventsParams params, RequestOptions... opts) {
        RequestOptions reqOpts = firstOr(opts);
        return new Pager<>() {
            private List<Event> items;
            private int index;
            private boolean done;
            private dev.tripswitch.TripSwitchException error;
            private boolean started;
            private String cursor;

            @Override
            public boolean hasNext() {
                if (error != null) throw error;
                if (items != null && index < items.size()) return true;
                if (done) return false;
                try {
                    ListEventsParams p = started
                            ? ListEventsParams.builder().breakerId(params.breakerId()).startTime(params.startTime())
                                .endTime(params.endTime()).cursor(cursor).limit(params.limit()).build()
                            : params;
                    started = true;
                    ListEventsResponse resp = listEvents(projectId, p, reqOpts != null ? new RequestOptions[]{reqOpts} : new RequestOptions[0]);
                    items = resp.events();
                    index = 0;
                    cursor = resp.nextCursor();
                    done = cursor == null || cursor.isEmpty();
                    return items != null && !items.isEmpty();
                } catch (dev.tripswitch.TripSwitchException e) {
                    error = e;
                    throw e;
                }
            }

            @Override
            public Event next() {
                if (items != null && index < items.size()) return items.get(index++);
                throw new java.util.NoSuchElementException();
            }

            @Override
            public dev.tripswitch.TripSwitchException getError() { return error; }
        };
    }

    public Pager<NotificationChannel> notificationChannelPager(String projectId, ListParams params, RequestOptions... opts) {
        RequestOptions reqOpts = firstOr(opts);
        return new Pager<>() {
            private List<NotificationChannel> items;
            private int index;
            private boolean done;
            private dev.tripswitch.TripSwitchException error;
            private boolean started;
            private String cursor;

            @Override
            public boolean hasNext() {
                if (error != null) throw error;
                if (items != null && index < items.size()) return true;
                if (done) return false;
                try {
                    ListParams p = started ? new ListParams(cursor, params.limit()) : params;
                    started = true;
                    ListNotificationChannelsResponse resp = listNotificationChannels(projectId, p, reqOpts != null ? new RequestOptions[]{reqOpts} : new RequestOptions[0]);
                    items = resp.channels();
                    index = 0;
                    cursor = resp.nextCursor();
                    done = cursor == null || cursor.isEmpty();
                    return items != null && !items.isEmpty();
                } catch (dev.tripswitch.TripSwitchException e) {
                    error = e;
                    throw e;
                }
            }

            @Override
            public NotificationChannel next() {
                if (items != null && index < items.size()) return items.get(index++);
                throw new java.util.NoSuchElementException();
            }

            @Override
            public dev.tripswitch.TripSwitchException getError() { return error; }
        };
    }

    // ---- Workspaces ----

    public ListWorkspacesResponse listWorkspaces(RequestOptions... opts) {
        return execute("GET", "/v1/workspaces", null, null, firstOr(opts), ListWorkspacesResponse.class);
    }

    public Workspace createWorkspace(CreateWorkspaceInput input, RequestOptions... opts) {
        return execute("POST", "/v1/workspaces", input, null, firstOr(opts), Workspace.class);
    }

    public Workspace getWorkspace(String workspaceId, RequestOptions... opts) {
        return execute("GET", "/v1/workspaces/" + workspaceId, null, null, firstOr(opts), Workspace.class);
    }

    public Workspace updateWorkspace(String workspaceId, UpdateWorkspaceInput input, RequestOptions... opts) {
        return execute("PATCH", "/v1/workspaces/" + workspaceId, input, null, firstOr(opts), Workspace.class);
    }

    public void deleteWorkspace(String workspaceId, RequestOptions... opts) {
        execute("DELETE", "/v1/workspaces/" + workspaceId, null, null, firstOr(opts), Void.class);
    }

    // ---- Lifecycle ----

    @Override
    public void close() {
        if (ownsClient) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    // ---- Helpers ----

    private static RequestOptions firstOr(RequestOptions[] opts) {
        return opts != null && opts.length > 0 ? opts[0] : null;
    }

    // ---- Builder ----

    public static class Builder {
        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private OkHttpClient httpClient;

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder httpClient(OkHttpClient httpClient) { this.httpClient = httpClient; return this; }

        public AdminClient build() {
            boolean owns = false;
            OkHttpClient client = this.httpClient;
            if (client == null) {
                client = new OkHttpClient.Builder()
                        .callTimeout(DEFAULT_TIMEOUT)
                        .build();
                owns = true;
            }
            return new AdminClient(apiKey, baseUrl, client, owns);
        }
    }
}
