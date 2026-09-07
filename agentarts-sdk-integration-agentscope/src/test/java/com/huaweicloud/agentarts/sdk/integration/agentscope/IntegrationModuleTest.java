package com.huaweicloud.agentarts.sdk.integration.agentscope;

import com.huaweicloud.agentarts.sdk.core.APIException;
import com.huaweicloud.agentarts.sdk.core.PingStatus;
import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import com.huaweicloud.agentarts.sdk.integration.agentscope.message.MessageConverter;
import com.huaweicloud.agentarts.sdk.integration.agentscope.message.MessageConversionException;
import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeRuntimeHost;
import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeRequestContext;
import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeSessionExecutor;
import com.huaweicloud.agentarts.sdk.integration.agentscope.state.AgentStateStoreException;
import com.huaweicloud.agentarts.sdk.integration.agentscope.state.MemoryAgentStateStore;
import com.huaweicloud.agentarts.sdk.integration.agentscope.tool.CodeInterpreterTool;
import com.huaweicloud.agentarts.sdk.integration.agentscope.tool.GatewayTool;
import com.huaweicloud.agentarts.sdk.gateway.GatewayClient;
import com.huaweicloud.agentarts.sdk.memory.MemoryClient;
import com.huaweicloud.agentarts.sdk.memory.model.*;
import com.huaweicloud.agentarts.sdk.runtime.AgentArtsRuntimeApp;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import com.huaweicloud.agentarts.sdk.runtime.context.RequestContext;
import com.huaweicloud.agentarts.sdk.tools.CodeInterpreterClient;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for agentscope integration: interface contracts, message conversion, runtime bridge.
 */
class IntegrationModuleTest {

    private static String resultText(ToolResultBlock result) {
        assertNotNull(result);
        assertFalse(result.getOutput().isEmpty());
        return ((TextBlock) result.getOutput().get(0)).getText();
    }

    // ========================
    // MemoryAgentStateStore — AgentScope Session contract
    // ========================

    @Nested
    @DisplayName("MemoryAgentStateStore implements Session")
    class StateStoreTests {

        private MemoryAgentStateStore store;
        private FakeMemoryClient fakeClient;

        @BeforeEach
        void setUp() {
            fakeClient = new FakeMemoryClient();
            store = new MemoryAgentStateStore(fakeClient, "space-123");
        }

        @Test
        void saveAndGetSingleState() {
            TestState state = new TestState("hello");
            store.save("user1", "session1", "key1", state);

            // Verify Memory API was called
            assertTrue(fakeClient.sessionCreated, "createMemorySession should have been called");
            assertTrue(fakeClient.messagesAdded, "addMessages should have been called");

            Optional<TestState> result = store.get("user1", "session1", "key1", TestState.class);
            assertTrue(result.isPresent());
            assertEquals("hello", result.get().value);
        }

        @Test
        void saveAndGetListState() {
            List<TestState> states = List.of(new TestState("a"), new TestState("b"), new TestState("c"));
            store.save("user1", "session1", "messages", states);

            List<TestState> result = store.getList("user1", "session1", "messages", TestState.class);
            assertEquals(3, result.size());
            assertEquals("a", result.get(0).value);
            assertEquals("b", result.get(1).value);
            assertEquals("c", result.get(2).value);
        }

        @Test
        void savesAndLoadsListsLargerThanOneMessageBatch() {
            List<TestState> states = new ArrayList<>();
            for (int i = 0; i < 205; i++) {
                states.add(new TestState("value-" + i));
            }

            store.save("user1", "session1", "large-list", states);
            List<TestState> restored = store.getList(
                    "user1", "session1", "large-list", TestState.class);

            assertEquals(205, restored.size());
            assertEquals("value-0", restored.get(0).value);
            assertEquals("value-204", restored.get(204).value);
        }

        @Test
        void saveListDoesFullReplacement() {
            store.save("user1", "session1", "items", List.of(new TestState("old1"), new TestState("old2")));
            store.save("user1", "session1", "items", List.of(new TestState("new1")));

            List<TestState> result = store.getList("user1", "session1", "items", TestState.class);
            assertEquals(1, result.size());
            assertEquals("new1", result.get(0).value);
        }

