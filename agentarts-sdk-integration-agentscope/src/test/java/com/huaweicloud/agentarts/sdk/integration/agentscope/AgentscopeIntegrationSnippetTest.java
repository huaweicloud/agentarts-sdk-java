package com.huaweicloud.agentarts.sdk.integration.agentscope;

import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeRuntimeHost;
import com.huaweicloud.agentarts.sdk.integration.agentscope.state.MemoryAgentStateStore;
import com.huaweicloud.agentarts.sdk.integration.agentscope.tool.CodeInterpreterTool;
import com.huaweicloud.agentarts.sdk.integration.agentscope.tool.GatewayTool;
import com.huaweicloud.agentarts.sdk.memory.MemoryClient;
import com.huaweicloud.agentarts.sdk.gateway.GatewayClient;
import com.huaweicloud.agentarts.sdk.runtime.AgentArtsRuntimeApp;
import com.huaweicloud.agentarts.sdk.tools.CodeInterpreterClient;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Compiles + constructs the README "agentscope-java Integration" snippet
 * end-to-end (without starting the HTTP server or making cloud/model calls),
 * so the documented wiring stays verifiably buildable.
 */
class AgentscopeIntegrationSnippetTest {

    @Test
    void snippetConstructsSuccessfully() {
        // Clients — construction is cloud-free; AK/SK are read lazily at sign time.
        GatewayClient gatewayClient = new GatewayClient();
        CodeInterpreterClient interpreterClient = new CodeInterpreterClient("cn-southwest-2");
        MemoryClient memoryClient = new MemoryClient("cn-southwest-2", "dummy-api-key");
        String spaceId = "dummy-space";

        // Register AgentArts tools (Gateway, Code Interpreter) as agentscope AgentTools.
        Toolkit toolkit = new Toolkit();
        toolkit.registerAgentTool(new GatewayTool(gatewayClient));
        toolkit.registerAgentTool(new CodeInterpreterTool(interpreterClient));

        // Use AgentArts Memory as the agent's state store (client + spaceId).
        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey("dummy-key").modelName("gpt-4o").stream(true).build();

        MemoryAgentStateStore session = new MemoryAgentStateStore(memoryClient, spaceId);
        ReActAgent agent = ReActAgent.builder()
                .name("my-agent").model(model).toolkit(toolkit)
                .build();
        // build() success is the smoke test — no filler assertNotNull needed.

        // Host the agent behind AgentArts Runtime — POST /invocations dispatches to it.
        // (app.run(port) omitted — it would block on the HTTP server.)
        AgentArtsRuntimeApp app = new AgentArtsRuntimeApp();
        new AgentscopeRuntimeHost(app, (payload, ctx) -> {
            String message = (String) payload.getOrDefault("message", "");
            synchronized (agent) {
                Msg input = Msg.builder().role(MsgRole.USER).textContent(message).build();
                var reply = agent.call(input).block();
                return Map.of("reply", reply != null ? reply.getTextContent() : "");
            }
        });
        // Construction of AgentscopeRuntimeHost is the smoke test — no filler assertNotNull.

        gatewayClient.close();
        interpreterClient.close();
        session.close();
    }
}
