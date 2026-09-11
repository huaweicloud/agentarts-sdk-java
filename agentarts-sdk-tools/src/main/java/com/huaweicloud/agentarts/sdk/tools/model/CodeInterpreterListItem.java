package com.huaweicloud.agentarts.sdk.tools.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * A single item in the list-code-interpreters response.
 *
 * <p>The list API returns a reduced field set compared to
 * {@link CodeInterpreterInfo} (returned by create/get/update): it does not
 * include create-only fields such as {@code auth_type}, {@code api_key_name},
 * or {@code network_config}. This class only exposes the fields the list API
 * actually returns.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeInterpreterListItem {
    @JsonProperty("id") private String id;
    @JsonProperty("name") private String name;
    @JsonProperty("description") private String description;
    @JsonProperty("created_at") private String createdAt;
    @JsonProperty("updated_at") private String updatedAt;
    @JsonProperty("execution_agency_name") private String executionAgencyName;
    @JsonProperty("agent_gateway_id") private String agentGatewayId;
    @JsonProperty("access_endpoint") private String accessEndpoint;
    @JsonProperty("observability") private Map<String, Object> observability;
    @JsonProperty("tags") private List<Map<String, String>> tags;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public String getExecutionAgencyName() { return executionAgencyName; }
    public String getAgentGatewayId() { return agentGatewayId; }
    public String getAccessEndpoint() { return accessEndpoint; }
    public Map<String, Object> getObservability() { return observability; }
    public List<Map<String, String>> getTags() { return tags; }
}