        @Test
        void getReturnsEmptyWhenNotFound() {
            Optional<TestState> result = store.get("user1", "session1", "nonexistent", TestState.class);
            assertTrue(result.isEmpty());
        }

        @Test
        void getListReturnsEmptyWhenNotFound() {
            List<TestState> result = store.getList("user1", "session1", "nonexistent", TestState.class);
            assertTrue(result.isEmpty());
        }

        @Test
        void existsReturnsTrueAfterSave() {
            store.save("user1", "session1", "key1", new TestState("val"));
            assertTrue(store.exists("user1", "session1"));
        }

        @Test
        void existsReturnsFalseForUnknown() {
            assertFalse(store.exists("user1", "unknown-session"));
        }

        @Test
        void deleteRemovesSession() {
            store.save("user1", "session1", "key1", new TestState("val"));
            store.delete("user1", "session1");
            assertFalse(store.exists("user1", "session1"));
        }

        @Test
        void deleteByKeyRemovesOnlyKey() {
            store.save("user1", "session1", "key1", new TestState("val1"));
            store.save("user1", "session1", "key2", new TestState("val2"));
            store.delete("user1", "session1", "key1");

            assertTrue(store.get("user1", "session1", "key1", TestState.class).isEmpty());
            assertTrue(store.get("user1", "session1", "key2", TestState.class).isPresent());
        }

        @Test
        void listSessionIdsReturnsAll() {
            store.save("user1", "s1", "k", new TestState("1"));
            store.save("user1", "s2", "k", new TestState("2"));
            store.save("user1", "s3", "k", new TestState("3"));

            Set<String> ids = store.listSessionIds("user1");
            assertEquals(3, ids.size());
            assertTrue(ids.contains("s1"));
            assertTrue(ids.contains("s2"));
            assertTrue(ids.contains("s3"));
        }

        @Test
        void paginatesLongLivedStateIndexAndRejectsTruncatedPages() {
            for (int i = 0; i < 125; i++) {
                store.save("user1", "session-" + i, "key", new TestState("value"));
            }

            assertEquals(125, store.listSessionIds("user1").size());

            fakeClient.truncatePages = true;
            assertThrows(AgentStateStoreException.class, () -> store.listSessionIds("user1"));
        }

        @Test
        void listSessionIdsReturnsEmptyForUnknownUser() {
            Set<String> ids = store.listSessionIds("unknown");
            assertTrue(ids.isEmpty());
        }

        @Test
        void nullUserIdMapsToAnon() {
            store.save(null, "session1", "key1", new TestState("val"));
            assertTrue(store.exists(null, "session1"));

            Optional<TestState> result = store.get(null, "session1", "key1", TestState.class);
            assertTrue(result.isPresent());
        }

        @Test
        void multipleUsersIsolated() {
            store.save("alice", "s1", "k", new TestState("alice-val"));
            store.save("bob", "s1", "k", new TestState("bob-val"));

            assertEquals("alice-val", store.get("alice", "s1", "k", TestState.class).orElseThrow().value);
            assertEquals("bob-val", store.get("bob", "s1", "k", TestState.class).orElseThrow().value);
        }

        @Test
        void closeClosesMemoryClient() {
            store.close();
            assertTrue(fakeClient.closed, "close() should have been called on MemoryClient");
        }

        @Test
        void writeFailuresArePropagated() {
            fakeClient.failWrites = true;

            assertThrows(AgentStateStoreException.class,
                    () -> store.save("user1", "session1", "key1", new TestState("value")));
        }

        @Test
        void readFailuresArePropagated() {
            store.save("user1", "session1", "key1", new TestState("value"));
            fakeClient.failReads = true;

            assertThrows(AgentStateStoreException.class,
                    () -> store.get("user1", "session1", "key1", TestState.class));
        }

        @Test
        void sessionCreationFailuresArePropagated() {
            fakeClient.failSessionCreation = true;

            assertThrows(AgentStateStoreException.class,
                    () -> store.save("user1", "session1", "key1", new TestState("value")));
        }

        @Test
        void rejectsInvalidStateKeys() {
            assertThrows(IllegalArgumentException.class,
                    () -> store.save("user1", " ", "key1", new TestState("value")));
            assertThrows(IllegalArgumentException.class,
                    () -> store.save("user1", "session1", "", new TestState("value")));
        }

