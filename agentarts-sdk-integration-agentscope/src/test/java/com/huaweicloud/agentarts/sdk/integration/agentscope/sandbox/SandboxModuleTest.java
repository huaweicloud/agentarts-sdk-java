package com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.agentarts.sdk.core.APIException;
import com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox.client.*;
import com.huaweicloud.agentarts.sdk.service.runtime.RuntimeClient;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for sandbox integration module: lifecycle, data models, client wiring.
 */
class SandboxModuleTest {

    // ========================
    // SandboxConstants
    // ========================

    @Nested
    @DisplayName("SandboxConstants")
    class SandboxConstantsTests {

        @Test
        void sandboxTypeHasExpectedValue() {
            assertEquals("AgentArtsBasedSandbox", SandboxConstants.SANDBOX_TYPE);
        }
    }

    // ========================
    // Data models: ExecData, ExecResponse, SessionData, SessionStartResponse
    // ========================

    @Nested
    @DisplayName("Sandbox data models")
    class DataModelTests {

        private final ObjectMapper mapper = new ObjectMapper();

        @Test
        void execDataGettersAndSetters() {
            ExecData data = new ExecData();
            data.setExitCode(0);
            data.setStdout("hello");
            data.setStderr("");

            assertEquals(0, data.getExitCode());
            assertEquals("hello", data.getStdout());
            assertEquals("", data.getStderr());
        }

        @Test
        void execDataJacksonDeserializesExitCodeFromSnakeCase() throws Exception {
            String json = "{\"exit_code\":1,\"stdout\":\"out\",\"stderr\":\"err\"}";
            ExecData data = mapper.readValue(json, ExecData.class);

            assertEquals(1, data.getExitCode());
            assertEquals("out", data.getStdout());
            assertEquals("err", data.getStderr());
        }

        @Test
        void execResponseGettersAndSetters() {
            ExecResponse resp = new ExecResponse();
            ExecData data = new ExecData();
            data.setExitCode(0);

            resp.setCode(200);
            resp.setMessage("ok");
            resp.setData(data);

            assertEquals(200, resp.getCode());
            assertEquals("ok", resp.getMessage());
            assertSame(data, resp.getData());
        }

        @Test
        void sessionDataGettersAndSetters() {
            SessionData data = new SessionData();
            data.setSessionId("sess-123");
            assertEquals("sess-123", data.getSessionId());
        }

        @Test
        void sessionDataJacksonDeserializesSessionIdFromSnakeCase() throws Exception {
            String json = "{\"session_id\":\"sess-456\"}";
            SessionData data = mapper.readValue(json, SessionData.class);
            assertEquals("sess-456", data.getSessionId());
        }

        @Test
        void sessionStartResponseGettersAndSetters() {
            SessionStartResponse resp = new SessionStartResponse();
            SessionData data = new SessionData();
            data.setSessionId("sess-789");

            resp.setCode(0);
            resp.setMessage("success");
            resp.setData(data);

            assertEquals(0, resp.getCode());
            assertEquals("success", resp.getMessage());
            assertSame(data, resp.getData());
            assertEquals("sess-789", resp.getData().getSessionId());
        }

        @Test
        void sessionStartResponseRoundTrip() throws Exception {
            String json = "{\"code\":0,\"message\":\"ok\",\"data\":{\"session_id\":\"rt-1\"}}";
            SessionStartResponse resp = mapper.readValue(json, SessionStartResponse.class);

            assertEquals(0, resp.getCode());
            assertEquals("ok", resp.getMessage());
            assertNotNull(resp.getData());
            assertEquals("rt-1", resp.getData().getSessionId());
        }
    }

    // ========================
    // AgentArtsSandboxState
    // ========================

    @Nested
    @DisplayName("AgentArtsSandboxState")
    class SandboxStateTests {

        @Test
        void extendsSandboxState() {
            assertInstanceOf(SandboxState.class, new AgentArtsSandboxState());
        }

        @Test
        void sessionIdGetterSetter() {
            AgentArtsSandboxState state = new AgentArtsSandboxState();
            state.setSessionId("sess-abc");
            assertEquals("sess-abc", state.getSessionId());
        }
    }

    // ========================
    // AgentArtsSandboxClientOption
    // ========================

    @Nested
    @DisplayName("AgentArtsSandboxClientOption")
    class SandboxClientOptionTests {

        @Test
        void extendsSandboxClientOptions() {
            assertInstanceOf(SandboxClientOptions.class, new AgentArtsSandboxClientOption());
        }

