package com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox;

import com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox.client.AgentRunClient;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SandboxClient implementation backed by AgentArts Runtime.
 *
 * <p>Provides sandbox lifecycle operations (create, resume, delete) and
 * state serialization for agentscope sandbox integration. Each sandbox
 * instance is backed by an {@link AgentRunClient} that communicates with
 * the AgentArts Runtime data plane.</p>
 */
public class AgentArtsBasedSandboxClient implements SandboxClient<AgentArtsSandboxClientOption> {
    private static final Logger LOG = LoggerFactory.getLogger(AgentArtsBasedSandboxClient.class);

    private final AgentRunClient agentRunClient;

    public AgentArtsBasedSandboxClient(AgentRunClient agentRunClient) {
        this.agentRunClient = agentRunClient;
    }

    @Override
    public Sandbox create(WorkspaceSpec workspaceSpec, SandboxSnapshotSpec snapshotSpec, AgentArtsSandboxClientOption options) {
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
        if (sandbox == null) {
            return;
        }
        try {
            sandbox.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
