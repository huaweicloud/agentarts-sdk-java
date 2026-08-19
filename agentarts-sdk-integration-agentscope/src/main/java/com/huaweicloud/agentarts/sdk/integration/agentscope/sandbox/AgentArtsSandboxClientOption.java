package com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox;

import com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox.client.AgentRunClient;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;

/**
 * Client options for AgentArts sandbox, extending {@link SandboxClientOptions}.
 *
 * <p>Holds AgentArts-specific configuration: base URL, API key, and sandbox name.
 * Creates an {@link AgentArtsBasedSandboxClient} via {@link #createClient()}.</p>
 */
public class AgentArtsSandboxClientOption extends SandboxClientOptions {
    private String baseUrl;

    private String apiKey;

    private String sandboxName;

    public String getSandboxName() {
        return sandboxName;
    }

    public void setSandboxName(String sandboxName) {
        this.sandboxName = sandboxName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getType() {
        return SandboxConstants.SANDBOX_TYPE;
    }

    @Override
    public SandboxClient<? extends SandboxClientOptions> createClient() {
        return new AgentArtsBasedSandboxClient(new AgentRunClient(this.getBaseUrl(), this.sandboxName, this.getApiKey()));
    }
}