        @Test
        void gettersAndSetters() {
            AgentArtsSandboxClientOption opt = new AgentArtsSandboxClientOption();
            opt.setBaseUrl("http://localhost:8080");
            opt.setApiKey("key-123");
            opt.setSandboxName("my-sandbox");

            assertEquals("http://localhost:8080", opt.getBaseUrl());
            assertEquals("key-123", opt.getApiKey());
            assertEquals("my-sandbox", opt.getSandboxName());
        }

        @Test
        void getTypeReturnsSandboxTypeConstant() {
            AgentArtsSandboxClientOption opt = new AgentArtsSandboxClientOption();
            assertEquals(SandboxConstants.SANDBOX_TYPE, opt.getType());
        }

        @Test
        void createClientReturnsAgentArtsBasedSandboxClient() {
            AgentArtsSandboxClientOption opt = new AgentArtsSandboxClientOption();
            opt.setBaseUrl("http://localhost:8080");
            opt.setApiKey("key-123");
            opt.setSandboxName("sandbox-1");

            SandboxClient<?> client = opt.createClient();
            assertInstanceOf(AgentArtsBasedSandboxClient.class, client);
        }
    }

    // ========================
    // AgentArtsSandbox — lifecycle
    // ========================

    @Nested
    @DisplayName("AgentArtsSandbox lifecycle")
    class SandboxTests {

        private AgentRunClient mockClient;
        private AgentArtsSandbox sandbox;

        @BeforeEach
        void setUp() {
            mockClient = mock(AgentRunClient.class);
            sandbox = new AgentArtsSandbox(mockClient);
        }

        @Test
        void implementsSandboxInterface() {
            assertInstanceOf(Sandbox.class, sandbox);
        }

        @Test
        void isRunningIsFalseByDefault() {
            assertFalse(sandbox.isRunning());
        }

        @Test
        void startCreatesSessionViaClient() throws Exception {
            when(mockClient.createSession()).thenReturn("sess-1");

            sandbox.start();

            assertTrue(sandbox.isRunning());
            verify(mockClient).createSession();
        }

        @Test
        void startIsIdempotentWhenSessionAlreadyExists() throws Exception {
            when(mockClient.createSession()).thenReturn("sess-1");
            sandbox.start();
            sandbox.start();

            verify(mockClient, times(1)).createSession();
        }

        @Test
        void closeStopsSessionAndSetsRunningFalse() throws Exception {
            when(mockClient.createSession()).thenReturn("sess-1");
            sandbox.start();
            assertTrue(sandbox.isRunning());

            sandbox.close();

            assertFalse(sandbox.isRunning());
            verify(mockClient).stopSession("sess-1");
        }

        @Test
        void closeWithNoSessionDoesNotCallStopSession() throws Exception {
            sandbox.close();

            verify(mockClient, never()).stopSession(anyString());
        }

        @Test
        void execDelegatesToClient() throws Exception {
            when(mockClient.createSession()).thenReturn("sess-1");
            ExecResult expected = new ExecResult(0, "output", "", false);
            when(mockClient.executeCommand("sess-1", "echo hello")).thenReturn(expected);

            sandbox.start();
            ExecResult result = sandbox.exec(null, "echo hello", 30);

            assertSame(expected, result);
            verify(mockClient).executeCommand("sess-1", "echo hello");
        }

        @Test
        void execThrowsWhenSessionNotInitialized() {
            assertThrows(RuntimeException.class,
                    () -> sandbox.exec(null, "echo hello", 30));
        }

        @Test
        void resumeSetsSessionIdAndRunning() {
            AgentArtsSandboxState state = new AgentArtsSandboxState();
            state.setSessionId("sess-resumed");

            sandbox.resume(state);

            assertTrue(sandbox.isRunning());
            SandboxState currentState = sandbox.getState();
            assertEquals("sess-resumed", currentState.getSessionId());
        }

        @Test
        void getStateReturnsStateWithCurrentSessionId() throws Exception {
            when(mockClient.createSession()).thenReturn("sess-state");
            sandbox.start();

            SandboxState state = sandbox.getState();
            assertInstanceOf(AgentArtsSandboxState.class, state);
            assertEquals("sess-state", state.getSessionId());
        }

        @Test
        void stopDoesNotChangeRunningState() throws Exception {
            when(mockClient.createSession()).thenReturn("sess-1");
            sandbox.start();
            assertTrue(sandbox.isRunning());

            sandbox.stop();

            assertTrue(sandbox.isRunning(), "stop() should not change running flag");
        }

        @Test
        void persistWorkspaceReturnsNull() throws Exception {
            assertNull(sandbox.persistWorkspace());
        }

        @Test
        void hydrateWorkspaceIsNoOp() throws Exception {
            assertDoesNotThrow(() -> sandbox.hydrateWorkspace(null));
        }
    }

    // ========================
    // AgentArtsBasedSandboxClient
    // ========================

