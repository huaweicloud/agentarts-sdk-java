package com.huaweicloud.agentarts.sdk.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

/**
 * Request body for creating a gateway target.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateGatewayTargetRequest {

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("target_configuration")
    private Map<String, Object> targetConfiguration;

    @JsonProperty("credential_provider_configuration")
    private Map<String, Object> credentialProviderConfiguration;

    public CreateGatewayTargetRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CreateGatewayTargetRequest withName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CreateGatewayTargetRequest withDescription(String description) {
        this.description = description;
        return this;
    }

    public Map<String, Object> getTargetConfiguration() {
        return targetConfiguration;
    }

    public void setTargetConfiguration(Map<String, Object> targetConfiguration) {
        this.targetConfiguration = targetConfiguration;
    }

    public CreateGatewayTargetRequest withTargetConfiguration(Map<String, Object> targetConfiguration) {
        this.targetConfiguration = targetConfiguration;
        return this;
    }

    public Map<String, Object> getCredentialProviderConfiguration() {
        return credentialProviderConfiguration;
    }

    public void setCredentialProviderConfiguration(Map<String, Object> credentialProviderConfiguration) {
        this.credentialProviderConfiguration = credentialProviderConfiguration;
    }

    public CreateGatewayTargetRequest withCredentialProviderConfiguration(Map<String, Object> credentialProviderConfiguration) {
        this.credentialProviderConfiguration = credentialProviderConfiguration;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreateGatewayTargetRequest that = (CreateGatewayTargetRequest) o;
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(targetConfiguration, that.targetConfiguration)
                && Objects.equals(credentialProviderConfiguration, that.credentialProviderConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, targetConfiguration, credentialProviderConfiguration);
    }

    @Override
    public String toString() {
        return "CreateGatewayTargetRequest{"
                + "name='" + name + '\''
                + ", description='" + description + '\''
                + ", targetConfiguration=" + redacted(targetConfiguration)
                + ", credentialProviderConfiguration=" + redacted(credentialProviderConfiguration)
                + '}';
    }

    private static String redacted(Object value) {
        return value == null ? "null" : "[REDACTED]";
    }
}
