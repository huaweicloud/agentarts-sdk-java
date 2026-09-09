package com.huaweicloud.agentarts.sdk.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.agentarts.sdk.core.APIException;
import com.huaweicloud.agentarts.sdk.core.Constants;
import com.huaweicloud.agentarts.sdk.core.SignMode;
import com.huaweicloud.agentarts.sdk.core.util.UrlUtils;
import com.huaweicloud.agentarts.sdk.memory.model.*;
import com.huaweicloud.agentarts.sdk.service.http.BaseHttpClient;
import com.huaweicloud.agentarts.sdk.service.http.RequestConfig;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;

import java.util.*;
import reactor.core.publisher.Mono;

/**
 * Memory client with dual-plane architecture (AK/SK control + API Key data).
 * Control plane operations use AK/SK signing.
 * Data plane operations use API Key (Bearer token) authentication.
 *
 * <h2>Control Plane (AK/SK signed):</h2>
 * <ul>
 *   <li>{@link #createSpace} / {@link #getSpace} / {@link #listSpaces}</li>
 *   <li>{@link #updateSpace} / {@link #deleteSpace} / {@link #createApiKey}</li>
 * </ul>
 *
 * <h2>Data Plane (API Key auth):</h2>
 * <ul>
 *   <li>{@link #createMemorySession} / {@link #addMessages} / {@link #getLastKMessages}</li>
 *   <li>{@link #getMessage} / {@link #listMessages} / {@link #searchMemories}</li>
 *   <li>{@link #getMemory} / {@link #listMemories} / {@link #deleteMemory}</li>
 * </ul>
 */
