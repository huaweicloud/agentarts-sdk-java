package com.huaweicloud.agentarts.examples;

import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeRequestContext;
import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeRuntimeHost;
import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeSessionExecutor;
import com.huaweicloud.agentarts.sdk.integration.agentscope.state.MemoryAgentStateStore;
import com.huaweicloud.agentarts.sdk.memory.MemoryClient;
import com.huaweicloud.agentarts.sdk.runtime.AgentArtsRuntimeApp;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Scanner;

/**
 * Production-oriented AgentScope integration example.
 *
 * <p>The example builds a fresh agent for every request, shares the model, uses
 * bounded execution, serializes calls per user/session, and persists state only
 * after a successful response. No credential is ever written to output.</p>
 */
public final class AgentScopeIntegrationExample {

    private static final String SYSTEM_PROMPT =
            "You are a helpful assistant. Use tools when they improve accuracy.";

    private AgentScopeIntegrationExample() {
    }

    public static void main(String[] args) {
        String apiKey = requireEnvironment("OPENAI_API_KEY");
        OpenAIChatModel model = createModel(apiKey);
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new TimeTools());
        AgentStateStore stateStore = createAgentStore();
        int timeoutSeconds = positiveIntEnvironment("AGENT_TIMEOUT_SECONDS", 120);
        AgentscopeSessionExecutor executor = new AgentscopeSessionExecutor(
                stateStore, Duration.ofSeconds(timeoutSeconds));

        if ("server".equalsIgnoreCase(System.getenv("RUNTIME_MODE"))) {
            runServer(model, toolkit, executor, stateStore);
        } else {
            try {
                runInteractive(model, toolkit, executor, stateStore);
            } finally {
                stateStore.close();
            }
        }
    }

    private static OpenAIChatModel createModel(String apiKey) {
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(System.getenv().getOrDefault("OPENAI_MODEL_NAME", "gpt-4o"))
                .stream(true);
        String baseUrl = System.getenv("OPENAI_BASE_URL");
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl.trim());
        }
        return builder.build();
    }

    private static AgentStateStore createAgentStore() {
        String memoryKey = System.getenv("HUAWEICLOUD_SDK_MEMORY_API_KEY");
        String spaceId = System.getenv("AGENTARTS_MEMORY_SPACE_ID");
        if (isConfigured(memoryKey) && isConfigured(spaceId)) {
            String region = System.getenv().getOrDefault(
                    "HUAWEICLOUD_SDK_REGION", "cn-southwest-2");
            return new MemoryAgentStateStore(new MemoryClient(region, memoryKey), spaceId);
        }
        if (isConfigured(memoryKey) || isConfigured(spaceId)) {
            throw new IllegalStateException(
                    "HUAWEICLOUD_SDK_MEMORY_API_KEY and AGENTARTS_MEMORY_SPACE_ID must be configured together");
        }
        return new InMemoryAgentStateStore();
    }

    private static ReActAgent createAgent(
            OpenAIChatModel model, Toolkit toolkit, AgentStateStore stateStore) {
        return ReActAgent.builder()
                .name("agentarts-assistant")
                .sysPrompt(SYSTEM_PROMPT)
                .model(model)
                .stateStore(stateStore)
                .toolkit(toolkit.copy())
                .maxIters(8)
                .build();
    }

    private static void runInteractive(
            OpenAIChatModel model, Toolkit toolkit, AgentscopeSessionExecutor executor, AgentStateStore stateStore) {
        AgentscopeRequestContext context = new AgentscopeRequestContext(
                "interactive", System.getProperty("user.name", "local"), "interactive", null);
        Scanner scanner = new Scanner(System.in);
        System.out.println("AgentScope assistant ready. Type 'exit' to stop.");
        while (scanner.hasNextLine()) {
            String message = scanner.nextLine();
            if ("exit".equalsIgnoreCase(message.trim())) {
                return;
            }
            if (!message.isBlank()) {
                Msg response = executor.call(createAgent(model, toolkit, stateStore), message, context);
                System.out.println(response.getTextContent());
            }
        }
    }

    private static void runServer(
            OpenAIChatModel model, Toolkit toolkit, AgentscopeSessionExecutor executor,
            AgentStateStore stateStore) {
        AgentArtsRuntimeApp app = new AgentArtsRuntimeApp();
        app.registerManagedResource(stateStore::close);
        new AgentscopeRuntimeHost(app, (payload, context) -> {
            Object value = payload.get("message");
            if (!(value instanceof String message) || message.isBlank()) {
                throw new IllegalArgumentException("message must be a non-blank string");
            }
            Msg response = executor.call(createAgent(model, toolkit, stateStore), message, context);
            return Map.of("reply", response.getTextContent());
        });
        int port = positiveIntEnvironment("PORT", 8080);
        String host = System.getenv().getOrDefault("HOST", "0.0.0.0");
        app.runUntilShutdown(port, host);
    }

    private static int positiveIntEnvironment(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a positive integer", e);
        }
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (!isConfigured(value)) {
            throw new IllegalStateException(name + " must be configured");
        }
        return value;
    }

    private static boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }

    public static final class TimeTools {
        @Tool(name = "current_time", description = "Return the current time with UTC offset")
        public String currentTime() {
            return OffsetDateTime.now().toString();
        }
    }
}
