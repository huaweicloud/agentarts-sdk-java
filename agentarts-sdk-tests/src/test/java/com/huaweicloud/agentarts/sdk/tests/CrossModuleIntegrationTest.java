package com.huaweicloud.agentarts.sdk.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.agentarts.sdk.core.Constants;
import com.huaweicloud.agentarts.sdk.core.PingStatus;
import com.huaweicloud.agentarts.sdk.core.signer.V11Signer;
import com.huaweicloud.agentarts.sdk.integration.agentscope.message.MessageConverter;
import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeRequestContext;
import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeRuntimeHost;
import com.huaweicloud.agentarts.sdk.memory.MemoryClient;
import com.huaweicloud.agentarts.sdk.memory.model.TextMessage;
import com.huaweicloud.agentarts.sdk.memory.model.ToolCallMessage;
import com.huaweicloud.agentarts.sdk.memory.model.ToolResultMessage;
import com.huaweicloud.agentarts.sdk.runtime.AgentArtsRuntimeApp;
import com.huaweicloud.agentarts.sdk.runtime.context.AgentArtsRuntimeContext;
import com.huaweicloud.agentarts.sdk.runtime.context.RequestContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.State;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-module integration tests for the AgentArts Java SDK.
 *
 * <p>Tests verify interactions between multiple SDK modules:
 * core + runtime, runtime + identity, memory + integration, CLI + core,
 * and the full-stack end-to-end pipeline.</p>
 */
class CrossModuleIntegrationTest {

    private static final ObjectMapper MAPPER = com.huaweicloud.agentarts.sdk.core.util.JsonUtils.MAPPER;

    // ========================
    // Core + Runtime: Full HTTP pipeline
    // ========================

    @Nested
    @DisplayName("Core + Runtime integration")
    class CoreRuntimeTests {

        @Test
        void runtimeAppStartsAndRespondsToPing() throws Exception {
            AgentArtsRuntimeApp app = new AgentArtsRuntimeApp();
            app.setPingHandler(() -> PingStatus.HEALTHY);
            app.run(0); // random port

            try {
                int port = app.getPort();
                assertTrue(port > 0, "Server should be listening on a port");

                // Make HTTP request using Vert.x WebClient
                Vertx vertx = Vertx.vertx();
                WebClient client = WebClient.create(vertx);

                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> body = new AtomicReference<>();
                AtomicReference<Integer> statusCode = new AtomicReference<>();

                client.get(port, "localhost", "/ping")
                        .send()
                        .onSuccess(resp -> {
                            statusCode.set(resp.statusCode());
                            body.set(resp.bodyAsString());
                            latch.countDown();
                        })
                        .onFailure(err -> latch.countDown());

                assertTrue(latch.await(5, TimeUnit.SECONDS), "Request should complete within 5s");
                assertEquals(200, statusCode.get());

                JsonNode json = MAPPER.readTree(body.get());
                assertEquals("Healthy", json.get("status").asText());
                assertTrue(json.has("time_of_last_update"));

                client.close();
                vertx.close();
            } finally {
                app.stop();
            }
        }

        @Test
        void runtimeAppHandlesInvocationWithConstantsHeaders() throws Exception {
            AgentArtsRuntimeApp app = new AgentArtsRuntimeApp();
            app.setEntrypoint((Map<String, Object> payload, RequestContext ctx) -> {
                return Map.of(
                        "echo", payload.getOrDefault("msg", ""),
                        "sessionId", ctx.getSessionId() != null ? ctx.getSessionId() : "",
                        "requestId", ctx.getRequestId() != null ? ctx.getRequestId() : ""
                );
            });
            app.setPingHandler(() -> PingStatus.HEALTHY);
            app.run(0);

            try {
                int port = app.getPort();
                Vertx vertx = Vertx.vertx();
                WebClient client = WebClient.create(vertx);

                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> body = new AtomicReference<>();
                AtomicReference<String> sessionHeader = new AtomicReference<>();

                String payload = "{\"msg\":\"cross-module-test\"}";
                client.post(port, "localhost", "/invocations")
                        .putHeader("Content-Type", "application/json")
                        .putHeader(Constants.SESSION_HEADER, "test-session-123")
                        .sendBuffer(Buffer.buffer(payload))
                        .onSuccess(resp -> {
                            body.set(resp.bodyAsString());
                            sessionHeader.set(resp.getHeader(Constants.SESSION_HEADER));
                            latch.countDown();
                        })
                        .onFailure(err -> latch.countDown());

                assertTrue(latch.await(5, TimeUnit.SECONDS));

                JsonNode json = MAPPER.readTree(body.get());
                assertEquals("cross-module-test", json.get("echo").asText());
                assertEquals("test-session-123", json.get("sessionId").asText());
                assertEquals("test-session-123", sessionHeader.get());

                client.close();
                vertx.close();
            } finally {
                app.stop();
            }
        }

