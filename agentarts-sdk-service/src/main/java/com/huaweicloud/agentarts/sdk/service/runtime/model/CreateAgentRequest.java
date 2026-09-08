package com.huaweicloud.agentarts.sdk.service.runtime.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request body for creating a Runtime agent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateAgentRequest {

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("artifact_source")
    private Map<String, Object> artifactSource;

    @JsonProperty("storage_config")
    private Map<String, Object> storageConfig;

    @JsonProperty("identity_configuration")
    private Map<String, Object> identityConfiguration;

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

    @JsonProperty("arch")
    private String arch;

    public CreateAgentRequest withName(String name) {
        this.name = name;
        return this;
    }

    public CreateAgentRequest withDescription(String description) {
        this.description = description;
        return this;
    }

    public CreateAgentRequest withArtifactSource(Map<String, Object> artifactSource) {
        this.artifactSource = artifactSource;
        return this;
    }

    public CreateAgentRequest withStorageConfig(Map<String, Object> storageConfig) {
        this.storageConfig = storageConfig;
        return this;
    }

    public CreateAgentRequest withIdentityConfiguration(Map<String, Object> identityConfiguration) {
        this.identityConfiguration = identityConfiguration;
        return this;
    }

    public CreateAgentRequest withInvokeConfig(Map<String, Object> invokeConfig) {
        this.invokeConfig = invokeConfig;
        return this;
    }

    public CreateAgentRequest withNetworkConfig(Map<String, Object> networkConfig) {
        this.networkConfig = networkConfig;
        return this;
    }

    public CreateAgentRequest withObservability(Map<String, Object> observability) {
        this.observability = observability;
        return this;
    }

    public CreateAgentRequest withExecutionAgencyName(String executionAgencyName) {
        this.executionAgencyName = executionAgencyName;
        return this;
    }

    public CreateAgentRequest withAgentGatewayId(String agentGatewayId) {
        this.agentGatewayId = agentGatewayId;
        return this;
    }

    public CreateAgentRequest withEnvironmentVariables(List<Map<String, String>> environmentVariables) {
        this.environmentVariables = environmentVariables;
        return this;
    }

    public CreateAgentRequest withTags(List<Map<String, String>> tags) {
        this.tags = tags;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Map<String, Object> getIdentityConfiguration() {
        return identityConfiguration;
    }

    public void setIdentityConfiguration(Map<String, Object> identityConfiguration) {
        this.identityConfiguration = identityConfiguration;
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

    public String getArch() {
        return arch;
    }

    public CreateAgentRequest withArch(String arch) {
        this.arch = arch;
        return this;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreateAgentRequest that = (CreateAgentRequest) o;
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(artifactSource, that.artifactSource)
                && Objects.equals(storageConfig, that.storageConfig)
                && Objects.equals(identityConfiguration, that.identityConfiguration)
                && Objects.equals(invokeConfig, that.invokeConfig)
                && Objects.equals(networkConfig, that.networkConfig)
                && Objects.equals(observability, that.observability)
                && Objects.equals(executionAgencyName, that.executionAgencyName)
                && Objects.equals(agentGatewayId, that.agentGatewayId)
                && Objects.equals(environmentVariables, that.environmentVariables)
                && Objects.equals(tags, that.tags)
                && Objects.equals(arch, that.arch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, artifactSource, storageConfig,
                identityConfiguration, invokeConfig, networkConfig, observability,
                executionAgencyName, agentGatewayId, environmentVariables, tags, arch);
    }

    @Override
    public String toString() {
        return "CreateAgentRequest{"
                + "name='" + name + "'"
                + ", description='" + description + "'"
                + ", executionAgencyName='" + executionAgencyName + "'"
                + ", agentGatewayId='" + agentGatewayId + "'"
                + ", storageConfig=" + redacted(storageConfig)
                + ", identityConfiguration=" + redacted(identityConfiguration)
                + ", invokeConfig=" + redacted(invokeConfig)
                + ", environmentVariables=" + redacted(environmentVariables)
                + ", tags=" + tags
                + ", arch='" + arch + "'"
                + "}";
    }

    private static String redacted(Object value) {
        return value == null ? "null" : "[REDACTED]";
    }
}
