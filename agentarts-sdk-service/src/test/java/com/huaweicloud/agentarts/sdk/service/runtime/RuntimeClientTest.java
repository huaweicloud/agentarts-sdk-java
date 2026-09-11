package com.huaweicloud.agentarts.sdk.service.runtime;

import com.huaweicloud.agentarts.sdk.core.SignMode;
import com.huaweicloud.agentarts.sdk.core.APIException;
import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import com.huaweicloud.agentarts.sdk.service.http.BaseHttpClient;
import com.huaweicloud.agentarts.sdk.service.http.RequestConfig;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import com.huaweicloud.agentarts.sdk.service.runtime.model.CreateAgentRequest;
import com.huaweicloud.agentarts.sdk.service.runtime.model.UpdateAgentRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import com.sun.net.httpserver.HttpServer;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RuntimeClient and LocalRuntimeClient.
 */
class RuntimeClientTest {

    @Nested
    @DisplayName("RuntimeClient API methods")
    @SuppressWarnings("deprecation")
    class ApiMethodTests {

        @TempDir
        Path tmpDir;

        @Test
        void controlPlaneMethodsExist() throws Exception {
            Class<?> cls = RuntimeClient.class;
            assertNotNull(cls.getMethod("createAgent", CreateAgentRequest.class));
            assertNotNull(cls.getMethod("createAgent", String.class, String.class));
            assertNotNull(cls.getMethod("updateAgent", String.class, UpdateAgentRequest.class));
            assertNotNull(cls.getMethod("createOrUpdateAgent", String.class, CreateAgentRequest.class));
            assertNotNull(cls.getMethod("getAgents", String.class, int.class, int.class));
            assertNotNull(cls.getMethod("getAgents"));
            assertNotNull(cls.getMethod("findAgentByName", String.class));
            assertNotNull(cls.getMethod("findAgentById", String.class));
            assertNotNull(cls.getMethod("deleteAgentByName", String.class));
        }

        @Test
        void endpointMethodsExist() throws Exception {
            Class<?> cls = RuntimeClient.class;
            assertNotNull(cls.getMethod("createAgentEndpoint", String.class, String.class));
            assertNotNull(cls.getMethod("updateAgentEndpoint", String.class, String.class, Map.class));
            assertNotNull(cls.getMethod("deleteAgentEndpoint", String.class, String.class));
            assertNotNull(cls.getMethod("findAgentEndpoint", String.class, String.class));
        }

        @Test
        void dataPlaneMethodsExist() throws Exception {
            Class<?> cls = RuntimeClient.class;
            assertNotNull(cls.getMethod("invokeAgent", String.class, String.class, String.class));
            assertNotNull(cls.getMethod("invokeAgentRaw", String.class, String.class, String.class,
                    String.class, String.class, int.class, String.class, String.class));
            assertNotNull(cls.getMethod("execCommand", String.class, String.class, String.class));
            assertNotNull(cls.getMethod("execCommandRaw", String.class, String.class, List.class,
                    boolean.class, String.class, String.class, String.class, int.class));
            assertNotNull(cls.getMethod("uploadFiles", String.class, String.class, List.class));
            assertNotNull(cls.getMethod("downloadFiles", String.class, String.class, String.class));
            assertNotNull(cls.getMethod("startSession", String.class));
            assertNotNull(cls.getMethod("stopSession", String.class, String.class));
        }

        @Test
        void implementsAutoCloseable() {
            assertTrue(AutoCloseable.class.isAssignableFrom(RuntimeClient.class));
        }

        @Test
        void constructorsDoNotThrow() {
            // RuntimeClient exposes no region/signMode getters, so the smoke test
            // is that each public constructor completes without throwing.
            new RuntimeClient();
            new RuntimeClient("cn-southwest-2");
            new RuntimeClient("cn-southwest-2", true);
        }