        @Test
        void runtimeAppReturns400ForInvalidJson() throws Exception {
            AgentArtsRuntimeApp app = new AgentArtsRuntimeApp();
            app.setEntrypoint((Map<String, Object> payload) -> payload);
            app.setPingHandler(() -> PingStatus.HEALTHY);
            app.run(0);

            try {
                int port = app.getPort();
                Vertx vertx = Vertx.vertx();
                WebClient client = WebClient.create(vertx);

                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<Integer> statusCode = new AtomicReference<>();
                AtomicReference<String> body = new AtomicReference<>();

                client.post(port, "localhost", "/invocations")
                        .putHeader("Content-Type", "application/json")
                        .sendBuffer(Buffer.buffer("not-json"))
                        .onSuccess(resp -> {
                            statusCode.set(resp.statusCode());
                            body.set(resp.bodyAsString());
                            latch.countDown();
                        })
                        .onFailure(err -> latch.countDown());

                assertTrue(latch.await(5, TimeUnit.SECONDS));
                assertEquals(400, statusCode.get());

                JsonNode json = MAPPER.readTree(body.get());
                assertEquals("Invalid JSON payload", json.get("error").asText());

                client.close();
                vertx.close();
            } finally {
                app.stop();
            }
        }

        @Test
        void v11SignerProducesValidAuthorizationForRuntimeEndpoint() {
            String region = Constants.DEFAULT_REGION;
            String endpoint = Constants.getControlPlaneEndpoint(region);

            assertNotNull(endpoint);
            assertTrue(endpoint.startsWith("https://"));
            assertTrue(endpoint.contains(region));

            // Sign a request
            V11Signer signer = new V11Signer("test-ak", "test-sk", region);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("host", "agentarts." + region + ".myhuaweicloud.com");
            headers.put("content-type", "application/json");

            Map<String, String> signedHeaders = signer.sign(
                    "POST", "/v1/agent-runtime/invoke",
                    Map.of(), headers
            );

            assertTrue(signedHeaders.containsKey("Authorization"));
            assertTrue(signedHeaders.get("Authorization").startsWith("V11-HMAC-SHA256"));
            assertTrue(signedHeaders.get("Authorization").contains("Credential=test-ak/"));
            assertTrue(signedHeaders.containsKey("x-sdk-date"));
        }
    }

    // ========================
    // Runtime Context: ThreadLocal propagation
    // ========================

    @Nested
    @DisplayName("Runtime context ThreadLocal propagation")
    class RuntimeContextTests {

        @AfterEach
        void cleanup() {
            AgentArtsRuntimeContext.clear();
        }

        @Test
        void requestContextToRuntimeContextRoundTrip() {
            RequestContext rc = new RequestContext("req-abc", "sess-xyz", "user-123", "token-456");

            AgentArtsRuntimeContext.fromRequestContext(rc);

            assertEquals("req-abc", AgentArtsRuntimeContext.getRequestId());
            assertEquals("sess-xyz", AgentArtsRuntimeContext.getSessionId());
            assertEquals("user-123", AgentArtsRuntimeContext.getUserId());
            assertEquals("token-456", AgentArtsRuntimeContext.getWorkloadAccessToken());

            // Round-trip back
            RequestContext rt = AgentArtsRuntimeContext.toRequestContext();
            assertEquals("req-abc", rt.getRequestId());
            assertEquals("sess-xyz", rt.getSessionId());
            assertEquals("user-123", rt.getUserId());
            assertEquals("token-456", rt.getWorkloadAccessToken());
        }

