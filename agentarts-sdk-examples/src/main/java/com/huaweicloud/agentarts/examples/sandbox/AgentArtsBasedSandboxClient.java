package com.huaweicloud.agentarts.examples.sandbox;

import com.huaweicloud.agentarts.examples.client.AgentRunClient;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentArtsBasedSandboxClient implements SandboxClient<AgentArtsSandboxClientOption> {
    private static final Logger LOG = LoggerFactory.getLogger(AgentArtsBasedSandboxClient.class);

    private AgentRunClient agentRunClient;

    public AgentArtsBasedSandboxClient(AgentRunClient agentRunClient) {
        this.agentRunClient = agentRunClient;
    }

    @Override
    public Sandbox create(WorkspaceSpec workspaceSpec, SandboxSnapshotSpec snapshotSpec, AgentArtsSandboxClientOption options) {
        LOG.info("creating agentarts sandbox");
        return new AgentArtsSandbox(agentRunClient);
    }

    @Override
    public Sandbox resume(SandboxState state) {
        AgentArtsSandbox sandbox = new AgentArtsSandbox(agentRunClient);
        sandbox.resume(state);
        return sandbox;
    }

    @Override
    public void delete(Sandbox sandbox) {
    }

    @Override
    public String serializeState(SandboxState state) {
        LOG.info("begin serialize:{}", state.getSessionId());
        return state.getSessionId();
    }

    @Override
    public SandboxState deserializeState(String sessionId) {
        LOG.info("begin deserialize:{}", sessionId);
        AgentArtsSandboxState state = new AgentArtsSandboxState();
        state.setSessionId(sessionId);
        return state;
    }
}