        @Test
        void stateCanBeReadAfterStoreRestart() {
            store.save("user1", "session1", "key1", new TestState("persisted"));

            MemoryAgentStateStore restarted = new MemoryAgentStateStore(fakeClient, "space-123");
            Optional<TestState> restored = restarted.get(
                    "user1", "session1", "key1", TestState.class);

            assertEquals("persisted", restored.orElseThrow().value);
        }

        @Test
        void deterministicSessionConflictAllowsSubsequentWrites() {
            store.save("user1", "session1", "key1", new TestState("first"));
            MemoryAgentStateStore restarted = new MemoryAgentStateStore(fakeClient, "space-123");

            restarted.save("user1", "session1", "key1", new TestState("second"));

            assertEquals("second", restarted.get(
                    "user1", "session1", "key1", TestState.class).orElseThrow().value);
        }

        @Test
        void indexCanBeReadAfterStoreRestart() {
            store.save("user1", "session1", "key1", new TestState("persisted"));
            store.save("user1", "session2", "key1", new TestState("persisted"));

            MemoryAgentStateStore restarted = new MemoryAgentStateStore(fakeClient, "space-123");

            assertTrue(restarted.exists("user1", "session1"));
            assertEquals(Set.of("session1", "session2"), restarted.listSessionIds("user1"));
        }

        @Test
        void keyDeletionSurvivesStoreRestart() {
            store.save("user1", "session1", "key1", new TestState("persisted"));
            store.delete("user1", "session1", "key1");

            MemoryAgentStateStore restarted = new MemoryAgentStateStore(fakeClient, "space-123");

            assertTrue(restarted.get(
                    "user1", "session1", "key1", TestState.class).isEmpty());
            assertFalse(restarted.exists("user1", "session1"));
        }

        @Test
        void sessionDeletionSurvivesStoreRestart() {
            store.save("user1", "session1", "key1", new TestState("one"));
            store.save("user1", "session1", "key2", new TestState("two"));
            store.delete("user1", "session1");

            MemoryAgentStateStore restarted = new MemoryAgentStateStore(fakeClient, "space-123");

            assertTrue(restarted.get(
                    "user1", "session1", "key1", TestState.class).isEmpty());
            assertTrue(restarted.get(
                    "user1", "session1", "key2", TestState.class).isEmpty());
            assertFalse(restarted.exists("user1", "session1"));
            assertTrue(restarted.listSessionIds("user1").isEmpty());
        }

        @Test
        void saveAfterDeleteRestoresIndexAndState() {
            store.save("user1", "session1", "key1", new TestState("before"));
            store.delete("user1", "session1", "key1");
            store.save("user1", "session1", "key1", new TestState("after"));

            MemoryAgentStateStore restarted = new MemoryAgentStateStore(fakeClient, "space-123");

            assertEquals("after", restarted.get(
                    "user1", "session1", "key1", TestState.class).orElseThrow().value);
            assertTrue(restarted.exists("user1", "session1"));
        }
    }

    // ========================
    // GatewayTool — AgentTool interface contract
    // ========================

    @Nested
    @DisplayName("GatewayTool implements AgentTool")
    class GatewayToolTests {

        @Test
        void implementsAgentTool() {
            assertTrue(AgentTool.class.isAssignableFrom(GatewayTool.class));
        }

        @Test
        void hasCorrectName() {
            GatewayTool tool = new GatewayTool(mock(GatewayClient.class));
            assertEquals("gateway_call", tool.getName());
        }

        @Test
        void hasDescription() {
            GatewayTool tool = new GatewayTool(mock(GatewayClient.class));
            assertNotNull(tool.getDescription());
            assertFalse(tool.getDescription().isEmpty());
        }

        @Test
        void parametersSchemaIsValid() {
            GatewayTool tool = new GatewayTool(mock(GatewayClient.class));
            Map<String, Object> params = tool.getParameters();
            assertEquals("object", params.get("type"));
            assertNotNull(params.get("properties"));
            assertNotNull(params.get("required"));

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) params.get("required");
            assertTrue(required.contains("gateway_id"));
            assertTrue(required.contains("tool_name"));
            assertFalse(required.contains("target_id"));
            assertEquals(false, params.get("additionalProperties"));
        }