        @Test
        void runtimeContextClearRemovesAllFields() {
            AgentArtsRuntimeContext.setSessionId("s1");
            AgentArtsRuntimeContext.setRequestId("r1");
            AgentArtsRuntimeContext.setUserId("u1");
            AgentArtsRuntimeContext.setWorkloadAccessToken("t1");
            AgentArtsRuntimeContext.setOAuth2CallbackUrl("https://callback");
            AgentArtsRuntimeContext.setUserToken("ut1");
            AgentArtsRuntimeContext.setOAuth2CustomState("state1");

            AgentArtsRuntimeContext.clear();

            assertNull(AgentArtsRuntimeContext.getSessionId());
            assertNull(AgentArtsRuntimeContext.getRequestId());
            assertNull(AgentArtsRuntimeContext.getUserId());
            assertNull(AgentArtsRuntimeContext.getWorkloadAccessToken());
            assertNull(AgentArtsRuntimeContext.getOAuth2CallbackUrl());
            assertNull(AgentArtsRuntimeContext.getUserToken());
            assertNull(AgentArtsRuntimeContext.getOAuth2CustomState());
        }

        @Test
        void runtimeContextIsThreadIsolated() throws Exception {
            AgentArtsRuntimeContext.setSessionId("main-session");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> otherThreadSession = new AtomicReference<>("NOT_SET");

            Thread other = new Thread(() -> {
                otherThreadSession.set(AgentArtsRuntimeContext.getSessionId());
                latch.countDown();
            });
            other.start();
            assertTrue(latch.await(2, TimeUnit.SECONDS));

            assertEquals("main-session", AgentArtsRuntimeContext.getSessionId());
            assertNull(otherThreadSession.get(), "Other thread should not see main thread's context");
        }
    }

    // ========================
    // Memory + Integration: StateStore + MessageConverter
    // ========================

    @Nested
    @DisplayName("Memory + Integration cross-module")
    class MemoryIntegrationTests {

        @Test
        void memoryClientUsesConstantsRegion() {
            MemoryClient client = new MemoryClient(null, "test-key");
            // A null region must resolve through Constants.getRegion() (which falls
            // back to Constants.DEFAULT_REGION when no env var is set).
            assertEquals(Constants.getRegion(), client.getRegionName());
        }

        @Test
        void stateStoreAndMessageConverterShareModels() {
            // Use the stable AgentScope Session API for cross-module integration testing
            // (MemoryAgentStateStore requires real API credentials, tested separately)
            InMemoryAgentStateStore store = new InMemoryAgentStateStore();
            AgentscopeRequestContext context = new AgentscopeRequestContext(
                    "session-1", "user-1", "request-1", null);

            // Save and retrieve using agentscope State interface
            TestAgentState state = new TestAgentState("agent-1", 42);
            store.save(context.userId(), context.sessionId(), "agent_state", state);

            Optional<TestAgentState> retrieved = store.get(
                    context.userId(), context.sessionId(), "agent_state", TestAgentState.class);
            assertTrue(retrieved.isPresent());
            assertEquals("agent-1", retrieved.get().agentId);
            assertEquals(42, retrieved.get().step);

            // Also verify message conversion works with same model classes
            TextMessage textMsg = new TextMessage("user", "Hello from state");
            TextBlock block = MessageConverter.toTextBlock(textMsg);
            assertEquals("Hello from state", block.getText());

            TextMessage roundTripped = MessageConverter.toTextMessage(block, "user");
            assertEquals(textMsg.getContent(), roundTripped.getContent());
        }

        @Test
        void toolCallRoundTripAcrossModules() {
            // Create a tool call message (memory module)
            ToolCallMessage callMsg = new ToolCallMessage("call-1", "search", "{\"query\":\"test\"}");

            // Convert to agentscope ToolUseBlock (integration module)
            ToolUseBlock useBlock = MessageConverter.toToolUseBlock(callMsg);
            assertEquals("call-1", useBlock.getId());
            assertEquals("search", useBlock.getName());
            assertEquals("test", useBlock.getInput().get("query"));

            // Convert back
            ToolCallMessage roundTripped = MessageConverter.toToolCallMessage(useBlock);
            assertEquals("call-1", roundTripped.getId());
            assertEquals("search", roundTripped.getName());

            // Create tool result (memory module)
            ToolResultMessage resultMsg = new ToolResultMessage("call-1", "found 3 results");

            // Convert to agentscope ToolResultBlock (integration module)
            ToolResultBlock resultBlock = MessageConverter.toToolResultBlock(resultMsg);
            assertEquals("call-1", resultBlock.getId());

            // Convert back
            ToolResultMessage roundTrippedResult = MessageConverter.toToolResultMessage(resultBlock);
            assertEquals("call-1", roundTrippedResult.getToolCallId());
            assertEquals("found 3 results", roundTrippedResult.getContent());
        }
    }

    // ========================
    // Constants: Full endpoint chain
    // ========================

