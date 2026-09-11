package com.huaweicloud.agentarts.sdk.tools.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response for listing code interpreters.
 *
 * <p>Each item is a {@link CodeInterpreterListItem} — the list API returns a
 * reduced field set and does not include create-only fields such as
 * {@code auth_type} or {@code network_config}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeInterpreterListResponse {
    @JsonProperty("items") private List<CodeInterpreterListItem> items;
    @JsonProperty("total_count") private int totalCount;

    public List<CodeInterpreterListItem> getItems() { return items; }
    public int getTotalCount() { return totalCount; }
}