    @Nested
    @DisplayName("AgentArtsBasedSandboxClient")
    class SandboxClientTests {

        private AgentRunClient mockRunClient;
        private AgentArtsBasedSandboxClient client;

        @BeforeEach
        void setUp() {
            mockRunClient = mock(AgentRunClient.class);
            client = new AgentArtsBasedSandboxClient(mockRunClient);
        }

        @Test
        void implementsSandboxClientInterface() {
            assertInstanceOf(SandboxClient.class, client);
        }

        @Test
        void createReturnsAgentArtsSandbox() {
            Sandbox sandbox = client.create(null, null, null);
            assertInstanceOf(AgentArtsSandbox.class, sandbox);
            assertFalse(sandbox.isRunning());
        }

        @Test
        void resumeReturnsRunningSandboxWithState() {
            AgentArtsSandboxState state = new AgentArtsSandboxState();
            state.setSessionId("sess-resume");

            Sandbox sandbox = client.resume(state);

            assertInstanceOf(AgentArtsSandbox.class, sandbox);
            assertTrue(sandbox.isRunning());
            assertEquals("sess-resume", sandbox.getState().getSessionId());
        }

        @Test
        void deleteClosesSandbox() throws Exception {
            Sandbox sandbox = mock(Sandbox.class);
            client.delete(sandbox);
            verify(sandbox).close();
        }

        @Test
        void deleteHandlesNullSandbox() {
            assertDoesNotThrow(() -> client.delete(null));
        }

        @Test
        void deleteWrapsCloseExceptionInRuntimeException() throws Exception {
            Sandbox sandbox = mock(Sandbox.class);
            doThrow(new Exception("close failed")).when(sandbox).close();

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> client.delete(sandbox));
            assertEquals("close failed", ex.getCause().getMessage());
        }

        @Test
        void serializeStateReturnsSessionId() {
            AgentArtsSandboxState state = new AgentArtsSandboxState();
            state.setSessionId("sess-serialize");

            String serialized = client.serializeState(state);
            assertEquals("sess-serialize", serialized);
        }

        @Test
        void deserializeStateCreatesStateWithSessionId() {
            SandboxState state = client.deserializeState("sess-deserialize");

            assertInstanceOf(AgentArtsSandboxState.class, state);
            assertEquals("sess-deserialize", state.getSessionId());
        }

