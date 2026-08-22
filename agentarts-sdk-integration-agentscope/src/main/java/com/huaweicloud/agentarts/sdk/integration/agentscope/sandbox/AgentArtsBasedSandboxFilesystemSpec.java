package com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox;

import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;

/**
 * A simple sandbox filesystem spec implementation based on AgentArts Runtime.
 *
 * <p>This class provides basic sandbox functionality by wiring up AgentArts sandbox
 * client options, workspace root, and a no-op snapshot spec. For more complex sandbox
 * control requirements, you can either extend this class or refer to the agentscope
 * documentation for custom sandbox management:
 * <a href="https://java.agentscope.io/v2/zh/docs/harness/sandbox.html#id6">
 * https://java.agentscope.io/v2/zh/docs/harness/sandbox.html#id6</a></p>
 */
public class AgentArtsBasedSandboxFilesystemSpec extends SandboxFilesystemSpec {
    private final String baseUrl;

    private final String apiKey;

    private final String sandboxName;

    private final String workspaceRoot;

    private final AgentArtsSandboxClientOption options;

    public AgentArtsBasedSandboxFilesystemSpec(String baseUrl, String apiKey, String sandboxName, String workspaceRoot) {
        super();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.sandboxName = sandboxName;
        this.workspaceRoot = workspaceRoot;

        options = new AgentArtsSandboxClientOption();
        options.setSandboxName(sandboxName);
        options.setBaseUrl(baseUrl);
        options.setApiKey(apiKey);
    }

    @Override
    protected SandboxClient<?> createClient() {
        return options.createClient();
    }

    @Override
    protected SandboxClientOptions clientOptions() {
        return options;
    }

    @Override
    protected SandboxSnapshotSpec snapshotSpec() {
        return new NoopSnapshotSpec();
    }

    @Override
    protected WorkspaceSpec workspaceSpec() {
        WorkspaceSpec spec = new WorkspaceSpec();
        spec.setRoot(workspaceRoot);
        return spec;
    }
}
