package com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox.client;

/**
 * Response wrapper for session start against AgentArts Runtime.
 *
 * <p>Contains the response code, message, and {@link SessionData} payload.</p>
 */
public class SessionStartResponse {
    private int code;

    private String message;

    private SessionData data;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public SessionData getData() {
        return data;
    }

    public void setData(SessionData data) {
        this.data = data;
    }
}
