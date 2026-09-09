package com.huaweicloud.agentarts.sdk.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request body for creating a gateway.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateGatewayRequest {

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("protocol_type")
    private String protocolType;

    @JsonProperty("authorizer_type")
    private String authorizerType;

    @JsonProperty("agency_name")
    private String agencyName;

    @JsonProperty("authorizer_configuration")
    private Map<String, Object> authorizerConfiguration;

    @JsonProperty("protocol_configuration")
    private Map<String, Object> protocolConfiguration;

    @JsonProperty("log_delivery_configuration")
    private Map<String, Object> logDeliveryConfiguration;

    @JsonProperty("outbound_network_configuration")
    private Map<String, Object> outboundNetworkConfiguration;

    @JsonProperty("tags")
    private List<Map<String, String>> tags;

    public CreateGatewayRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CreateGatewayRequest withName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CreateGatewayRequest withDescription(String description) {
        this.description = description;
        return this;
    }

    public String getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
    }

    public CreateGatewayRequest withProtocolType(String protocolType) {
        this.protocolType = protocolType;
        return this;
    }

    public String getAuthorizerType() {
        return authorizerType;
    }

    public void setAuthorizerType(String authorizerType) {
        this.authorizerType = authorizerType;
    }

    public CreateGatewayRequest withAuthorizerType(String authorizerType) {
        this.authorizerType = authorizerType;
        return this;
    }

    public String getAgencyName() {
        return agencyName;
    }

    public void setAgencyName(String agencyName) {
        this.agencyName = agencyName;
    }

    public CreateGatewayRequest withAgencyName(String agencyName) {
        this.agencyName = agencyName;
        return this;
    }

    public Map<String, Object> getAuthorizerConfiguration() {
        return authorizerConfiguration;
    }

    public void setAuthorizerConfiguration(Map<String, Object> authorizerConfiguration) {
        this.authorizerConfiguration = authorizerConfiguration;
    }

    public CreateGatewayRequest withAuthorizerConfiguration(Map<String, Object> authorizerConfiguration) {
        this.authorizerConfiguration = authorizerConfiguration;
        return this;
    }

    public Map<String, Object> getProtocolConfiguration() {
        return protocolConfiguration;
    }

    public void setProtocolConfiguration(Map<String, Object> protocolConfiguration) {
        this.protocolConfiguration = protocolConfiguration;
    }

    public CreateGatewayRequest withProtocolConfiguration(Map<String, Object> protocolConfiguration) {
        this.protocolConfiguration = protocolConfiguration;
        return this;
    }

    public Map<String, Object> getLogDeliveryConfiguration() {
        return logDeliveryConfiguration;
    }

    public void setLogDeliveryConfiguration(Map<String, Object> logDeliveryConfiguration) {
        this.logDeliveryConfiguration = logDeliveryConfiguration;
    }

    public CreateGatewayRequest withLogDeliveryConfiguration(Map<String, Object> logDeliveryConfiguration) {
        this.logDeliveryConfiguration = logDeliveryConfiguration;
        return this;
    }

    public Map<String, Object> getOutboundNetworkConfiguration() {
        return outboundNetworkConfiguration;
    }

    public void setOutboundNetworkConfiguration(Map<String, Object> outboundNetworkConfiguration) {
        this.outboundNetworkConfiguration = outboundNetworkConfiguration;
    }

    public CreateGatewayRequest withOutboundNetworkConfiguration(Map<String, Object> outboundNetworkConfiguration) {
        this.outboundNetworkConfiguration = outboundNetworkConfiguration;
        return this;
    }

    public List<Map<String, String>> getTags() {
        return tags;
    }

    public void setTags(List<Map<String, String>> tags) {
        this.tags = tags;
    }

    public CreateGatewayRequest withTags(List<Map<String, String>> tags) {
        this.tags = tags;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreateGatewayRequest that = (CreateGatewayRequest) o;
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(protocolType, that.protocolType)
                && Objects.equals(authorizerType, that.authorizerType)
                && Objects.equals(agencyName, that.agencyName)
                && Objects.equals(authorizerConfiguration, that.authorizerConfiguration)
                && Objects.equals(protocolConfiguration, that.protocolConfiguration)
                && Objects.equals(logDeliveryConfiguration, that.logDeliveryConfiguration)
                && Objects.equals(outboundNetworkConfiguration, that.outboundNetworkConfiguration)
                && Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, protocolType, authorizerType, agencyName,
                authorizerConfiguration, protocolConfiguration, logDeliveryConfiguration,
                outboundNetworkConfiguration, tags);
    }

    @Override
    public String toString() {
        return "CreateGatewayRequest{"
                + "name='" + name + '\''
                + ", description='" + description + '\''
                + ", protocolType='" + protocolType + '\''
                + ", authorizerType='" + authorizerType + '\''
                + ", agencyName='" + agencyName + '\''
                + '}';
    }
}
