package com.huaweicloud.agentarts.sdk.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Runtime configuration for deploying and running the agent.
 *
 * <p>Runtime configuration for deploying and running the agent.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuntimeConfig {

    @JsonProperty("agent_id")
    private String agentId;

    @JsonProperty("agent_gateway_id")
    private String agentGatewayId;

    @JsonProperty("arch")
    private String arch = "arm64";

    @JsonProperty("execution_agency_name")
    private String executionAgencyName;

    @JsonProperty("identity_configuration")
    private Map<String, Object> identityConfiguration;

    @JsonProperty("network_config")
    private Map<String, Object> networkConfig;

    @JsonProperty("invoke_config")
    private InvokeConfig invokeConfig;

    @JsonProperty("observability")
    private Map<String, Object> observability;

    @JsonProperty("artifact_source")
    private Map<String, Object> artifactSource;

    @JsonProperty("environment_variables")
    private Map<String, String> environmentVariables;

    @JsonProperty("tags")
    private Map<String, String> tags;

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getAgentGatewayId() { return agentGatewayId; }
    public void setAgentGatewayId(String agentGatewayId) { this.agentGatewayId = agentGatewayId; }

    public String getArch() { return arch; }
    public void setArch(String arch) { this.arch = arch; }

    public String getExecutionAgencyName() { return executionAgencyName; }
    public void setExecutionAgencyName(String executionAgencyName) { this.executionAgencyName = executionAgencyName; }

    public Map<String, Object> getIdentityConfiguration() { return identityConfiguration; }
    public void setIdentityConfiguration(Map<String, Object> identityConfiguration) { this.identityConfiguration = identityConfiguration; }

    public Map<String, Object> getNetworkConfig() { return networkConfig; }
    public void setNetworkConfig(Map<String, Object> networkConfig) { this.networkConfig = networkConfig; }

    public InvokeConfig getInvokeConfig() { return invokeConfig; }
    public void setInvokeConfig(InvokeConfig invokeConfig) { this.invokeConfig = invokeConfig; }

    public Map<String, Object> getObservability() { return observability; }
    public void setObservability(Map<String, Object> observability) { this.observability = observability; }

    public Map<String, Object> getArtifactSource() { return artifactSource; }
    public void setArtifactSource(Map<String, Object> artifactSource) { this.artifactSource = artifactSource; }

    public Map<String, String> getEnvironmentVariables() { return environmentVariables; }
    public void setEnvironmentVariables(Map<String, String> environmentVariables) { this.environmentVariables = environmentVariables; }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
}
