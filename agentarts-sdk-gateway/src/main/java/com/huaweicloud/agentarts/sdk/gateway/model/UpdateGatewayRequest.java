package com.huaweicloud.agentarts.sdk.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request body for updating a gateway.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateGatewayRequest {

    @JsonProperty("description")
    private String description;

    @JsonProperty("protocol_configuration")
    private Map<String, Object> protocolConfiguration;

    @JsonProperty("log_delivery_configuration")
    private Map<String, Object> logDeliveryConfiguration;

    @JsonProperty("tags")
    private List<Map<String, String>> tags;

    public UpdateGatewayRequest() {
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UpdateGatewayRequest withDescription(String description) {
        this.description = description;
        return this;
    }

    public Map<String, Object> getProtocolConfiguration() {
        return protocolConfiguration;
    }

    public void setProtocolConfiguration(Map<String, Object> protocolConfiguration) {
        this.protocolConfiguration = protocolConfiguration;
    }

    public UpdateGatewayRequest withProtocolConfiguration(Map<String, Object> protocolConfiguration) {
        this.protocolConfiguration = protocolConfiguration;
        return this;
    }

    public Map<String, Object> getLogDeliveryConfiguration() {
        return logDeliveryConfiguration;
    }

    public void setLogDeliveryConfiguration(Map<String, Object> logDeliveryConfiguration) {
        this.logDeliveryConfiguration = logDeliveryConfiguration;
    }

    public UpdateGatewayRequest withLogDeliveryConfiguration(Map<String, Object> logDeliveryConfiguration) {
        this.logDeliveryConfiguration = logDeliveryConfiguration;
        return this;
    }

    public List<Map<String, String>> getTags() {
        return tags;
    }

    public void setTags(List<Map<String, String>> tags) {
        this.tags = tags;
    }

    public UpdateGatewayRequest withTags(List<Map<String, String>> tags) {
        this.tags = tags;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdateGatewayRequest that = (UpdateGatewayRequest) o;
        return Objects.equals(description, that.description)
                && Objects.equals(protocolConfiguration, that.protocolConfiguration)
                && Objects.equals(logDeliveryConfiguration, that.logDeliveryConfiguration)
                && Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, protocolConfiguration, logDeliveryConfiguration, tags);
    }

    @Override
    public String toString() {
        return "UpdateGatewayRequest{"
                + "description='" + description + '\''
                + '}';
    }
}
