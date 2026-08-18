package com.huaweicloud.agentarts.examples;

import com.huaweicloud.agentarts.examples.converter.OpenAiEventConverter;
import com.huaweicloud.agentarts.examples.sandbox.AgentArtsBasedSandboxFilesystemSpec;
import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeRuntimeHost;
import com.huaweicloud.agentarts.sdk.memory.MemoryClient;
import com.huaweicloud.agentarts.sdk.runtime.AgentArtsRuntimeApp;
import io.agentscope.core.message.Msg;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class SandboxIntegrationExample {
    private static final AgentArtsRuntimeApp APP = new AgentArtsRuntimeApp();

    private static final MemoryClient MEMORY_CLIENT = new MemoryClient(System.getenv("HUAWEICLOUD_SDK_REGION"), System.getenv("MEMORY_API_KEY"));

    private static final HarnessAgent AGENT = buildAgent();

    public static Toolkit buildToolkit() {
        Toolkit toolkit = new Toolkit();
        return toolkit;
    }

    public static OpenAIChatModel buildModel() {
        return OpenAIChatModel.builder()
                .baseUrl("https://api.modelarts-maas.com")
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("GLM-5.1")
                .stream(true).build();
    }

    public static HarnessAgent buildAgent() {
        AgentArtsBasedSandboxFilesystemSpec filesystemSpec = new AgentArtsBasedSandboxFilesystemSpec(
                System.getenv("AGENTRUN_BASE_URL"), System.getenv("AGENT_RUN_API_KEY"));
        JsonFileAgentStateStore stateStore = new JsonFileAgentStateStore(
                Path.of("/mem") // 状态将保存在应用主机的这个目录下
        );
        try {
            return HarnessAgent.builder()
                    .name("my-agent")
                    .model(buildModel())
                    .workspace("/workspace")
                    .toolkit(buildToolkit())
                    .filesystem(filesystemSpec)
                    .disableSubagents()
                    .maxIters(50)
                    .skillRepository(new ClasspathSkillRepository("skills"))
                    .stateStore(stateStore).build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        new AgentscopeRuntimeHost(APP, (payload, ctx) -> {
            OpenAiEventConverter converter = new OpenAiEventConverter("GLM-5.1");
            String message = (String) payload.getOrDefault("message", "");
            return AGENT.streamEvents(List.of(Msg.builder().textContent(message).build()), ctx.runtimeContext())
                    .map(event -> {
                        String result = converter.convertEventToOpenAiChunk(event);
                        return result == null ? "" : result;
                    }).filter(StringUtils::isNotEmpty);
        });
        APP.run(8080);
    }
}
