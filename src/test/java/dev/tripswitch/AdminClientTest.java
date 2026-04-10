package dev.tripswitch;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tripswitch.admin.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AdminClientTest {

    private MockWebServer server;
    private AdminClient adminClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (adminClient != null) {
            adminClient.close();
        }
        server.shutdown();
    }

    private String baseUrl() {
        return server.url("/").toString().replaceAll("/$", "");
    }

    private AdminClient newClient() {
        adminClient = AdminClient.builder()
                .apiKey("eb_admin_test")
                .baseUrl(baseUrl())
                .build();
        return adminClient;
    }

    // ---- Client Construction ----

    @Test
    void testBuilder() {
        adminClient = AdminClient.builder()
                .apiKey("eb_admin_test")
                .baseUrl("https://custom.api.dev")
                .build();
        assertNotNull(adminClient);
    }

    // ---- Projects ----

    @Test
    void testGetProject() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"project_id\":\"proj_123\",\"name\":\"Test Project\"}"));

        Project project = newClient().getProject("proj_123");
        assertEquals("proj_123", project.id());
        assertEquals("Test Project", project.name());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertEquals("/v1/projects/proj_123", req.getPath());
        assertEquals("Bearer eb_admin_test", req.getHeader("Authorization"));
    }

    @Test
    void testCreateProject() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"project_id\":\"proj_new\",\"name\":\"my-project\"}"));

        Project project = newClient().createProject(new CreateProjectInput("my-project"));
        assertEquals("proj_new", project.id());
        assertEquals("my-project", project.name());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", req.getMethod());
        assertEquals("/v1/projects", req.getPath());
        assertTrue(req.getBody().readUtf8().contains("\"name\":\"my-project\""));
    }

    @Test
    void testUpdateProject() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"project_id\":\"proj_123\",\"name\":\"Updated Name\"}"));

        Project project = newClient().updateProject("proj_123",
                UpdateProjectInput.builder().name("Updated Name").build());
        assertEquals("Updated Name", project.name());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("PATCH", req.getMethod());
        assertEquals("/v1/projects/proj_123", req.getPath());
    }

    @Test
    void testListProjects() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"projects\":[{\"project_id\":\"p1\",\"name\":\"one\"},{\"project_id\":\"p2\",\"name\":\"two\"}]}"));

        ListProjectsResponse result = newClient().listProjects();
        assertEquals(2, result.projects().size());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertEquals("/v1/projects", req.getPath());
    }

    @Test
    void testDeleteProject_Success() throws Exception {
        // GET to verify name
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"project_id\":\"proj_123\",\"name\":\"prod-payments\"}"));
        // DELETE
        server.enqueue(new MockResponse().setResponseCode(204));

        newClient().deleteProject("proj_123",
                DeleteProjectOptions.builder("prod-payments").build());

        RecordedRequest getReq = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", getReq.getMethod());
        RecordedRequest delReq = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("DELETE", delReq.getMethod());
    }

    @Test
    void testDeleteProject_MissingConfirmName() {
        assertThrows(TripSwitchException.class, () ->
                newClient().deleteProject("proj_123",
                        DeleteProjectOptions.builder(null).build()));
    }

    @Test
    void testDeleteProject_WrongName() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"project_id\":\"proj_123\",\"name\":\"prod-payments\"}"));

        assertThrows(TripSwitchException.class, () ->
                newClient().deleteProject("proj_123",
                        DeleteProjectOptions.builder("wrong-name").build()));
    }

    // ---- Breakers ----

    @Test
    void testCreateBreaker() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(201)
                .setBody("{\"breaker\":{\"id\":\"b_456\",\"name\":\"api-latency\",\"kind\":\"p95\",\"op\":\"gt\",\"threshold\":500.0},\"router_ids\":[\"r_789\"]}"));

        Breaker breaker = newClient().createBreaker("proj_123",
                new CreateBreakerInput("api-latency", "latency_ms", BreakerKind.P95, BreakerOp.GT, 500));
        assertEquals("b_456", breaker.id());
        assertEquals(List.of("r_789"), breaker.routerIds());
        assertEquals("api-latency", breaker.name());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", req.getMethod());
        assertEquals("/v1/projects/proj_123/breakers", req.getPath());
    }

    @Test
    void testGetBreaker() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"breaker\":{\"id\":\"b_456\",\"name\":\"test-breaker\"},\"router_ids\":[\"r_789\"]}"));

        Breaker breaker = newClient().getBreaker("proj_123", "b_456");
        assertEquals("b_456", breaker.id());
        assertEquals(List.of("r_789"), breaker.routerIds());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertEquals("/v1/projects/proj_123/breakers/b_456", req.getPath());
    }

    @Test
    void testUpdateBreaker() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"breaker\":{\"id\":\"b_456\",\"name\":\"test\",\"threshold\":0.75},\"router_ids\":[\"r_789\"]}"));

        Breaker breaker = newClient().updateBreaker("proj_123", "b_456",
                UpdateBreakerInput.builder().threshold(0.75).build());
        assertEquals(0.75, breaker.threshold(), 0.001);
        assertEquals(List.of("r_789"), breaker.routerIds());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("PATCH", req.getMethod());
        assertEquals("/v1/projects/proj_123/breakers/b_456", req.getPath());
    }

    @Test
    void testDeleteBreaker() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        newClient().deleteBreaker("proj_123", "b_456");

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("DELETE", req.getMethod());
        assertEquals("/v1/projects/proj_123/breakers/b_456", req.getPath());
    }

    @Test
    void testListBreakers() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"breakers\":[{\"id\":\"b1\",\"name\":\"one\"},{\"id\":\"b2\",\"name\":\"two\"}],\"count\":2}"));

        ListBreakersResponse result = newClient().listBreakers("proj_123", new ListParams(10));
        assertEquals(2, result.breakers().size());
        assertEquals(2, result.count());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().contains("limit=10"));
    }

    @Test
    void testListBreakers_WithCursor() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"breakers\":[],\"count\":0}"));

        newClient().listBreakers("proj_123", new ListParams("cursor_abc", 20));

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertTrue(req.getPath().contains("cursor=cursor_abc"));
        assertTrue(req.getPath().contains("limit=20"));
    }

    @Test
    void testSyncBreakers() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"id\":\"b1\",\"name\":\"synced\"}]"));

        List<Breaker> result = newClient().syncBreakers("proj_123",
                new SyncBreakersInput(List.of(
                        new CreateBreakerInput("synced", "metric", BreakerKind.ERROR_RATE, BreakerOp.GT, 0.5))));
        assertEquals(1, result.size());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("PUT", req.getMethod());
    }

    @Test
    void testGetBreakerState() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"breaker_id\":\"b_456\",\"state\":\"closed\",\"allow_rate\":1.0}"));

        BreakerState state = newClient().getBreakerState("proj_123", "b_456");
        assertEquals("b_456", state.breakerId());
        assertEquals("closed", state.state());
        assertEquals(1.0, state.allowRate());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertEquals("/v1/projects/proj_123/breakers/b_456/state", req.getPath());
    }

    @Test
    void testBatchGetBreakerStates() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"breaker_id\":\"b1\",\"state\":\"closed\",\"allow_rate\":1.0},"
                        + "{\"breaker_id\":\"b2\",\"state\":\"open\",\"allow_rate\":0.0}]"));

        List<BreakerState> states = newClient().batchGetBreakerStates("proj_123",
                new BatchGetBreakerStatesInput(List.of("b1", "b2"), null));
        assertEquals(2, states.size());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", req.getMethod());
        assertEquals("/v1/projects/proj_123/breakers/state:batch", req.getPath());
    }

    // ---- Routers ----

    @Test
    void testCreateRouter() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(201)
                .setBody("{\"id\":\"r_789\",\"name\":\"canary-router\",\"mode\":\"canary\"}"));

        Router router = newClient().createRouter("proj_123",
                new CreateRouterInput("canary-router", RouterMode.CANARY));
        assertEquals("r_789", router.id());
        assertEquals(RouterMode.CANARY, router.mode());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", req.getMethod());
        assertEquals("/v1/projects/proj_123/routers", req.getPath());
    }

    @Test
    void testGetRouter() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"r_789\",\"name\":\"test-router\",\"mode\":\"static\"}"));

        Router router = newClient().getRouter("proj_123", "r_789");
        assertEquals("r_789", router.id());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertEquals("/v1/projects/proj_123/routers/r_789", req.getPath());
    }

    @Test
    void testUpdateRouter() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"r_789\",\"name\":\"updated\",\"mode\":\"weighted\"}"));

        Router router = newClient().updateRouter("proj_123", "r_789",
                UpdateRouterInput.builder().name("updated").build());
        assertEquals("updated", router.name());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("PATCH", req.getMethod());
    }

    @Test
    void testDeleteRouter() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        newClient().deleteRouter("proj_123", "r_789");

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("DELETE", req.getMethod());
        assertEquals("/v1/projects/proj_123/routers/r_789", req.getPath());
    }

    @Test
    void testListRouters() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"routers\":[{\"id\":\"r1\",\"name\":\"one\",\"mode\":\"static\"}]}"));

        ListRoutersResponse result = newClient().listRouters("proj_123", new ListParams(10));
        assertEquals(1, result.routers().size());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
    }

    @Test
    void testLinkBreaker() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        newClient().linkBreaker("proj_123", "r_789", new LinkBreakerInput("b_456"));

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", req.getMethod());
        assertEquals("/v1/projects/proj_123/routers/r_789/breakers", req.getPath());
        assertTrue(req.getBody().readUtf8().contains("\"breaker_id\":\"b_456\""));
    }

    @Test
    void testUnlinkBreaker() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        newClient().unlinkBreaker("proj_123", "r_789", "b_456");

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("DELETE", req.getMethod());
        assertEquals("/v1/projects/proj_123/routers/r_789/breakers/b_456", req.getPath());
    }

    // ---- Events ----

    @Test
    void testListEvents() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"events\":[{\"id\":\"e1\",\"from_state\":\"closed\",\"to_state\":\"open\"},"
                        + "{\"id\":\"e2\",\"from_state\":\"open\",\"to_state\":\"half_open\"}],\"returned\":2}"));

        ListEventsResponse result = newClient().listEvents("proj_123",
                ListEventsParams.builder().breakerId("b_456").limit(10).build());
        assertEquals(2, result.events().size());
        assertEquals("closed", result.events().get(0).fromState());
        assertEquals("open", result.events().get(0).toState());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().contains("breaker_id=b_456"));
    }

    // ---- Notification Channels ----

    @Test
    void testCreateNotificationChannel() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(201)
                .setBody("{\"id\":\"ch_abc\",\"name\":\"alerts\",\"channel\":\"slack\","
                        + "\"events\":[\"trip\",\"recover\"],\"enabled\":true}"));

        NotificationChannel channel = newClient().createNotificationChannel("proj_123",
                new CreateNotificationChannelInput("alerts", NotificationChannelType.SLACK,
                        Map.of("webhook_url", "https://hooks.slack.com/..."),
                        List.of(NotificationEventType.TRIP, NotificationEventType.RECOVER), true));
        assertEquals("ch_abc", channel.id());
        assertEquals(NotificationChannelType.SLACK, channel.channel());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", req.getMethod());
        assertEquals("/v1/projects/proj_123/notification-channels", req.getPath());
    }

    @Test
    void testGetNotificationChannel() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"ch_abc\",\"name\":\"alerts\",\"channel\":\"slack\"}"));

        NotificationChannel channel = newClient().getNotificationChannel("proj_123", "ch_abc");
        assertEquals("ch_abc", channel.id());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertEquals("/v1/projects/proj_123/notification-channels/ch_abc", req.getPath());
    }

    @Test
    void testUpdateNotificationChannel() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"ch_abc\",\"name\":\"updated\",\"channel\":\"slack\"}"));

        NotificationChannel channel = newClient().updateNotificationChannel("proj_123", "ch_abc",
                UpdateNotificationChannelInput.builder().name("updated").build());
        assertEquals("updated", channel.name());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("PATCH", req.getMethod());
    }

    @Test
    void testDeleteNotificationChannel() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        newClient().deleteNotificationChannel("proj_123", "ch_abc");

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("DELETE", req.getMethod());
        assertEquals("/v1/projects/proj_123/notification-channels/ch_abc", req.getPath());
    }

    @Test
    void testListNotificationChannels() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"channels\":[{\"id\":\"ch1\",\"name\":\"one\",\"channel\":\"slack\"}],\"next_cursor\":null}"));

        ListNotificationChannelsResponse result = newClient().listNotificationChannels("proj_123", new ListParams(10));
        assertEquals(1, result.channels().size());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
    }

    @Test
    void testTestNotificationChannel() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        newClient().testNotificationChannel("proj_123", "ch_abc");

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", req.getMethod());
        assertEquals("/v1/projects/proj_123/notification-channels/ch_abc/test", req.getPath());
    }

    // ---- Project Keys ----

    @Test
    void testListProjectKeys() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"keys\":[{\"id\":\"k1\",\"name\":\"default\",\"key_prefix\":\"eb_pk_\"}],\"count\":1}"));

        ListProjectKeysResponse result = newClient().listProjectKeys("proj_123");
        assertEquals(1, result.keys().size());
        assertEquals("eb_pk_", result.keys().get(0).keyPrefix());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertEquals("/v1/projects/proj_123/keys", req.getPath());
    }

    @Test
    void testCreateProjectKey() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(201)
                .setBody("{\"id\":\"k_new\",\"name\":\"my-key\",\"key\":\"eb_pk_full_key\",\"key_prefix\":\"eb_pk_\",\"message\":\"Save this key\"}"));

        CreateProjectKeyResponse result = newClient().createProjectKey("proj_123",
                new CreateProjectKeyInput("my-key"));
        assertEquals("k_new", result.id());
        assertEquals("eb_pk_full_key", result.key());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", req.getMethod());
        assertEquals("/v1/projects/proj_123/keys", req.getPath());
    }

    @Test
    void testDeleteProjectKey() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        newClient().deleteProjectKey("proj_123", "k_123");

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("DELETE", req.getMethod());
        assertEquals("/v1/projects/proj_123/keys/k_123", req.getPath());
    }

    // ---- Metadata ----

    @Test
    void testUpdateBreakerMetadata() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        newClient().updateBreakerMetadata("proj_123", "b_456",
                Map.of("region", "us-east-1", "team", "payments"));

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("PATCH", req.getMethod());
        assertEquals("/v1/projects/proj_123/breakers/b_456/metadata", req.getPath());
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"region\":\"us-east-1\""));
    }

    @Test
    void testUpdateRouterMetadata() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        newClient().updateRouterMetadata("proj_123", "r_789",
                Map.of("env", "production"));

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("PATCH", req.getMethod());
        assertEquals("/v1/projects/proj_123/routers/r_789/metadata", req.getPath());
    }

    // ---- Workspaces ----

    @Test
    void testListWorkspaces() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"workspaces\":[{\"id\":\"ws_1\",\"name\":\"default\",\"slug\":\"default\",\"org_id\":\"org_1\",\"inserted_at\":\"2024-01-01T00:00:00Z\"}]}"));

        ListWorkspacesResponse result = newClient().listWorkspaces();
        assertEquals(1, result.workspaces().size());
        assertEquals("ws_1", result.workspaces().get(0).id());
        assertEquals("default", result.workspaces().get(0).name());
        assertEquals("org_1", result.workspaces().get(0).orgId());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertEquals("/v1/workspaces", req.getPath());
    }

    @Test
    void testCreateWorkspace() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(201)
                .setBody("{\"id\":\"ws_2\",\"name\":\"staging\",\"slug\":\"staging\",\"org_id\":\"org_1\",\"inserted_at\":\"2024-06-01T00:00:00Z\"}"));

        Workspace ws = newClient().createWorkspace(new CreateWorkspaceInput("staging", "staging"));
        assertEquals("ws_2", ws.id());
        assertEquals("staging", ws.name());
        assertEquals("staging", ws.slug());
        assertEquals("org_1", ws.orgId());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", req.getMethod());
        assertEquals("/v1/workspaces", req.getPath());
    }

    @Test
    void testGetWorkspace() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"ws_1\",\"name\":\"default\",\"slug\":\"default\",\"org_id\":\"org_1\",\"inserted_at\":\"2024-01-01T00:00:00Z\"}"));

        Workspace ws = newClient().getWorkspace("ws_1");
        assertEquals("ws_1", ws.id());
        assertEquals("org_1", ws.orgId());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertEquals("/v1/workspaces/ws_1", req.getPath());
    }

    @Test
    void testUpdateWorkspace() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"ws_1\",\"name\":\"renamed\",\"slug\":\"default\",\"org_id\":\"org_1\",\"inserted_at\":\"2024-01-01T00:00:00Z\"}"));

        Workspace ws = newClient().updateWorkspace("ws_1",
                UpdateWorkspaceInput.builder().name("renamed").build());
        assertEquals("renamed", ws.name());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("PATCH", req.getMethod());
        assertEquals("/v1/workspaces/ws_1", req.getPath());
        assertTrue(req.getBody().readUtf8().contains("\"name\":\"renamed\""));
    }

    @Test
    void testDeleteWorkspace() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        newClient().deleteWorkspace("ws_1");

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("DELETE", req.getMethod());
        assertEquals("/v1/workspaces/ws_1", req.getPath());
    }

    // ---- Error Handling ----

    @Test
    void testError_404_NotFoundException() {
        server.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"not_found\",\"message\":\"Breaker not found\"}"));

        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                newClient().getBreaker("proj_123", "nonexistent"));
        assertEquals(404, ex.getStatus());
        assertEquals("not_found", ex.getCode());
    }

    @Test
    void testError_401_UnauthorizedException() {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Invalid API key\"}"));

        assertThrows(UnauthorizedException.class, () ->
                newClient().getProject("proj_123"));
    }

    @Test
    void testError_403_ForbiddenException() {
        server.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Forbidden\"}"));

        assertThrows(ForbiddenException.class, () ->
                newClient().getProject("proj_123"));
    }

    @Test
    void testError_429_RateLimitedException() {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "30")
                .setBody("{\"message\":\"Rate limit exceeded\"}"));

        RateLimitedException ex = assertThrows(RateLimitedException.class, () ->
                newClient().getProject("proj_123"));
        assertNotNull(ex.getRetryAfter());
        assertEquals(Duration.ofSeconds(30), ex.getRetryAfter());
    }

    @Test
    void testError_409_ConflictException() {
        server.enqueue(new MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Already exists\"}"));

        assertThrows(ConflictException.class, () ->
                newClient().createBreaker("proj_123",
                        new CreateBreakerInput("dup", "m", BreakerKind.ERROR_RATE, BreakerOp.GT, 0.5)));
    }

    @Test
    void testError_422_ValidationException() {
        server.enqueue(new MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Invalid threshold\"}"));

        assertThrows(ValidationException.class, () ->
                newClient().createBreaker("proj_123",
                        new CreateBreakerInput("bad", "m", BreakerKind.ERROR_RATE, BreakerOp.GT, -1)));
    }

    @Test
    void testError_400_ValidationException() {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Bad request\"}"));

        assertThrows(ValidationException.class, () ->
                newClient().getProject("proj_123"));
    }

    @Test
    void testError_500_ServerFaultException() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Internal server error\"}"));

        assertThrows(ServerFaultException.class, () ->
                newClient().getProject("proj_123"));
    }

    @Test
    void testError_Transport_ConnectionRefused() {
        adminClient = AdminClient.builder()
                .apiKey("test")
                .baseUrl("http://localhost:1")
                .build();

        assertThrows(TransportException.class, () ->
                adminClient.getProject("proj_123"));
    }

    // ---- Request Options ----

    @Test
    void testRequestOptions_IdempotencyKey() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"project_id\":\"proj_123\",\"name\":\"test\"}"));

        newClient().getProject("proj_123",
                RequestOptions.builder().idempotencyKey("idem_456").build());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("idem_456", req.getHeader("Idempotency-Key"));
    }

    @Test
    void testRequestOptions_RequestId() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"project_id\":\"proj_123\",\"name\":\"test\"}"));

        newClient().getProject("proj_123",
                RequestOptions.builder().requestId("trace_123").build());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("trace_123", req.getHeader("X-Request-ID"));
    }

    @Test
    void testRequestOptions_CustomHeaders() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"project_id\":\"proj_123\",\"name\":\"test\"}"));

        newClient().getProject("proj_123",
                RequestOptions.builder()
                        .header("X-Custom", "value")
                        .header("X-Another", "header")
                        .build());

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("value", req.getHeader("X-Custom"));
        assertEquals("header", req.getHeader("X-Another"));
    }

    @Test
    void testRequestOptions_Timeout() {
        // Server delays response longer than timeout
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"project_id\":\"proj_123\",\"name\":\"test\"}")
                .setBodyDelay(2, TimeUnit.SECONDS));

        assertThrows(TransportException.class, () ->
                newClient().getProject("proj_123",
                        RequestOptions.builder().timeout(Duration.ofMillis(100)).build()));
    }
}