        @Test
        void serializeAndDeserializeRoundTrip() {
            AgentArtsSandboxState original = new AgentArtsSandboxState();
            original.setSessionId("sess-roundtrip");

            String serialized = client.serializeState(original);
            SandboxState restored = client.deserializeState(serialized);

            assertEquals(original.getSessionId(), restored.getSessionId());
        }
    }

    // ========================
    // AgentRunClient
    // ========================

    @Nested
    @DisplayName("AgentRunClient")
    class AgentRunClientTests {

        @Test
        void createSessionReturnsSessionIdFromResponse() {
            try (MockedConstruction<RuntimeClient> mocked = mockConstruction(RuntimeClient.class,
                    (mock, context) -> when(mock.startSession("sandbox-1"))
                            .thenReturn(Map.of("data", Map.of("session_id", "sess-new"))))) {

                AgentRunClient client = new AgentRunClient("http://localhost:8080", "sandbox-1", "api-key");
                String sessionId = client.createSession();

                assertEquals("sess-new", sessionId);
            }
        }

        @Test
        void createSessionThrowsWhenResponseIsNull() {
            try (MockedConstruction<RuntimeClient> mocked = mockConstruction(RuntimeClient.class,
                    (mock, context) -> when(mock.startSession("sandbox-1")).thenReturn(null))) {

                AgentRunClient client = new AgentRunClient("http://localhost:8080", "sandbox-1", "api-key");

                assertThrows(RuntimeException.class, () -> client.createSession());
            }
        }

        @Test
        void createSessionThrowsWhenDataIsNull() {
            try (MockedConstruction<RuntimeClient> mocked = mockConstruction(RuntimeClient.class,
                    (mock, context) -> when(mock.startSession("sandbox-1"))
                            .thenReturn(Map.of("code", 0, "message", "ok")))) {

                AgentRunClient client = new AgentRunClient("http://localhost:8080", "sandbox-1", "api-key");

                assertThrows(RuntimeException.class, () -> client.createSession());
            }
        }

        @Test
        void stopSessionCallsRuntimeClient() {
            try (MockedConstruction<RuntimeClient> mocked = mockConstruction(RuntimeClient.class)) {
                AgentRunClient client = new AgentRunClient("http://localhost:8080", "sandbox-1", "api-key");
                RuntimeClient runtimeMock = mocked.constructed().get(0);

                client.stopSession("sess-1");

                verify(runtimeMock).stopSession("sandbox-1", "sess-1");
            }
        }

        @Test
        void stopSessionSwallows404Error() {
            try (MockedConstruction<RuntimeClient> mocked = mockConstruction(RuntimeClient.class,
                    (mock, context) -> doThrow(new APIException(404, "not_found", "session not found"))
                            .when(mock).stopSession("sandbox-1", "sess-gone"))) {

                AgentRunClient client = new AgentRunClient("http://localhost:8080", "sandbox-1", "api-key");

                assertDoesNotThrow(() -> client.stopSession("sess-gone"));
            }
        }

        @Test
        void stopSessionPropagatesNon404Error() {
            try (MockedConstruction<RuntimeClient> mocked = mockConstruction(RuntimeClient.class,
                    (mock, context) -> doThrow(new APIException(500, "internal", "server error"))
                            .when(mock).stopSession("sandbox-1", "sess-err"))) {

                AgentRunClient client = new AgentRunClient("http://localhost:8080", "sandbox-1", "api-key");

                assertThrows(RuntimeException.class, () -> client.stopSession("sess-err"));
            }
        }

        @Test
        void executeCommandReturnsExecResult() {
            try (MockedConstruction<RuntimeClient> mocked = mockConstruction(RuntimeClient.class,
                    (mock, context) -> when(mock.execCommand(eq("sandbox-1"), eq("sess-1"),
                                    eq(java.util.List.of("sh", "-c", "echo hello")),
                                    eq(false), eq(""), eq(""), eq(""), eq(900)))
                            .thenReturn(Map.of("exit_code", 0, "stdout", "hello\n", "stderr", "")))) {

                AgentRunClient client = new AgentRunClient("http://localhost:8080", "sandbox-1", "api-key");
                ExecResult result = client.executeCommand("sess-1", "echo hello");

                assertEquals(0, result.exitCode());
                assertEquals("hello\n", result.stdout());
                assertEquals("", result.stderr());
            }
        }

        @Test
        void executeCommandThrowsWhenResponseIsNull() {
            try (MockedConstruction<RuntimeClient> mocked = mockConstruction(RuntimeClient.class,
                    (mock, context) -> when(mock.execCommand(anyString(), anyString(),
                                    anyList(), anyBoolean(), anyString(), anyString(), anyString(), anyInt()))
                            .thenReturn(null))) {

                AgentRunClient client = new AgentRunClient("http://localhost:8080", "sandbox-1", "api-key");

                assertThrows(RuntimeException.class,
                        () -> client.executeCommand("sess-1", "echo hello"));
            }
        }
    }

    // ========================
    // AgentArtsBasedSandboxFilesystemSpec
    // ========================

    @Nested
    @DisplayName("AgentArtsBasedSandboxFilesystemSpec")
    class FilesystemSpecTests {

        @Test
        void workspaceSpecReturnsConfiguredRoot() {
            AgentArtsBasedSandboxFilesystemSpec spec =
                    new AgentArtsBasedSandboxFilesystemSpec("http://localhost", "key", "sandbox", "/workspace");

            WorkspaceSpec ws = spec.workspaceSpec();
            assertEquals("/workspace", ws.getRoot());
        }

        @Test
        void snapshotSpecReturnsNoopSpec() {
            AgentArtsBasedSandboxFilesystemSpec spec =
                    new AgentArtsBasedSandboxFilesystemSpec("http://localhost", "key", "sandbox", "/workspace");

            SandboxSnapshotSpec snapshot = spec.snapshotSpec();
            assertInstanceOf(NoopSnapshotSpec.class, snapshot);
        }

        @Test
        void clientOptionsReturnsAgentArtsOptions() {
            AgentArtsBasedSandboxFilesystemSpec spec =
                    new AgentArtsBasedSandboxFilesystemSpec("http://localhost:9090", "api-key-1", "sandbox-1", "/root");

            SandboxClientOptions options = spec.clientOptions();
            assertInstanceOf(AgentArtsSandboxClientOption.class, options);

            AgentArtsSandboxClientOption agentArtsOpt = (AgentArtsSandboxClientOption) options;
            assertEquals("http://localhost:9090", agentArtsOpt.getBaseUrl());
            assertEquals("api-key-1", agentArtsOpt.getApiKey());
        }

        @Test
        void createClientReturnsSandboxClient() {
            AgentArtsBasedSandboxFilesystemSpec spec =
                    new AgentArtsBasedSandboxFilesystemSpec("http://localhost", "key", "sandbox", "/workspace");

            SandboxClient<?> client = spec.createClient();
            assertInstanceOf(AgentArtsBasedSandboxClient.class, client);
        }
    }
}
