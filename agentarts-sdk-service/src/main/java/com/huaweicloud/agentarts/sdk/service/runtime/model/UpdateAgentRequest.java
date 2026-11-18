package com.huaweicloud.agentarts.sdk.service.runtime.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request body for updating a Runtime agent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateAgentRequest {

    @JsonProperty("description")
    private String description;

    @JsonProperty("artifact_source")
    private Map<String, Object> artifactSource;

    @JsonProperty("storage_config")
    private Map<String, Object> storageConfig;

    @JsonProperty("invoke_config")
    private Map<String, Object> invokeConfig;

    @JsonProperty("network_config")
    private Map<String, Object> networkConfig;

    @JsonProperty("observability")
    private Map<String, Object> observability;

    @JsonProperty("execution_agency_name")
    private String executionAgencyName;

    @JsonProperty("agent_gateway_id")
    private String agentGatewayId;

    @JsonProperty("environment_variables")
    private List<Map<String, String>> environmentVariables;

    @JsonProperty("tags")
    private List<Map<String, String>> tags;

    /**
     * Credential anti-theft switch. When True, runtime credentials are bound to the
     * current VPC and rejected outside it. Only supported in private(VPC) network
     * mode. Null means the field is omitted and the backend default applies.
     */
    @JsonProperty("enable_restricted_credentials")
    private Boolean enableRestrictedCredentials;

    public UpdateAgentRequest withDescription(String description) {
        this.description = description;
        return this;
    }

    public UpdateAgentRequest withArtifactSource(Map<String, Object> artifactSource) {
        this.artifactSource = artifactSource;
        return this;
    }

    public UpdateAgentRequest withStorageConfig(Map<String, Object> storageConfig) {
        this.storageConfig = storageConfig;
        return this;
    }

    public UpdateAgentRequest withInvokeConfig(Map<String, Object> invokeConfig) {
        this.invokeConfig = invokeConfig;
        return this;
    }

    public UpdateAgentRequest withNetworkConfig(Map<String, Object> networkConfig) {
        this.networkConfig = networkConfig;
        return this;
    }

    public UpdateAgentRequest withObservability(Map<String, Object> observability) {
        this.observability = observability;
        return this;
    }

    public UpdateAgentRequest withExecutionAgencyName(String executionAgencyName) {
        this.executionAgencyName = executionAgencyName;
        return this;
    }

    public UpdateAgentRequest withAgentGatewayId(String agentGatewayId) {
        this.agentGatewayId = agentGatewayId;
        return this;
    }

    public UpdateAgentRequest withEnvironmentVariables(List<Map<String, String>> environmentVariables) {
        this.environmentVariables = environmentVariables;
        return this;
    }

    public UpdateAgentRequest withTags(List<Map<String, String>> tags) {
        this.tags = tags;
        return this;
    }

    public UpdateAgentRequest withEnableRestrictedCredentials(Boolean enableRestrictedCredentials) {
        this.enableRestrictedCredentials = enableRestrictedCredentials;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getArtifactSource() {
        return artifactSource;
    }

    public void setArtifactSource(Map<String, Object> artifactSource) {
        this.artifactSource = artifactSource;
    }

    public Map<String, Object> getStorageConfig() {
        return storageConfig;
    }

    public void setStorageConfig(Map<String, Object> storageConfig) {
        this.storageConfig = storageConfig;
    }

    public Map<String, Object> getInvokeConfig() {
        return invokeConfig;
    }

    public void setInvokeConfig(Map<String, Object> invokeConfig) {
        this.invokeConfig = invokeConfig;
    }

    public Map<String, Object> getNetworkConfig() {
        return networkConfig;
    }

    public void setNetworkConfig(Map<String, Object> networkConfig) {
        this.networkConfig = networkConfig;
    }

    public Map<String, Object> getObservability() {
        return observability;
    }

    public void setObservability(Map<String, Object> observability) {
        this.observability = observability;
    }

    public String getExecutionAgencyName() {
        return executionAgencyName;
    }

    public void setExecutionAgencyName(String executionAgencyName) {
        this.executionAgencyName = executionAgencyName;
    }

    public String getAgentGatewayId() {
        return agentGatewayId;
    }

    public void setAgentGatewayId(String agentGatewayId) {
        this.agentGatewayId = agentGatewayId;
    }

    public List<Map<String, String>> getEnvironmentVariables() {
        return environmentVariables;
    }

    public void setEnvironmentVariables(List<Map<String, String>> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public List<Map<String, String>> getTags() {
        return tags;
    }

    public void setTags(List<Map<String, String>> tags) {
        this.tags = tags;
    }

    public Boolean getEnableRestrictedCredentials() {
        return enableRestrictedCredentials;
    }

    public void setEnableRestrictedCredentials(Boolean enableRestrictedCredentials) {
        this.enableRestrictedCredentials = enableRestrictedCredentials;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdateAgentRequest that = (UpdateAgentRequest) o;
        return Objects.equals(description, that.description)
                && Objects.equals(artifactSource, that.artifactSource)
                && Objects.equals(storageConfig, that.storageConfig)
                && Objects.equals(invokeConfig, that.invokeConfig)
                && Objects.equals(networkConfig, that.networkConfig)
                && Objects.equals(observability, that.observability)
                && Objects.equals(executionAgencyName, that.executionAgencyName)
                && Objects.equals(agentGatewayId, that.agentGatewayId)
                && Objects.equals(environmentVariables, that.environmentVariables)
                && Objects.equals(tags, that.tags)
                && Objects.equals(enableRestrictedCredentials, that.enableRestrictedCredentials);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, artifactSource, storageConfig, invokeConfig,
                networkConfig, observability, executionAgencyName, agentGatewayId,
                environmentVariables, tags, enableRestrictedCredentials);
    }

    @Override
    public String toString() {
        return "UpdateAgentRequest{"
                + "description='" + description + "'"
                + ", executionAgencyName='" + executionAgencyName + "'"
                + ", agentGatewayId='" + agentGatewayId + "'"
                + ", storageConfig=" + redacted(storageConfig)
                + ", invokeConfig=" + redacted(invokeConfig)
                + ", environmentVariables=" + redacted(environmentVariables)
                + ", tags=" + tags
                + ", enableRestrictedCredentials=" + enableRestrictedCredentials
                + "}";
    }

    private static String redacted(Object value) {
        return value == null ? "null" : "[REDACTED]";
    }
}
