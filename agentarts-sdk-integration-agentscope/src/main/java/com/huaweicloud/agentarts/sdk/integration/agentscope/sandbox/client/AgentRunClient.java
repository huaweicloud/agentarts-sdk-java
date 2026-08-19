package com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.agentarts.sdk.core.APIException;
import com.huaweicloud.agentarts.sdk.service.runtime.RuntimeClient;
import io.agentscope.harness.agent.sandbox.ExecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Client for AgentArts Runtime session and command execution operations.
 *
 * <p>Wraps {@link RuntimeClient} data-plane calls to create/stop sessions
 * and execute commands within an AgentArts sandbox session. Responses are
 * deserialized into {@link SessionStartResponse} and {@link ExecData}.</p>
 */
public class AgentRunClient {
    private static final Logger LOG = LoggerFactory.getLogger(AgentRunClient.class);

    private final String sandboxRuntimeName;

    private final RuntimeClient runtimeClient = new RuntimeClient();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentRunClient(String baseUrl, String sandboxRuntimeName, String apiKey) {
        runtimeClient.setDataPlaneEndpoint(baseUrl);
        runtimeClient.setAuthToken(apiKey);
        this.sandboxRuntimeName = sandboxRuntimeName;
    }

    public String createSession() {
        Map<String, Object> result = runtimeClient.startSession(sandboxRuntimeName);
        if (result == null) {
            LOG.error("create session response invalid");
            throw new RuntimeException("create session response invalid");
        }
        SessionStartResponse response = objectMapper.convertValue(result, SessionStartResponse.class);
        if (response.getData() == null) {
            LOG.error("create session response invalid");
            throw new RuntimeException("create session response invalid");
        }
        return response.getData().getSessionId();
    }

    public void stopSession(String sessionId) {
        try {
            runtimeClient.stopSession(sandboxRuntimeName, sessionId);
        } catch (APIException e) {
            if (e.getStatusCode() == 404) {
                LOG.warn("session:{} already not exist", sessionId);
                return;
            }
            LOG.error("stop session failed for:{}", sessionId, e);
            throw new RuntimeException(e);
        }
    }

    public ExecResult executeCommand(String sessionId, String command) {
        Map<String, Object> result = this.runtimeClient.execCommand(sandboxRuntimeName, sessionId,
                List.of("sh", "-c", command), false, "", "", "", 900);

        if (result == null) {
            throw new RuntimeException("execute command response invalid");
        }

        ExecData response = objectMapper.convertValue(result, ExecData.class);
        if (response == null) {
            throw new RuntimeException("execute command response invalid");
        }
        LOG.debug("execute command:{} success, result:{}", command, result);

        return new ExecResult(response.getExitCode(), response.getStdout(), response.getStderr(), false);
    }
}