        @Test
        void implementsCurrentAgentToolContract() {
            GatewayTool tool = new GatewayTool(mock(GatewayClient.class));
            assertInstanceOf(AgentTool.class, tool);
        }

        @Test
        void getOutputSchemaReturnsNull() {
            GatewayTool tool = new GatewayTool(mock(GatewayClient.class));
            assertNull(tool.getOutputSchema());
        }

        @Test
        void resolvesGatewayEndpointAndInvokesRealMcpTransport() throws Exception {
            String gatewayId = "01234567-89ab-cdef-0123-456789abcdef";
            GatewayClient gatewayClient = mock(GatewayClient.class);
            when(gatewayClient.getGateway(gatewayId)).thenReturn(RequestResult.builder()
                    .success(true)
                    .statusCode(200)
                    .data(JsonUtils.MAPPER.readTree(
                            "{\"endpoint_url\":\"https://gateway.example.test\"}"))
                    .build());
            AtomicReference<List<Object>> invocation = new AtomicReference<>();
            GatewayTool tool = new GatewayTool(gatewayClient,
                    (endpoint, authorization, sessionId, toolName, arguments) -> {
                        invocation.set(List.of(endpoint, authorization, sessionId, toolName, arguments));
                        return Mono.just(ToolResultBlock.text("real MCP result"));
                    });
            AgentscopeRequestContext runtimeContext = new AgentscopeRequestContext(
                    "session-1", "user-1", "request-1", "unit-test-token");

            ToolResultBlock result = tool.callAsync(ToolCallParam.builder()
                    .context(runtimeContext.toToolExecutionContext())
                    .input(Map.of(
                            "gateway_id", gatewayId,
                            "tool_name", "target___echo",
                            "arguments", Map.of("message", "hello")))
                    .build()).block();

            assertNotNull(result);
            assertEquals("real MCP result", resultText(result));
            assertEquals("https://gateway.example.test/mcp", invocation.get().get(0));
            assertEquals("Bearer unit-test-token", invocation.get().get(1));
            assertEquals("session-1", invocation.get().get(2));
            assertEquals("target___echo", invocation.get().get(3));
            assertEquals(Map.of("message", "hello"), invocation.get().get(4));
        }

        @Test
        void rejectsInvalidGatewayIdBeforeNetworkCall() {
            GatewayClient gatewayClient = mock(GatewayClient.class);
            GatewayTool tool = new GatewayTool(gatewayClient,
                    (endpoint, authorization, sessionId, toolName, arguments) ->
                            Mono.error(new AssertionError("must not invoke")));

            ToolResultBlock result = tool.callAsync(ToolCallParam.builder()
                    .input(Map.of("gateway_id", "not-a-uuid", "tool_name", "echo"))
                    .build()).block();

            assertNotNull(result);
            assertTrue(resultText(result).startsWith("Error:"));
            verifyNoInteractions(gatewayClient);
        }

        @Test
        void rejectsOversizedToolInputBeforeNetworkCall() {
            GatewayClient gatewayClient = mock(GatewayClient.class);
            GatewayTool tool = new GatewayTool(gatewayClient);

            ToolResultBlock result = tool.callAsync(ToolCallParam.builder()
                    .input(Map.of(
                            "gateway_id", "01234567-89ab-cdef-0123-456789abcdef",
                            "tool_name", "x".repeat(257)))
                    .build()).block();

            assertNotNull(result);
            assertTrue(resultText(result).startsWith("Error:"));
            verifyNoInteractions(gatewayClient);
        }

        @Test
        void preservesCustomInvokerResult() throws Exception {
            String gatewayId = "01234567-89ab-cdef-0123-456789abcdef";
            GatewayClient gatewayClient = mock(GatewayClient.class);
            when(gatewayClient.getGateway(gatewayId)).thenReturn(RequestResult.builder()
                    .success(true)
                    .statusCode(200)
                    .data(JsonUtils.MAPPER.readTree(
                            "{\"endpoint_url\":\"https://gateway.example.test\"}"))
                    .build());
            GatewayTool tool = new GatewayTool(gatewayClient,
                    (endpoint, authorization, sessionId, toolName, arguments) ->
                            Mono.just(ToolResultBlock.text("still running")));

            ToolResultBlock result = tool.callAsync(ToolCallParam.builder()
                    .input(Map.of("gateway_id", gatewayId, "tool_name", "target___echo"))
                    .build()).block();

            assertNotNull(result);
            assertEquals("still running", resultText(result));
        }