public class MemoryClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = com.huaweicloud.agentarts.sdk.core.util.JsonUtils.MAPPER;

    private final String regionName;
    private final String apiKey;
    private final boolean verifySsl;

    private BaseHttpClient controlPlaneClient;
    private BaseHttpClient dataPlaneClient;

    /**
     * Create a MemoryClient with full configuration.
     *
     * @param regionName Huawei Cloud region (nullable, auto-detected if null)
     * @param apiKey     data plane API key for Bearer auth (nullable, falls back to env)
     * @param verifySsl  whether to verify SSL certificates
     */
    public MemoryClient(String regionName, String apiKey, boolean verifySsl) {
        this(regionName, apiKey, verifySsl, null, null);
    }

    MemoryClient(String regionName, String apiKey, boolean verifySsl,
                 BaseHttpClient controlPlaneClient, BaseHttpClient dataPlaneClient) {
        this.regionName = regionName != null ? regionName : Constants.getRegion();
        this.apiKey = apiKey;
        this.verifySsl = verifySsl;
        this.controlPlaneClient = controlPlaneClient;
        this.dataPlaneClient = dataPlaneClient;
    }

    /** Create a MemoryClient with SSL verification enabled. */
    public MemoryClient(String regionName, String apiKey) {
        this(regionName, apiKey, true);
    }

    /** Create a MemoryClient with default region and SSL settings. */
    public MemoryClient() {
        this(null, null, true);
    }

    // ========================
    // Lazy client initialization
    // ========================

    private synchronized BaseHttpClient getControlPlaneClient() {
        if (controlPlaneClient == null) {
            String endpoint = Constants.getMemoryEndpoint("control", regionName) + "/v1/core";
            RequestConfig config = RequestConfig.builder()
                    .baseUrl(endpoint)
                    .verifySsl(verifySsl)
                    .build();
            controlPlaneClient = new BaseHttpClient(config, true, SignMode.SDK_HMAC_SHA256, regionName);
        }
        return controlPlaneClient;
    }

    private synchronized BaseHttpClient getDataPlaneClient() {
        if (dataPlaneClient == null) {
            String endpoint = Constants.getMemoryEndpoint("data", regionName) + "/v1/core";
            RequestConfig config = RequestConfig.builder()
                    .baseUrl(endpoint)
                    .verifySsl(verifySsl)
                    .build();
            dataPlaneClient = new BaseHttpClient(config, false, SignMode.SDK_HMAC_SHA256, regionName);
            // Set API key auth
            String key = apiKey;
            if (key == null || key.isEmpty()) {
                key = System.getenv("HUAWEICLOUD_SDK_MEMORY_API_KEY");
            }
            if (com.huaweicloud.agentarts.sdk.core.util.JsonUtils.isNotBlank(key)) {
                dataPlaneClient.setAuthToken("Bearer", key);
            } else {
                dataPlaneClient.close();
                dataPlaneClient = null;
                throw new IllegalStateException(
                        "Memory data-plane API key is required; pass apiKey or set HUAWEICLOUD_SDK_MEMORY_API_KEY");
            }
        }
        return dataPlaneClient;
    }

    // ========================
    // Control Plane: Space management
    // ========================

    /**
     * Create a memory space (Control Plane, AK/SK signed).
     *
     * @param name             space name
     * @param messageTtlHours  message TTL in hours (default 168 = 7 days)
     * @param description      optional description
     * @return created space info
     */
    public SpaceInfo createSpace(String name, int messageTtlHours, String description) {
        return createSpace(
                name,
                messageTtlHours,
                description,
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                null);
    }

    /** Create a memory space with every configuration option exposed by the Python SDK. */
    public SpaceInfo createSpace(
            String name,
            int messageTtlHours,
            String description,
            List<Map<String, String>> tags,
            Integer memoryExtractIdleSeconds,
            Integer memoryExtractMaxTokens,
            Integer memoryExtractMaxMessages,
            boolean publicAccessEnable,
            String privateVpcId,
            String privateSubnetId,
            List<String> memoryStrategiesBuiltin,
            List<Map<String, Object>> memoryStrategiesCustomized) {
        // Create API key first — space creation requires api_key_id
        ApiKeyInfo keyResult = createApiKey();
        String apiKeyId = keyResult != null ? keyResult.getId() : null;
        String apiKeyValue = keyResult != null ? keyResult.getApiKey() : null;

        Map<String, Object> networkAccess = new LinkedHashMap<>();
        networkAccess.put("public_access_enable", publicAccessEnable);
        if (privateVpcId != null && privateSubnetId != null) {
            networkAccess.put(
                    "private_access_config",
                    Map.of(
                            "enable", true,
                            "vpc_id", privateVpcId,
                            "subnet_id", privateSubnetId));
        }

        CreateSpaceRequest req = new CreateSpaceRequest()
                .withName(name)
                .withMessageTtlHours(messageTtlHours)
                .withDescription(description)
                .withApiKeyId(apiKeyId)
                .withTags(tags)
                .withNetworkAccess(networkAccess)
                .withMemoryExtractIdleSeconds(memoryExtractIdleSeconds)
                .withMemoryExtractMaxTokens(memoryExtractMaxTokens)
                .withMemoryExtractMaxMessages(memoryExtractMaxMessages)
                .withMemoryStrategiesBuiltin(memoryStrategiesBuiltin)
                .withMemoryStrategiesCustomized(memoryStrategiesCustomized);

        RequestResult result = getControlPlaneClient()
                .post("/spaces", null, req).block();
        SpaceInfo space = parseResult(result, SpaceInfo.class);
        // Inject the api_key into the response (server doesn't return it)
        if (space != null && apiKeyValue != null) {
            space.setApiKey(apiKeyValue);
        }
        if (space != null && apiKeyId != null) {
            space.setApiKeyId(apiKeyId);
        }
        return space;
    }

    /** Create a memory space with default TTL (168 hours). */
    public SpaceInfo createSpace(String name) {
        return createSpace(name, 168, null);
    }

    /** Get a memory space by ID (Control Plane). */
    public SpaceInfo getSpace(String spaceId) {
        RequestResult result = getControlPlaneClient()
                .get("/spaces/" + UrlUtils.encodePathSegment(spaceId, "spaceId")).block();
        return parseResult(result, SpaceInfo.class);
    }

    /** List memory spaces with pagination (Control Plane). */
    public SpaceListResponse listSpaces(int limit, int offset) {
        Map<String, List<String>> query = paginationQuery(limit, offset);
        RequestResult result = getControlPlaneClient()
                .request("GET", "/spaces", null, null, query).block();
        return parseResult(result, SpaceListResponse.class);
    }

    /** List memory spaces with default pagination (limit=20, offset=0). */
    public SpaceListResponse listSpaces() {
        return listSpaces(20, 0);
    }

    /** Update a memory space (Control Plane). Null fields are not updated. */
    public SpaceInfo updateSpace(String spaceId, String name, String description, Integer messageTtlHours) {
        return updateSpace(
                spaceId,
                name,
                description,
                messageTtlHours,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** Update every mutable space field supported by the Python SDK. */
    public SpaceInfo updateSpace(
            String spaceId,
            String name,
            String description,
            Integer messageTtlHours,
            Boolean memoryExtractEnabled,
            Integer memoryExtractIdleSeconds,
            Integer memoryExtractMaxTokens,
            Integer memoryExtractMaxMessages,
            List<Map<String, String>> tags,
            List<String> memoryStrategiesBuiltin) {
        UpdateSpaceRequest req = new UpdateSpaceRequest()
                .withName(name)
                .withDescription(description)
                .withMessageTtlHours(messageTtlHours)
                .withMemoryExtractEnabled(memoryExtractEnabled)
                .withMemoryExtractIdleSeconds(memoryExtractIdleSeconds)
                .withMemoryExtractMaxTokens(memoryExtractMaxTokens)
                .withMemoryExtractMaxMessages(memoryExtractMaxMessages)
                .withTags(tags)
                .withMemoryStrategiesBuiltin(memoryStrategiesBuiltin);

        RequestResult result = getControlPlaneClient()
                .put("/spaces/" + UrlUtils.encodePathSegment(spaceId, "spaceId"), null, req).block();
        return parseResult(result, SpaceInfo.class);
    }

    /** Delete a memory space (Control Plane). */
    public void deleteSpace(String spaceId) {
        ensureSuccess(getControlPlaneClient().delete(
                "/spaces/" + UrlUtils.encodePathSegment(spaceId, "spaceId")).block());
    }

    /** Create an API key for data plane access (Control Plane). */
    public ApiKeyInfo createApiKey() {
        RequestResult result = getControlPlaneClient()
                .post("/space-keys", null, Map.of()).block();
        return parseResult(result, ApiKeyInfo.class);
    }

    // ========================
    // Data Plane: Session management
    // ========================

    /**
     * Create a memory session (Data Plane, API Key auth).
     *
     * @param spaceId     space ID
     * @param id          optional session ID (auto-generated if null)
     * @param actorId     optional actor identifier
     * @param assistantId optional assistant identifier
     * @return created session info
     */
    public SessionInfo createMemorySession(String spaceId, String id, String actorId, String assistantId) {
        return createMemorySession(spaceId, id, actorId, assistantId, null);
    }

    /** Create a memory session with optional metadata (Data Plane, API Key auth). */
    public SessionInfo createMemorySession(
            String spaceId,
            String id,
            String actorId,
            String assistantId,
            Map<String, Object> meta) {
        return createMemorySessionAsync(spaceId, id, actorId, assistantId, meta).block();
    }

    /** Create a memory session with auto-generated ID (Data Plane). */
    public SessionInfo createMemorySession(String spaceId) {
        return createMemorySession(spaceId, null, null, null);
    }

    // ========================
    // Data Plane: Messages
    // ========================

    /**
     * Add messages to a session (Data Plane).
     *
     * @param spaceId          space ID
     * @param sessionId        session ID
     * @param messages         list of TextMessage, ToolCallMessage, or ToolResultMessage
     * @param timestamp        optional message timestamp
     * @param idempotencyKey   optional idempotency key
     * @param isForceExtract   whether to force memory extraction
     * @return batch response with added messages
     */
    public MessageBatchResponse addMessages(String spaceId, String sessionId,
                                              List<?> messages,
                                              Long timestamp, String idempotencyKey,
                                              boolean isForceExtract) {
        return addMessagesAsync(spaceId, sessionId, messages, timestamp,
                idempotencyKey, isForceExtract).block();
    }

    private static AddMessagesRequest buildAddMessagesRequest(
            List<?> messages, Long timestamp, String idempotencyKey, boolean isForceExtract) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be null or empty");
        }
        if (messages.size() > 100) {
            throw new IllegalArgumentException("messages can contain at most 100 items");
        }
        List<Map<String, Object>> msgDicts = new ArrayList<>();
        for (Object msg : messages) {
            if (msg instanceof TextMessage tm) {
                msgDicts.add(tm.toDict());
            } else if (msg instanceof ToolCallMessage tcm) {
                msgDicts.add(tcm.toDict());
            } else if (msg instanceof ToolResultMessage trm) {
                msgDicts.add(trm.toDict());
            } else {
                String type = msg == null ? "null" : msg.getClass().getName();
                throw new IllegalArgumentException(
                        "Unsupported message type: " + type + ". "
                        + "addMessages only accepts typed message objects (the SDK converts them "
                        + "to the backend's 'parts' format). Pass one of: "
                        + "TextMessage(role, content) for plain text, "
                        + "ToolCallMessage for tool invocations, "
                        + "ToolResultMessage for tool outputs. "
                        + "Raw Maps / LinkedHashMap / POJOs are not accepted. "
                        + "Example: List.of(new TextMessage(\"user\", \"hello\"))");
            }
        }

        return new AddMessagesRequest()
                .withMessages(msgDicts)
                .withTimestamp(timestamp)
                .withIdempotencyKey(idempotencyKey)
                .withIsForceExtract(isForceExtract);
    }

    /** Add messages with default options (Data Plane). */
    public MessageBatchResponse addMessages(String spaceId, String sessionId, List<?> messages) {
        return addMessages(spaceId, sessionId, messages, null, null, false);
    }

    /** Get the last K messages from a session (Data Plane). */
    public List<MessageInfo> getLastKMessages(String sessionId, int k, String spaceId) {
        return getLastKMessagesAsync(sessionId, k, spaceId).block();
    }

    /** Get a single message by ID (Data Plane). */
    public MessageInfo getMessage(String messageId, String spaceId, String sessionId) {
        return getMessageAsync(messageId, spaceId, sessionId).block();
    }

    /** List messages with pagination (Data Plane). */
    public MessageListResponse listMessages(String spaceId, String sessionId, int limit, int offset) {
        return listMessagesAsync(spaceId, sessionId, limit, offset).block();
    }

    /** List messages with default pagination (Data Plane). */
    public MessageListResponse listMessages(String spaceId) {
        return listMessages(spaceId, null, 10, 0);
    }

    // ========================
    // Data Plane: Memories
    // ========================

    /** Search memories with optional filters (Data Plane). */
    public MemorySearchResponse searchMemories(String spaceId, MemorySearchFilter filters) {
        return searchMemoriesAsync(spaceId, filters).block();
    }

    /** Search memories without filters (Data Plane). */
    public MemorySearchResponse searchMemories(String spaceId) {
        return searchMemories(spaceId, null);
    }

    /** List memories with pagination and optional filters (Data Plane). */
    public MemoryListResponse listMemories(String spaceId, int limit, int offset, MemoryListFilter filters) {
        return listMemoriesAsync(spaceId, limit, offset, filters).block();
    }

    /** List memories with default pagination (Data Plane). */
    public MemoryListResponse listMemories(String spaceId) {
        return listMemories(spaceId, 10, 0, null);
    }

    /** Get a memory by ID (Data Plane). */
    public MemoryInfo getMemory(String spaceId, String memoryId) {
        return getMemoryAsync(spaceId, memoryId).block();
    }

    /** Delete a memory by ID (Data Plane). */
    public void deleteMemory(String spaceId, String memoryId) {
        deleteMemoryAsync(spaceId, memoryId).block();
    }

    // ========================
    // Helpers
    // ========================

    private static Map<String, List<String>> paginationQuery(int limit, int offset) {
        Map<String, List<String>> query = new LinkedHashMap<>();
        query.put("limit", List.of(String.valueOf(limit)));
        query.put("offset", List.of(String.valueOf(offset)));
        return query;
    }

    private <T> T parseResult(RequestResult result, Class<T> type) {
        if (result == null || !result.isSuccess()) {
            int status = result != null ? result.getStatusCode() : 0;
            String err = result != null ? result.getError() : "null response";
            throw new APIException(status, "memory_api", err);
        }
        try {
            JsonNode data = result.getDataAsJson();
            if (data != null) {
                return MAPPER.treeToValue(data, type);
            }
            return null;
        } catch (Exception e) {
            throw new APIException(result.getStatusCode(), "memory_api",
                    "Failed to parse response: " + e.getMessage(), e);
        }
    }

    private void ensureSuccess(RequestResult result) {
        if (result == null || !result.isSuccess()) {
            int status = result != null ? result.getStatusCode() : 0;
            String err = result != null ? result.getError() : "null response";
            throw new APIException(status, "memory_api", err);
        }
    }

    @Override
    public synchronized void close() {
        if (controlPlaneClient != null) {
            controlPlaneClient.close();
            controlPlaneClient = null;
        }
        if (dataPlaneClient != null) {
            dataPlaneClient.close();
            dataPlaneClient = null;
        }
    }

    public String getRegionName() { return regionName; }

    // ========================
    // Async (Reactor) variants
    // ========================
    //
    // Each method is cold and composes BaseHttpClient's non-blocking transport.
    // Synchronous methods above are compatibility wrappers around this core.

    /** Async variant of {@link #createMemorySession(String, String, String, String, Map)}. */
    public Mono<SessionInfo> createMemorySessionAsync(
            String spaceId,
            String id,
            String actorId,
            String assistantId,
            Map<String, Object> meta) {
        return Mono.defer(() -> {
            CreateMemorySessionRequest request = new CreateMemorySessionRequest()
                    .withId(id)
                    .withActorId(actorId)
                    .withAssistantId(assistantId)
                    .withMeta(meta);
            String url = "/spaces/" + UrlUtils.encodePathSegment(spaceId, "spaceId")
                    + "/sessions";
            return getDataPlaneClient().post(url, null, request)
                    .map(result -> parseResult(result, SessionInfo.class));
        });
    }

    /** Async variant without session metadata. */
    public Mono<SessionInfo> createMemorySessionAsync(String spaceId, String id,
                                                       String actorId, String assistantId) {
        return createMemorySessionAsync(spaceId, id, actorId, assistantId, null);
    }

    /** Async variant of {@link #addMessages(String, String, List, Long, String, boolean)}. */
    public Mono<MessageBatchResponse> addMessagesAsync(String spaceId, String sessionId,
                                                         List<?> messages, Long timestamp,
                                                         String idempotencyKey, boolean isForceExtract) {
        return Mono.defer(() -> {
            AddMessagesRequest request = buildAddMessagesRequest(
                    messages, timestamp, idempotencyKey, isForceExtract);
            String url = "/spaces/" + UrlUtils.encodePathSegment(spaceId, "spaceId")
                    + "/sessions/" + UrlUtils.encodePathSegment(sessionId, "sessionId")
                    + "/messages";
            return getDataPlaneClient().post(url, null, request)
                    .map(result -> parseResult(result, MessageBatchResponse.class));
        });
    }

    /** Async variant of {@link #addMessages(String, String, List)}. */
    public Mono<MessageBatchResponse> addMessagesAsync(String spaceId, String sessionId,
                                                         List<?> messages) {
        return addMessagesAsync(spaceId, sessionId, messages, null, null, false);
    }

    /** Async variant of {@link #getLastKMessages(String, int, String)}. */
    public Mono<List<MessageInfo>> getLastKMessagesAsync(String sessionId, int k, String spaceId) {
        return listMessagesAsync(spaceId, sessionId, 1, 0)
                .flatMap(first -> {
                    int offset = Math.max(0, first.getTotal() - k);
                    return listMessagesAsync(spaceId, sessionId, k, offset);
                })
                .map(response -> response.getItems() != null ? response.getItems() : List.of());
    }

    /** Async variant of {@link #getMessage(String, String, String)}. */
    public Mono<MessageInfo> getMessageAsync(String messageId, String spaceId, String sessionId) {
        return Mono.defer(() -> {
            String url = "/spaces/" + UrlUtils.encodePathSegment(spaceId, "spaceId")
                    + "/sessions/" + UrlUtils.encodePathSegment(sessionId, "sessionId")
                    + "/messages/" + UrlUtils.encodePathSegment(messageId, "messageId");
            return getDataPlaneClient().get(url)
                    .map(result -> parseResult(result, MessageInfo.class));
        });
    }

    /** Async variant of {@link #listMessages(String, String, int, int)}. */
    public Mono<MessageListResponse> listMessagesAsync(String spaceId, String sessionId,
                                                         int limit, int offset) {
        return Mono.defer(() -> {
            String url = sessionId != null
                    ? "/spaces/" + UrlUtils.encodePathSegment(spaceId, "spaceId")
                            + "/sessions/" + UrlUtils.encodePathSegment(sessionId, "sessionId")
                            + "/messages"
                    : "/spaces/" + UrlUtils.encodePathSegment(spaceId, "spaceId") + "/messages";
            return getDataPlaneClient()
                    .request("GET", url, null, null, paginationQuery(limit, offset))
                    .map(result -> parseResult(result, MessageListResponse.class));
        });
    }

    /** Async variant of {@link #searchMemories(String, MemorySearchFilter)}. */
    public Mono<MemorySearchResponse> searchMemoriesAsync(String spaceId, MemorySearchFilter filters) {
        return Mono.defer(() -> {
            Map<String, Object> body = filters != null ? filters.toDict() : Map.of();
            String url = "/spaces/" + UrlUtils.encodePathSegment(spaceId, "spaceId")
                    + "/memories/search";
            return getDataPlaneClient().post(url, null, body)
                    .map(result -> parseResult(result, MemorySearchResponse.class));
        });
    }

    /** Async variant of {@link #searchMemories(String)}. */
    public Mono<MemorySearchResponse> searchMemoriesAsync(String spaceId) {
        return searchMemoriesAsync(spaceId, null);
    }

    /** Async variant of {@link #listMemories(String, int, int, MemoryListFilter)}. */
    public Mono<MemoryListResponse> listMemoriesAsync(String spaceId, int limit, int offset,
                                                        MemoryListFilter filters) {
        return Mono.defer(() -> {
            Map<String, List<String>> query = paginationQuery(limit, offset);
            if (filters != null) {
                filters.toDict().forEach(
                        (key, value) -> query.put(key, List.of(String.valueOf(value))));
            }
            String url = "/spaces/" + UrlUtils.encodePathSegment(spaceId, "spaceId") + "/memories";
            return getDataPlaneClient().request("GET", url, null, null, query)
                    .map(result -> parseResult(result, MemoryListResponse.class));
        });
    }

    /** Async variant of {@link #listMemories(String)}. */
    public Mono<MemoryListResponse> listMemoriesAsync(String spaceId) {
        return listMemoriesAsync(spaceId, 10, 0, null);
    }

    /** Async variant of {@link #getMemory(String, String)}. */
    public Mono<MemoryInfo> getMemoryAsync(String spaceId, String memoryId) {
        return Mono.defer(() -> {
            String url = "/spaces/" + UrlUtils.encodePathSegment(spaceId, "spaceId")
                    + "/memories/" + UrlUtils.encodePathSegment(memoryId, "memoryId");
            return getDataPlaneClient().get(url)
                    .map(result -> parseResult(result, MemoryInfo.class));
        });
    }

    /** Async variant of {@link #deleteMemory(String, String)}. */
    public Mono<Void> deleteMemoryAsync(String spaceId, String memoryId) {
        return Mono.defer(() -> {
            String url = "/spaces/" + UrlUtils.encodePathSegment(spaceId, "spaceId")
                    + "/memories/" + UrlUtils.encodePathSegment(memoryId, "memoryId");
            return getDataPlaneClient().delete(url)
                    .doOnNext(this::ensureSuccess)
                    .then();
        });
    }
}
