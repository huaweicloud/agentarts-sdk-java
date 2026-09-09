package com.huaweicloud.agentarts.sdk.memory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Memory space information. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpaceInfo {
    @JsonProperty("id") private String id;
    @JsonProperty("name") private String name;
    @JsonProperty("description") private String description;
    @JsonProperty("message_ttl_hours") private int messageTtlHours = 168;
    @JsonProperty("status") private String status;
    @JsonProperty("created_at") private String createdAt;
    @JsonProperty("updated_at") private String updatedAt;
    @JsonProperty("memory_extract_enabled") private boolean memoryExtractEnabled;
    @JsonProperty("memory_extract_idle_seconds") private Integer memoryExtractIdleSeconds;
    @JsonProperty("memory_extract_max_tokens") private Integer memoryExtractMaxTokens;
    @JsonProperty("memory_extract_max_messages") private Integer memoryExtractMaxMessages;
    @JsonProperty("memory_strategies_builtin") private List<Map<String, Object>> memoryStrategiesBuiltin;
    @JsonProperty("memory_strategies_customized") private List<Map<String, Object>> memoryStrategiesCustomized;
    @JsonProperty("vpc_id") private String vpcId;
    @JsonProperty("subnet_id") private String subnetId;
    @JsonProperty("public_access") private Map<String, Object> publicAccess;
    @JsonProperty("private_access") private Map<String, Object> privateAccess;
    @JsonProperty("api_key") private Object apiKey;
    @JsonProperty("api_key_id") private String apiKeyId;
    @JsonProperty("public_domain") private String publicDomain;
    @JsonProperty("private_domain") private String privateDomain;
    @JsonProperty("private_ip") private String privateIp;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public int getMessageTtlHours() { return messageTtlHours; }
    public void setMessageTtlHours(int h) { this.messageTtlHours = h; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public boolean isMemoryExtractEnabled() { return memoryExtractEnabled; }
    public void setMemoryExtractEnabled(boolean memoryExtractEnabled) { this.memoryExtractEnabled = memoryExtractEnabled; }
    public Integer getMemoryExtractIdleSeconds() { return memoryExtractIdleSeconds; }
    public void setMemoryExtractIdleSeconds(Integer value) { this.memoryExtractIdleSeconds = value; }
    public Integer getMemoryExtractMaxTokens() { return memoryExtractMaxTokens; }
    public void setMemoryExtractMaxTokens(Integer value) { this.memoryExtractMaxTokens = value; }
    public Integer getMemoryExtractMaxMessages() { return memoryExtractMaxMessages; }
    public void setMemoryExtractMaxMessages(Integer value) { this.memoryExtractMaxMessages = value; }
    public List<Map<String, Object>> getMemoryStrategiesBuiltin() { return memoryStrategiesBuiltin; }
    public void setMemoryStrategiesBuiltin(List<Map<String, Object>> value) { this.memoryStrategiesBuiltin = value; }
    public List<Map<String, Object>> getMemoryStrategiesCustomized() { return memoryStrategiesCustomized; }
    public void setMemoryStrategiesCustomized(List<Map<String, Object>> value) { this.memoryStrategiesCustomized = value; }
    public String getVpcId() { return vpcId; }
    public void setVpcId(String vpcId) { this.vpcId = vpcId; }
    public String getSubnetId() { return subnetId; }
    public void setSubnetId(String subnetId) { this.subnetId = subnetId; }
    public Map<String, Object> getPublicAccess() { return publicAccess; }
    public void setPublicAccess(Map<String, Object> publicAccess) { this.publicAccess = publicAccess; }
    public Map<String, Object> getPrivateAccess() { return privateAccess; }
    public void setPrivateAccess(Map<String, Object> privateAccess) { this.privateAccess = privateAccess; }
    public Object getApiKey() { return apiKey; }
    public void setApiKey(Object apiKey) { this.apiKey = apiKey; }
    public String getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(String apiKeyId) { this.apiKeyId = apiKeyId; }
    public String getPublicDomain() { return publicDomain; }
    public void setPublicDomain(String publicDomain) { this.publicDomain = publicDomain; }
    public String getPrivateDomain() { return privateDomain; }
    public void setPrivateDomain(String privateDomain) { this.privateDomain = privateDomain; }
    public String getPrivateIp() { return privateIp; }
    public void setPrivateIp(String privateIp) { this.privateIp = privateIp; }
}