        @Test
        void rejectsNullDependencies() {
            GatewayClient gatewayClient = mock(GatewayClient.class);
            assertThrows(NullPointerException.class, () -> new GatewayTool(null));
            assertThrows(NullPointerException.class, () -> new GatewayTool(gatewayClient, null));
        }
    }

    // ========================
    // CodeInterpreterTool — AgentTool interface contract
    // ========================

    @Nested
    @DisplayName("CodeInterpreterTool implements AgentTool")
    class CodeInterpreterToolTests {

        @Test
        void implementsAgentTool() {
            assertTrue(AgentTool.class.isAssignableFrom(CodeInterpreterTool.class));
        }

        @Test
        void hasCorrectName() {
            CodeInterpreterTool tool = new CodeInterpreterTool(mock(CodeInterpreterClient.class));
            assertEquals("code_interpreter", tool.getName());
        }

        @Test
        void hasDescription() {
            CodeInterpreterTool tool = new CodeInterpreterTool(mock(CodeInterpreterClient.class));
            assertNotNull(tool.getDescription());
            assertTrue(tool.getDescription().contains("code"));
        }

        @Test
        void parametersSchemaRequiresCode() {
            CodeInterpreterTool tool = new CodeInterpreterTool(mock(CodeInterpreterClient.class));
            Map<String, Object> params = tool.getParameters();
            assertEquals("object", params.get("type"));

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) params.get("required");
            assertTrue(required.contains("code"));
        }

        @Test
        void rejectsInvalidInputWithoutCallingService() {
            CodeInterpreterClient interpreter = mock(CodeInterpreterClient.class);
            CodeInterpreterTool tool = new CodeInterpreterTool(interpreter);

            ToolResultBlock result = tool.callAsync(ToolCallParam.builder()
                    .input(Map.of("code", 42))
                    .build()).block();

            assertNotNull(result);
            assertTrue(resultText(result).startsWith("Error:"));
            verifyNoInteractions(interpreter);
        }

        @Test
        void executesCodeAndSerializesResultAsJson() {
            CodeInterpreterClient interpreter = mock(CodeInterpreterClient.class);
            when(interpreter.executeCode("print('ok')", "python", false))
                    .thenReturn(Map.of("stdout", "ok"));
            CodeInterpreterTool tool = new CodeInterpreterTool(interpreter);

            ToolResultBlock result = tool.callAsync(ToolCallParam.builder()
                    .input(Map.of("code", "print('ok')"))
                    .build()).block();

            assertNotNull(result);
            assertEquals("{\"stdout\":\"ok\"}",
                    ((TextBlock) result.getOutput().get(0)).getText());
        }

        @Test
        void rejectsNullClient() {
            assertThrows(NullPointerException.class, () -> new CodeInterpreterTool(null));
        }
    }

    // ========================
    // AgentscopeRuntimeHost — context bridging
    // ========================

    @Nested
    @DisplayName("AgentscopeRuntimeHost context bridging")
    class RuntimeHostTests {

        @Test
        void bridgeContextMapsSessionId() {
            RequestContext rc = new RequestContext("req-1", "sess-123", "user-456", "token-789");
            AgentscopeRequestContext ctx = AgentscopeRuntimeHost.bridgeContext(rc);
            assertEquals("sess-123", ctx.sessionId());
        }

        @Test
        void bridgeContextMapsUserId() {
            RequestContext rc = new RequestContext("req-1", "sess-123", "user-456", "token-789");
            AgentscopeRequestContext ctx = AgentscopeRuntimeHost.bridgeContext(rc);
            assertEquals("user-456", ctx.userId());
        }

        @Test
        void bridgeContextMapsRequestId() {
            RequestContext rc = new RequestContext("req-1", "sess-123", "user-456", "token-789");
            AgentscopeRequestContext ctx = AgentscopeRuntimeHost.bridgeContext(rc);
            assertEquals("req-1", ctx.requestId());
        }

        @Test
        void bridgeContextMapsWorkloadAccessToken() {
            RequestContext rc = new RequestContext("req-1", "sess-123", "user-456", "token-789");
            AgentscopeRequestContext ctx = AgentscopeRuntimeHost.bridgeContext(rc);
            assertEquals("token-789", ctx.workloadAccessToken());
        }

        @Test
        void bridgeContextHandlesNullFields() {
            RequestContext rc = new RequestContext(null, null, null, null);
            AgentscopeRequestContext ctx = AgentscopeRuntimeHost.bridgeContext(rc);
            assertNotNull(ctx);
            assertNull(ctx.sessionId());
            assertNull(ctx.userId());
        }

        @Test
        void rejectsNullDependenciesAndContext() {
            AgentArtsRuntimeApp app = mock(AgentArtsRuntimeApp.class);
            assertThrows(
                    NullPointerException.class,
                    () -> new AgentscopeRuntimeHost(null, (payload, ctx) -> payload));
            assertThrows(NullPointerException.class, () -> new AgentscopeRuntimeHost(app, null));
            assertThrows(
                    NullPointerException.class,
                    () -> new AgentscopeRuntimeHost(app, (payload, ctx) -> payload, null));
            assertThrows(NullPointerException.class, () -> AgentscopeRuntimeHost.bridgeContext(null));
            verifyNoInteractions(app);
        }

        @Test
        void registersApplicationReadinessProbe() {
            AgentArtsRuntimeApp app = mock(AgentArtsRuntimeApp.class);
            Supplier<PingStatus> probe = () -> PingStatus.HEALTHY_BUSY;

            new AgentscopeRuntimeHost(app, (payload, ctx) -> payload, probe);

            verify(app).setPingHandler(probe);
            verify(app).setEntrypoint(any(java.util.function.BiFunction.class));
        }
    }

    @Nested
    @DisplayName("AgentscopeSessionExecutor bounded session execution")
    class SessionExecutorTests {

        @Test
        void loadsCallsAndPersistsSuccessfulState() {
            AgentStateStore session = mock(AgentStateStore.class);
            ReActAgent agent = mock(ReActAgent.class);
            Msg reply = Msg.builder().role(MsgRole.ASSISTANT).textContent("ok").build();
            when(agent.call(any(List.class), any(RuntimeContext.class))).thenReturn(Mono.just(reply));
            AgentscopeRequestContext context = new AgentscopeRequestContext(
                    "session", "user", "request", null);

            Msg result = new AgentscopeSessionExecutor(session, Duration.ofSeconds(1))
                    .call(agent, "hello", context);

            assertSame(reply, result);
        }

        @Test
        void rejectsInvalidConfigurationAndMessages() {
            assertThrows(NullPointerException.class,
                    () -> new AgentscopeSessionExecutor(null));
            assertThrows(IllegalArgumentException.class,
                    () -> new AgentscopeSessionExecutor(
                            new InMemoryAgentStateStore(), Duration.ZERO));
            AgentscopeSessionExecutor executor = new AgentscopeSessionExecutor(
                    new InMemoryAgentStateStore(), Duration.ofSeconds(1));
            assertThrows(IllegalArgumentException.class,
                    () -> executor.call(mock(ReActAgent.class), " ",
                            new AgentscopeRequestContext(null, null, null, null)));
        }
    }

    // ========================
    // MessageConverter — bidirectional conversion
    // ========================

    @Nested
    @DisplayName("MessageConverter bidirectional conversion")
    class MessageConverterTests {

        @Test
        void textMessageToTextBlock() {
            TextMessage msg = new TextMessage("user", "Hello world");
            TextBlock block = MessageConverter.toTextBlock(msg);
            assertEquals("Hello world", block.getText());
        }

        @Test
        void textBlockToTextMessage() {
            TextBlock block = TextBlock.builder().text("Hello world").build();
            TextMessage msg = MessageConverter.toTextMessage(block, "assistant");
            assertEquals("Hello world", msg.getContent());
            assertEquals("assistant", msg.getRole());
        }

        @Test
        void toolCallMessageToToolUseBlock() {
            ToolCallMessage msg = new ToolCallMessage("call-1", "search", "{\"query\":\"test\"}");
            ToolUseBlock block = MessageConverter.toToolUseBlock(msg);
            assertEquals("call-1", block.getId());
            assertEquals("search", block.getName());
            assertEquals("test", block.getInput().get("query"));
        }

        @Test
        void toolUseBlockToToolCallMessage() {
            ToolUseBlock block = ToolUseBlock.builder()
                    .id("call-2")
                    .name("execute")
                    .input(Map.of("code", "print(1)"))
                    .build();
            ToolCallMessage msg = MessageConverter.toToolCallMessage(block);
            assertEquals("call-2", msg.getId());
            assertEquals("execute", msg.getName());
            assertTrue(msg.getArguments().contains("print(1)"));
        }

        @Test
        void toolResultMessageToToolResultBlock() {
            ToolResultMessage msg = new ToolResultMessage("call-1", "result data");
            ToolResultBlock block = MessageConverter.toToolResultBlock(msg);
            assertEquals("call-1", block.getId());
            assertFalse(block.getOutput().isEmpty());
            assertTrue(block.getMetadata().isEmpty());
        }

        @Test
        void toolResultBlockToToolResultMessage() {
            TextBlock textOut = TextBlock.builder().text("output text").build();
            ToolResultBlock block = ToolResultBlock.of("call-1", "tool", textOut);
            ToolResultMessage msg = MessageConverter.toToolResultMessage(block);
            assertEquals("call-1", msg.getToolCallId());
            assertEquals("output text", msg.getContent());
        }

        @Test
        void toolResultMetadataSurvivesRoundTrip() {
            ToolResultBlock block = ToolResultBlock.of(
                    "call-1", "tool", TextBlock.builder().text("failed").build(),
                    Map.of("trace_id", "trace-1"));

            ToolResultMessage memoryMessage = MessageConverter.toToolResultMessage(block);
            ToolResultBlock restored = MessageConverter.toToolResultBlock(memoryMessage);

            assertEquals("trace-1", restored.getMetadata().get("trace_id"));
        }

        @Test
        void toolResultRoundTripPreservesUrlAsset() {
            ImageBlock image = ImageBlock.builder()
                    .source(URLSource.builder().url("https://example.invalid/image.png").build())
                    .build();
            ToolResultBlock source = ToolResultBlock.of(
                            "call-id",
                            "image-tool",
                            List.of(TextBlock.builder().text("caption").build(), image));

            ToolResultMessage memory = MessageConverter.toToolResultMessage(source);
            ToolResultBlock restored = MessageConverter.toToolResultBlock(memory);

            assertNotNull(memory.getAssetRef());
            assertEquals(2, restored.getOutput().size());
            assertInstanceOf(ImageBlock.class, restored.getOutput().get(1));
            ImageBlock restoredImage = (ImageBlock) restored.getOutput().get(1);
            assertEquals(
                    "https://example.invalid/image.png",
                    ((URLSource) restoredImage.getSource()).getUrl());
        }

        @Test
        void rejectsUnsupportedToolResultBlocksInsteadOfDroppingThem() {
            ThinkingBlock thinking = ThinkingBlock.builder().thinking("internal").build();
            ToolResultBlock source = ToolResultBlock.of("call-id", "tool", thinking);

            assertThrows(
                    MessageConversionException.class,
                    () -> MessageConverter.toToolResultMessage(source));
        }

        @Test
        void acceptsToolMethodResultWithoutCallIdentity() {
            ToolResultMessage message = MessageConverter.toToolResultMessage(
                    ToolResultBlock.text("complete"));
            assertEquals("", message.getToolCallId());
            assertEquals("complete", message.getContent());
        }

        @Test
        void serializationFailureIsNotSilentlyReplaced() {
            Map<String, Object> recursive = new HashMap<>();
            recursive.put("self", recursive);
            ToolUseBlock block = ToolUseBlock.builder()
                    .id("call-recursive")
                    .name("recursive")
                    .input(recursive)
                    .build();

            assertThrows(MessageConversionException.class,
                    () -> MessageConverter.toToolCallMessage(block));
        }

        @Test
        void roundTripTextPreservesContent() {
            TextMessage original = new TextMessage("user", "Round trip test");
            TextBlock block = MessageConverter.toTextBlock(original);
            TextMessage roundTripped = MessageConverter.toTextMessage(block, "user");
            assertEquals(original.getContent(), roundTripped.getContent());
        }
    }

    // ========================
    // Helper: test State implementation (Jackson-serializable)
    // ========================

    public static class TestState implements State {
        public String value;
        public TestState() {} // Jackson default constructor
        public TestState(String value) { this.value = value; }
    }

    // ========================
    // Fake MemoryClient for testing (avoids Mockito + JDK 26 incompatibility)
    // ========================

    static class FakeMemoryClient extends MemoryClient {
        boolean sessionCreated = false;
        boolean messagesAdded = false;
        boolean closed = false;
        boolean failSessionCreation = false;
        boolean failWrites = false;
        boolean failReads = false;
        boolean truncatePages = false;

        /** In-memory message storage: memSessionId → list of MessageInfo */
        final Map<String, List<MessageInfo>> messageStore = new ConcurrentHashMap<>();
        final Set<String> createdSessions = ConcurrentHashMap.newKeySet();

        FakeMemoryClient() {
            super("cn-southwest-2", "fake-test-key");
        }

        @Override
        public SessionInfo createMemorySession(String spaceId, String id, String actorId, String assistantId) {
            if (failSessionCreation) {
                throw new IllegalStateException("simulated session creation failure");
            }
            sessionCreated = true;
            // If id is null, generate a UUID (matching real Memory API behavior)
            String sessionId = (id != null) ? id : java.util.UUID.randomUUID().toString();
            if (!createdSessions.add(sessionId)) {
                throw new APIException(409, "session_exists", "session already exists");
            }
            String json = "{\"id\":\"" + sessionId + "\",\"space_id\":\"" + spaceId + "\"}";
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, SessionInfo.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public MessageBatchResponse addMessages(String spaceId, String sessionId, List<?> messages,
                                                  Long timestamp, String idempotencyKey, boolean isForceExtract) {
            if (failWrites) {
                throw new IllegalStateException("simulated write failure");
            }
            if (messages.size() > 100) {
                throw new IllegalArgumentException("simulated service batch limit");
            }
            messagesAdded = true;
            List<MessageInfo> stored = messageStore.computeIfAbsent(sessionId, k -> new ArrayList<>());
            int seq = stored.size();
            for (Object m : messages) {
                if (m instanceof TextMessage tm) {
                    Map<String, Object> part = new HashMap<>();
                    part.put("type", "text");
                    part.put("text", tm.getContent());

                    String json = "{\"id\":\"msg-" + (++seq) + "\",\"session_id\":\"" + sessionId
                            + "\",\"seq\":" + seq + ",\"role\":\"" + tm.getRole()
                            + "\",\"parts\":" + new com.fasterxml.jackson.databind.ObjectMapper()
                            .valueToTree(List.of(part)).toString() + "}";
                    try {
                        MessageInfo mi = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readValue(json, MessageInfo.class);
                        stored.add(mi);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            return new MessageBatchResponse();
        }

        @Override
        public List<MessageInfo> getLastKMessages(String sessionId, int k, String spaceId) {
            if (failReads) {
                throw new IllegalStateException("simulated read failure");
            }
            if (k > 100) {
                throw new IllegalArgumentException("simulated service page limit");
            }
            List<MessageInfo> all = messageStore.getOrDefault(sessionId, List.of());
            int start = Math.max(0, all.size() - k);
            return new ArrayList<>(all.subList(start, all.size()));
        }

        @Override
        public MessageListResponse listMessages(String spaceId, String sessionId, int limit, int offset) {
            if (failReads) {
                throw new IllegalStateException("simulated read failure");
            }
            if (limit > 100) {
                throw new IllegalArgumentException("simulated service page limit");
            }
            List<MessageInfo> all = messageStore.getOrDefault(sessionId, List.of());
            int end = Math.min(all.size(), offset + limit);
            List<MessageInfo> page = (offset < all.size()) ? all.subList(offset, end) : List.of();
            if (truncatePages && offset >= 100) {
                page = List.of();
            }
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.node.ObjectNode response = mapper.createObjectNode();
                response.set("items", mapper.valueToTree(page));
                response.put("total", all.size());
                response.put("limit", limit);
                response.put("offset", offset);
                return mapper.treeToValue(response, MessageListResponse.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
