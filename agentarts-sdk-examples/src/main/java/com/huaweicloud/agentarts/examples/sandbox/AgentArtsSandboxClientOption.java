package com.huaweicloud.agentarts.examples.sandbox;

import com.huaweicloud.agentarts.examples.client.AgentRunClient;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;

public class AgentArtsSandboxClientOption extends SandboxClientOptions {
    private String baseUrl;

    private String apiKey;

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
        return new AgentArtsBasedSandboxClient(new AgentRunClient(this.getBaseUrl(), this.getApiKey()));
    }
}
