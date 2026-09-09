package com.huaweicloud.agentarts.sdk.integration.agentscope.sandbox.client;

/**
 * Response wrapper for command execution against AgentArts Runtime.
 *
 * <p>Contains the response code, message, and {@link ExecData} payload.</p>
 */
public class ExecResponse {
    private int code;

    private String message;

    private ExecData data;

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

    public ExecData getData() {
        return data;
    }

    public void setData(ExecData data) {
        this.data = data;
    }
}