        @Test
        void perCallEndpointBearerAndEncodedQueryAreApplied() throws Exception {
            AtomicReference<String> authorization = new AtomicReference<>();
            AtomicReference<String> rawQuery = new AtomicReference<>();
            AtomicReference<String> rawPath = new AtomicReference<>();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                rawQuery.set(exchange.getRequestURI().getRawQuery());
                rawPath.set(exchange.getRequestURI().getRawPath());
                byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();

            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            try (RuntimeClient client = new RuntimeClient("cn-southwest-2")) {
                // The data-plane base URL is set via setDataPlaneEndpoint; the
                // per-call endpoint is an endpoint NAME carried as ?endpoint=xxx.
                client.setDataPlaneEndpoint(base);
                Map<String, Object> result = client.invokeAgent(
                        "agent/name", "session", "{}", "per-call-token", "named-endpoint",
                        2, "user", null);
                assertNotNull(result);
                assertEquals("Bearer per-call-token", authorization.get());
                assertEquals("/runtimes/agent%2Fname/invocations", rawPath.get());
                assertTrue(rawQuery.get().contains("endpoint=named-endpoint"));

                client.downloadFiles(
                        "agent/name", "session", "/home/user/a b.txt", true,
                        "download-token", "named-endpoint", null, 2);
                assertEquals("Bearer download-token", authorization.get());
                assertTrue(rawQuery.get().contains("path=%2Fhome%2Fuser%2Fa%20b.txt"));
                assertTrue(rawQuery.get().contains("recursive=true"));
                assertTrue(rawQuery.get().contains("endpoint=named-endpoint"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        void createOrUpdateForwardsEnvVarsAndStorageOnUpdatePath() throws Exception {
            BaseHttpClient controlClient = mock(BaseHttpClient.class);
            BaseHttpClient dataClient = mock(BaseHttpClient.class);
            // findAgentByName returns an existing agent so the update path is taken.
            RequestResult agents = RequestResult.builder()
                    .success(true).statusCode(200)
                    .data(JsonUtils.MAPPER.readTree(
                            "{\"items\":[{\"id\":\"agent-id\",\"name\":\"sample-agent\"}],\"total\":1}"))
                    .build();
            when(controlClient.request(eq("GET"), eq("/runtimes"),
                    isNull(), isNull(), anyMap())).thenReturn(Mono.just(agents));
            RequestResult updated = RequestResult.builder()
                    .success(true).statusCode(200)
                    .data(JsonUtils.MAPPER.readTree("{\"id\":\"agent-id\"}"))
                    .build();
            when(controlClient.put(eq("/runtimes/agent-id"), any()))
                    .thenReturn(Mono.just(updated));

            try (RuntimeClient client = new RuntimeClient(
                    "test-region", true, SignMode.SDK_HMAC_SHA256,
                    controlClient, dataClient)) {
                CreateAgentRequest req = new CreateAgentRequest()
                        .withName("sample-agent")
                        .withEnvironmentVariables(List.of(
                                Map.of("key", "API_KEY", "value", "secret")))
                        .withStorageConfig(Map.of("sfs_turbo", List.of(Map.of(
                                "sfs_turbo_id", "uuid",
                                "mount_path", "/data"))));
                client.createOrUpdateAgent("sample-agent", req);
            }

            ArgumentCaptor<UpdateAgentRequest> captor =
                    ArgumentCaptor.forClass(UpdateAgentRequest.class);
            verify(controlClient).put(eq("/runtimes/agent-id"), captor.capture());
            assertEquals(List.of(Map.of("key", "API_KEY", "value", "secret")),
                    captor.getValue().getEnvironmentVariables());
            assertEquals(Map.of("sfs_turbo", List.of(Map.of(
                            "sfs_turbo_id", "uuid",
                            "mount_path", "/data"))),
                    captor.getValue().getStorageConfig());
        }

        @Test
        void agentFiltersArePassedAsEncodedQueryParameters() throws Exception {
            BaseHttpClient controlClient = mock(BaseHttpClient.class);
            BaseHttpClient dataClient = mock(BaseHttpClient.class);
            RequestResult response = RequestResult.builder()
                    .success(true)
                    .statusCode(200)
                    .data(JsonUtils.MAPPER.readTree("{\"items\":[],\"total\":0}"))
                    .build();
            when(controlClient.request(eq("GET"), eq("/runtimes"),
                    isNull(), isNull(), anyMap())).thenReturn(Mono.just(response));

            try (RuntimeClient client = new RuntimeClient(
                    "test-region", true, SignMode.SDK_HMAC_SHA256,
                    controlClient, dataClient)) {
                assertEquals(0, client.getAgents("agent&limit=999", 2, 10).getTotal());
            }

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, List<String>>> query = ArgumentCaptor.forClass(Map.class);
            verify(controlClient).request(eq("GET"), eq("/runtimes"),
                    isNull(), isNull(), query.capture());
            assertEquals(List.of("agent&limit=999"), query.getValue().get("name"));
            assertEquals(List.of("2"), query.getValue().get("offset"));
            assertEquals(List.of("10"), query.getValue().get("limit"));
            verify(controlClient).close();
            verify(dataClient).close();
        }

        @Test
        void lookupAndDeletePropagateControlPlaneFailures() throws Exception {
            BaseHttpClient controlClient = mock(BaseHttpClient.class);
            BaseHttpClient dataClient = mock(BaseHttpClient.class);
            RequestResult forbidden = RequestResult.builder()
                    .success(false).statusCode(403).error("forbidden").build();
            when(controlClient.get("/runtimes/agent-id")).thenReturn(Mono.just(forbidden));

            try (RuntimeClient client = new RuntimeClient(
                    "test-region", true, SignMode.SDK_HMAC_SHA256,
                    controlClient, dataClient)) {
                APIException lookupError = assertThrows(
                        APIException.class, () -> client.findAgentById("agent-id"));
                assertEquals(403, lookupError.getStatusCode());

                RequestResult agents = RequestResult.builder()
                        .success(true).statusCode(200)
                        .data(JsonUtils.MAPPER.readTree(
                                "{\"items\":[{\"id\":\"agent-id\",\"name\":\"agent\"}],\"total\":1}"))
                        .build();
                when(controlClient.request(eq("GET"), eq("/runtimes"),
                        isNull(), isNull(), anyMap())).thenReturn(Mono.just(agents));
                when(controlClient.delete("/runtimes/agent-id")).thenReturn(Mono.just(forbidden));

                APIException deleteError = assertThrows(
                        APIException.class, () -> client.deleteAgentByName("agent"));
                assertEquals(403, deleteError.getStatusCode());
            }
        }

        @Test
        void multipartUploadsRejectHeaderInjectionAndOversizedBodies() {
            BaseHttpClient controlClient = mock(BaseHttpClient.class);
            BaseHttpClient dataClient = mock(BaseHttpClient.class);
            when(dataClient.getConfig()).thenReturn(RequestConfig.builder()
                    .maxRequestBodyBytes(256)
                    .build());

            try (RuntimeClient client = new RuntimeClient(
                    "test-region", true, SignMode.SDK_HMAC_SHA256,
                    controlClient, dataClient)) {
                List<Map<String, Object>> injected = List.of(
                        Map.of("filename", "safe.txt", "content", new byte[] {1}),
                        Map.of("filename", "bad\r\nX-Injected: yes", "content", new byte[] {2}));
                assertThrows(IllegalArgumentException.class,
                        () -> client.uploadFiles("agent", "session", injected,
                                "/remote", null, null, null,
                                "token", "https://example.test", null, 30));

                List<Map<String, Object>> oversized = List.of(
                        Map.of("filename", "one.bin", "content", new byte[200]),
                        Map.of("filename", "two.bin", "content", new byte[200]));
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                        () -> client.uploadFiles("agent", "session", oversized,
                                "/remote", null, null, null,
                                "token", "https://example.test", null, 30));
                assertTrue(error.getMessage().contains("configured limit"));
            }
            verify(dataClient, never()).request(
                    anyString(), anyString(), any(), any(), any(), anyDouble());
        }

        @Test
        void uploadFilesReadsLocalFileAndUploadsItsBytes() throws Exception {
            BaseHttpClient controlClient = mock(BaseHttpClient.class);
            BaseHttpClient dataClient = mock(BaseHttpClient.class);
            RequestResult response = RequestResult.builder()
                    .success(true).statusCode(200)
                    .data(JsonUtils.MAPPER.readTree("{\"status\":\"uploaded\"}"))
                    .build();
            when(dataClient.request(eq("POST"),
                    eq("/runtimes/agent/upload-files"),
                    anyMap(), any(), anyMap(), eq(30.0)))
                    .thenReturn(Mono.just(response));

            Path local = tmpDir.resolve("data.txt");
            Files.writeString(local, "hello local");

            try (RuntimeClient client = new RuntimeClient(
                    "test-region", true, SignMode.SDK_HMAC_SHA256,
                    controlClient, dataClient)) {
                Map<String, Object> result = client.uploadFiles(
                        "agent", "session",
                        List.of(Map.of("local_file", local.toString())),
                        "/home/user/", null, null, null,
                        "token", null, null, 30);
                assertNotNull(result);
            }

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, List<String>>> params = ArgumentCaptor.forClass(Map.class);
            ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
            verify(dataClient).request(eq("POST"),
                    eq("/runtimes/agent/upload-files"),
                    anyMap(), body.capture(), params.capture(), eq(30.0));
            assertArrayEquals("hello local".getBytes(StandardCharsets.UTF_8),
                    (byte[]) body.getValue());
            assertEquals("/home/user/data.txt", params.getValue().get("path").get(0));
        }

        @Test
        void uploadFilesRequiresLocalFileOrContent() {
            BaseHttpClient controlClient = mock(BaseHttpClient.class);
            BaseHttpClient dataClient = mock(BaseHttpClient.class);

            try (RuntimeClient client = new RuntimeClient(
                    "test-region", true, SignMode.SDK_HMAC_SHA256,
                    controlClient, dataClient)) {
                assertThrows(IllegalArgumentException.class,
                        () -> client.uploadFiles("agent", "session",
                                List.of(Map.<String, Object>of("filename", "x.txt")),
                                "/remote", null, null, null,
                                "token", "https://example.test", null, 30));
            }
        }

        @Test
        void chunkedCommandUsesHeaderAndExposesNdjsonStream() {
            BaseHttpClient controlClient = mock(BaseHttpClient.class);
            BaseHttpClient dataClient = mock(BaseHttpClient.class);
            AtomicBoolean closed = new AtomicBoolean();
            RequestResult response = RequestResult.builder()
                    .success(true)
                    .statusCode(200)
                    .streaming(true)
                    .lineStream(Flux.just("{\"stdout\":\"one\"}", "{\"exit_code\":0}"))
                    .closeAction(() -> closed.set(true))
                    .build();
            when(dataClient.request(eq("POST"), eq("/runtimes/agent/commands"),
                    anyMap(), any(), anyMap(), eq(12.0))).thenReturn(Mono.just(response));

            try (RuntimeClient client = new RuntimeClient(
                    "test-region", true, SignMode.SDK_HMAC_SHA256,
                    controlClient, dataClient);
                 RequestResult result = client.execCommandRaw(
                         "agent", "session", List.of("echo", "one"), true,
                         "token", "named-endpoint", "user", 12)) {
                assertEquals(List.of("{\"stdout\":\"one\"}", "{\"exit_code\":0}"),
                        result.iterLines().collectList().block());
            }
            assertTrue(closed.get());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
            ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, List<String>>> params = ArgumentCaptor.forClass(Map.class);
            verify(dataClient).request(eq("POST"),
                    eq("/runtimes/agent/commands"),
                    headers.capture(), body.capture(), params.capture(), eq(12.0));
            assertEquals("chunked", headers.getValue().get("Command-Type"));
            assertEquals("Bearer token", headers.getValue().get("Authorization"));
            assertEquals(List.of("named-endpoint"), params.getValue().get("endpoint"));
            assertEquals(List.of("echo", "one"),
                    ((com.huaweicloud.agentarts.sdk.service.runtime.model.ExecCommandRequest)
                            body.getValue()).getCommand());
            assertFalse(((com.huaweicloud.agentarts.sdk.service.runtime.model.ExecCommandRequest)
                    body.getValue()).isChunked());
        }
    }

    @Nested
    @DisplayName("LocalRuntimeClient API methods")
    class LocalClientTests {

        @Test
        void methodsExist() throws Exception {
            Class<?> cls = LocalRuntimeClient.class;
            assertNotNull(cls.getMethod("invokeAgent", String.class));
            assertNotNull(cls.getMethod("invokeAgent", String.class, String.class,
                    String.class, String.class, String.class, String.class));
            assertNotNull(cls.getMethod("invokeAgentRaw", String.class, String.class,
                    String.class, String.class, String.class, String.class));
            assertNotNull(cls.getMethod("pingAgent"));
        }

        @Test
        void implementsAutoCloseable() {
            assertTrue(AutoCloseable.class.isAssignableFrom(LocalRuntimeClient.class));
        }

        @Test
        void constructorsWork() {
            try (LocalRuntimeClient c0 = new LocalRuntimeClient();
                 LocalRuntimeClient c1 = new LocalRuntimeClient(3000);
                 LocalRuntimeClient c2 = new LocalRuntimeClient(3000, "localhost", 60)) {
                assertEquals(8080, c0.getPort());
                assertEquals("localhost", c0.getHost());
                assertEquals(3000, c1.getPort());
                assertEquals("localhost", c1.getHost());
                assertEquals(3000, c2.getPort());
                assertEquals("localhost", c2.getHost());
            }
        }

        @Test
        void defaultPortAndHost() {
            try (LocalRuntimeClient client = new LocalRuntimeClient()) {
                assertEquals(8080, client.getPort());
                assertEquals("localhost", client.getHost());
            }
        }

        @Test
        void completeInvokeOptionsReachLocalRuntime() throws Exception {
            AtomicReference<String> path = new AtomicReference<>();
            AtomicReference<String> query = new AtomicReference<>();
            AtomicReference<String> authorization = new AtomicReference<>();
            AtomicReference<String> session = new AtomicReference<>();
            AtomicReference<String> user = new AtomicReference<>();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                path.set(exchange.getRequestURI().getPath());
                query.set(exchange.getRequestURI().getRawQuery());
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                session.set(exchange.getRequestHeaders().getFirst("x-hw-agentarts-session-id"));
                user.set(exchange.getRequestHeaders().getFirst("X-HW-AgentGateway-User-Id"));
                byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();

            try (LocalRuntimeClient client = new LocalRuntimeClient(
                    server.getAddress().getPort(), "127.0.0.1", 5)) {
                assertNotNull(client.invokeAgent(
                        "{}", "unit-session", "unit-token", "named endpoint",
                        "unit-user", "stream"));
                assertEquals("/invocations/stream", path.get());
                assertEquals("endpoint=named%20endpoint", query.get());
                assertEquals("Bearer unit-token", authorization.get());
                assertEquals("unit-session", session.get());
                assertEquals("unit-user", user.get());
            } finally {
                server.stop(0);
            }
        }

        @Test
        void localCustomPathRejectsTraversalBeforeRequest() {
            try (LocalRuntimeClient client = new LocalRuntimeClient(1, "127.0.0.1", 1)) {
                assertThrows(IllegalArgumentException.class,
                        () -> client.invokeAgentRaw(
                                "{}", null, null, null, null, "../admin"));
            }
        }
    }
}
