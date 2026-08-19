package com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox;

import com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox.client.AgentRunClient;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxState;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Sandbox implementation backed by an AgentArts Runtime session.
 *
 * <p>Manages the lifecycle of an AgentArts session (start, stop, close)
 * and delegates command execution to {@link AgentRunClient}. Supports
 * session resumption via {@link #resume(SandboxState)}.</p>
 */
public class AgentArtsSandbox implements Sandbox {
    private static final Logger LOG = LoggerFactory.getLogger(AgentArtsSandbox.class);

    private AgentRunClient agentRunClient;

    private String sessionId;

    private boolean running;

    public AgentArtsSandbox(AgentRunClient agentRunClient) {
        this.agentRunClient = agentRunClient;
    }

    public void resume(SandboxState state) {
        this.sessionId = state.getSessionId();
        this.running = true;
    }

    @Override
    public void start() throws Exception {
        if (StringUtils.isNotEmpty(sessionId)) {
            return;
        }
        this.sessionId = agentRunClient.createSession();
        this.running = true;
        LOG.info("create agentarts session:{} success", sessionId);
    }

    @Override
    public void stop() throws Exception {
        LOG.info("stop agentarts session:{} success", sessionId);
    }

    @Override
    public void close() throws Exception {
        if (StringUtils.isNotEmpty(sessionId)) {
            agentRunClient.stopSession(this.sessionId);
            this.running = false;
        }
        LOG.info("close agentarts session:{} success", sessionId);
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    @Override
    public SandboxState getState() {
        SandboxState state = new AgentArtsSandboxState();
        state.setSessionId(this.sessionId);
        return state;
    }

    @Override
    public ExecResult exec(RuntimeContext runtimeContext, String command, Integer timeoutSeconds) throws Exception {
        if (StringUtils.isEmpty(this.sessionId)) {
            throw new RuntimeException("sand box not inited");
        }
        return this.agentRunClient.executeCommand(this.sessionId, command);
    }

    @Override
    public InputStream persistWorkspace() throws Exception {
        return null;
    }

    @Override
    public void hydrateWorkspace(InputStream archive) throws Exception {
    }
}