    @Nested
    @DisplayName("Constants endpoint chain")
    class ConstantsEndpointTests {

        @Test
        void allEndpointsUseCorrectRegion() {
            String region = "cn-north-4";

            String control = Constants.getControlPlaneEndpoint(region);
            assertTrue(control.contains(region), "Control plane should contain region");
            assertTrue(control.startsWith("https://"), "Control plane should be HTTPS");

            String iam = Constants.getIamEndpoint(region);
            assertTrue(iam.contains(region), "IAM should contain region");
            assertTrue(iam.startsWith("https://"), "IAM should be HTTPS");

            String swr = Constants.getSwrEndpoint(region);
            assertTrue(swr.contains(region), "SWR should contain region");
            assertTrue(swr.startsWith("https://"), "SWR should be HTTPS");

            String identity = Constants.getIdentityEndpoint(region);
            assertTrue(identity.contains(region), "Identity should contain region");
            assertTrue(identity.startsWith("https://"), "Identity should be HTTPS");

            String memoryControl = Constants.getMemoryEndpoint("control", region);
            assertEquals(control, memoryControl, "Memory control should equal control plane");

            String memoryData = Constants.getMemoryEndpoint("data", region);
            assertTrue(memoryData.contains(region), "Memory data should contain region");
            assertTrue(memoryData.startsWith("https://"), "Memory data should be HTTPS");
        }

        @Test
        void headerConstantsMatchPythonSdk() {
            // These MUST match the Python SDK header names exactly
            assertEquals("x-hw-agentarts-session-id", Constants.SESSION_HEADER);
            assertEquals("X-HW-AgentGateway-Workload-Access-Token", Constants.ACCESS_TOKEN_HEADER);
            assertEquals("X-HW-AgentGateway-User-Id", Constants.USER_ID_HEADER);
            assertEquals("X-Request-Id", Constants.REQUEST_ID_HEADER);
            assertEquals("X-Hw-AgentArts-Runtime-Custom-", Constants.CUSTOM_HEADER_PREFIX);
        }
    }

    // ========================
    // agentscope bridge + runtime
    // ========================

    @Nested
    @DisplayName("agentscope bridge + runtime integration")
    class AgentscopeBridgeTests {

        @Test
        void agentscopeRuntimeHostRegistersOnApp() {
            AgentArtsRuntimeApp app = new AgentArtsRuntimeApp();

            new AgentscopeRuntimeHost(app, (payload, runtimeCtx) -> {
                return Map.of("reply", "bridged");
            });

            // Verify the handler was registered by starting and invoking
            app.setPingHandler(() -> PingStatus.HEALTHY);
            app.run(0);

            try {
                int port = app.getPort();
                assertTrue(port > 0);

                Vertx vertx = Vertx.vertx();
                WebClient client = WebClient.create(vertx);

                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> body = new AtomicReference<>();

                client.post(port, "localhost", "/invocations")
                        .putHeader("Content-Type", "application/json")
                        .sendBuffer(Buffer.buffer("{\"message\":\"test\"}"))
                        .onSuccess(resp -> {
                            body.set(resp.bodyAsString());
                            latch.countDown();
                        })
                        .onFailure(err -> latch.countDown());

                try {
                    assertTrue(latch.await(5, TimeUnit.SECONDS));
                    JsonNode json = MAPPER.readTree(body.get());
                    assertEquals("bridged", json.get("reply").asText());
                } catch (Exception e) {
                    fail("Request failed: " + e.getMessage());
                }

                client.close();
                vertx.close();
            } finally {
                app.stop();
            }
        }

        @Test
        void bridgeContextPropagatesRequestIdAndToken() {
            RequestContext rc = new RequestContext("req-bridge-1", "sess-bridge-1", "user-bridge-1", "token-bridge-1");

            AgentscopeRequestContext ctx = AgentscopeRuntimeHost.bridgeContext(rc);

            assertEquals("sess-bridge-1", ctx.sessionId());
            assertEquals("user-bridge-1", ctx.userId());
            assertEquals("req-bridge-1", ctx.requestId());
            assertEquals("token-bridge-1", ctx.workloadAccessToken());
        }
    }

    // ========================
    // Helper: test State implementation
    // ========================

    static class TestAgentState implements State {
        public String agentId;
        public int step;

        TestAgentState() {}

        TestAgentState(String agentId, int step) {
            this.agentId = agentId;
            this.step = step;
        }
    }
}
