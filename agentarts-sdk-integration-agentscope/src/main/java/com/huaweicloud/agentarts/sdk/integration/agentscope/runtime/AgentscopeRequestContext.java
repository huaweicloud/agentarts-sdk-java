package com.huaweicloud.agentarts.sdk.integration.agentscope.runtime;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.ToolExecutionContext;

/**
 * Immutable per-request context used by the AgentArts-to-AgentScope bridge.
 *
 * <p>The context can be registered in AgentScope's {@link ToolExecutionContext}
 * without copying secrets into logs or global state. Its session key includes
 * the user identifier to prevent state collisions between tenants.</p>
 */
public record AgentscopeRequestContext(
        String sessionId,
        String userId,
        String requestId,
        String workloadAccessToken) {

    private static final String DEFAULT_USER_ID = "anonymous";
    private static final String DEFAULT_SESSION_ID = "default";

    /** Return a tenant-aware context suitable for AgentScope {@code Session} storage. */
    public RuntimeContext runtimeContext() {
        RuntimeContext.Builder builder = RuntimeContext.builder();
        return builder.sessionId(normalize(sessionId, DEFAULT_SESSION_ID))
                .userId(normalize(userId, DEFAULT_USER_ID)).build();
    }

    /** Build the context consumed by AgentScope tools for this invocation. */
    public ToolExecutionContext toToolExecutionContext() {
        return ToolExecutionContext.builder().register(this).build();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
