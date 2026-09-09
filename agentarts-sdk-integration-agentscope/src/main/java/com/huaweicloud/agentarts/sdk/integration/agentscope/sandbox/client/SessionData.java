package com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data model for session creation response from AgentArts Runtime.
 *
 * <p>Contains the session ID assigned by the Runtime.</p>
 */
public class SessionData {
    @JsonProperty("session_id")
    private String sessionId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
