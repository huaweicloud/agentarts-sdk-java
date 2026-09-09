package com.huaweicloud.agentarts.sdk.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.huaweicloud.agentarts.sdk.core.APIException;
import com.huaweicloud.agentarts.sdk.core.Constants;
import com.huaweicloud.agentarts.sdk.core.SignMode;
import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import com.huaweicloud.agentarts.sdk.core.util.UrlUtils;
import com.huaweicloud.agentarts.sdk.service.http.BaseHttpClient;
import com.huaweicloud.agentarts.sdk.service.http.RequestConfig;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import com.huaweicloud.agentarts.sdk.service.runtime.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Runtime client for managing AgentArts Runtime agents and data-plane operations.
 *
 * <p>Dual-plane architecture:</p>
 * <ul>
 *   <li><b>Control Plane</b> (AK/SK signed) — agent CRUD, endpoint CRUD</li>
 *   <li><b>Data Plane</b> (configurable signing or Bearer token) — invoke, exec, upload, download, sessions</li>
 * </ul>
 */
public class RuntimeClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeClient.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = JsonUtils.MAPPER;

    private final String region;
    private final boolean verifySsl;
    private final SignMode signMode;

    private BaseHttpClient controlClient;
    private BaseHttpClient dataClient;
    private String authToken;

    /**
     * Optional data-plane endpoint override. When set (e.g. from an agent's
     * {@code access_endpoint} discovered via the control plane), the data client
     * targets this URL instead of {@link Constants#getRuntimeDataPlaneEndpoint()}.
     */
    private String dataEndpointOverride;

    public synchronized void setDataPlaneEndpoint(String endpoint) {
        this.dataEndpointOverride = endpoint;
        if (dataClient != null) {
            dataClient.close();
            dataClient = null;
        }
    }

    public RuntimeClient(String region, boolean verifySsl, SignMode signMode) {
        this(region, verifySsl, signMode, null, null);
    }

    RuntimeClient(String region, boolean verifySsl, SignMode signMode,
                  BaseHttpClient controlClient, BaseHttpClient dataClient) {
        this.region = region != null ? region : Constants.getRegion();
        this.verifySsl = verifySsl;
        this.signMode = signMode != null ? signMode : SignMode.SDK_HMAC_SHA256;
        this.controlClient = controlClient;
        this.dataClient = dataClient;
    }

    public RuntimeClient(String region, boolean verifySsl) {
        this(region, verifySsl, SignMode.SDK_HMAC_SHA256);
    }

    public RuntimeClient(String region) {
        this(region, true, SignMode.SDK_HMAC_SHA256);
    }

    public RuntimeClient() {
        this(null, true, SignMode.SDK_HMAC_SHA256);
    }

    public synchronized void setAuthToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        this.authToken = token;
        if (dataClient != null) {
            dataClient.setAuthToken("Bearer", token);
        }
    }

    // ========================
    // Lazy client initialization
    // ========================

    private synchronized BaseHttpClient getControlClient() {
        if (controlClient == null) {
            String endpoint = Constants.getControlPlaneEndpoint(region) + "/v1/core";
            RequestConfig config = RequestConfig.builder()
                    .baseUrl(endpoint)
                    .verifySsl(verifySsl)
                    .build();
            controlClient = new BaseHttpClient(config, true, SignMode.SDK_HMAC_SHA256, region);
        }
        return controlClient;
    }

    private synchronized BaseHttpClient getDataClient() {
        if (dataClient == null) {
            String endpoint = dataEndpointOverride != null
                    ? Constants.ensureHttps(dataEndpointOverride)
                    : Constants.getRuntimeDataPlaneEndpoint();
            boolean useAkSk = (signMode == SignMode.V11_HMAC_SHA256);
            RequestConfig config = RequestConfig.builder()
                    .baseUrl(endpoint)
                    .verifySsl(verifySsl)
                    .build();
            dataClient = new BaseHttpClient(config, useAkSk, signMode, region);
            if (JsonUtils.isNotBlank(authToken)) {
                dataClient.setAuthToken("Bearer", authToken);
            }
        }
        return dataClient;
    }

    // ========================
    // Control Plane: Agent CRUD
    // ========================

    /**
     * Create a Runtime agent.
     *
     * @param req create agent request with all configuration fields
     * @return created agent info
     */
    public AgentInfo createAgent(CreateAgentRequest req) {
        Objects.requireNonNull(req, "CreateAgentRequest must not be null");
        RequestResult result = getControlClient().post("/runtimes", req).block();
        return parseResult(result, AgentInfo.class, "create_agent");
    }

    /** Convenience: create an agent with name and description only. */
    public AgentInfo createAgent(String name, String description) {
        return createAgent(new CreateAgentRequest().withName(name).withDescription(description));
    }

    /**
     * Update a Runtime agent by ID.
     *
     * @param agentId agent ID to update
     * @param req     update request with fields to change
     * @return updated agent info
     */
    public AgentInfo updateAgent(String agentId, UpdateAgentRequest req) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(req, "UpdateAgentRequest must not be null");
        RequestResult result = getControlClient().put(
                "/runtimes/" + UrlUtils.encodePathSegment(agentId, "agentId"), req).block();
        return parseResult(result, AgentInfo.class, "update_agent");
    }

    /**
     * Create or update an agent: find by name first, then create or update.
     *
     * @param agentName agent name to find
     * @param req       create request (used for both create and update paths)
     * @return agent info
     */
    public AgentInfo createOrUpdateAgent(String agentName, CreateAgentRequest req) {
        Objects.requireNonNull(agentName, "agentName must not be null");
        Objects.requireNonNull(req, "CreateAgentRequest must not be null");
        AgentInfo existing = findAgentByName(agentName);
        if (existing != null) {
            UpdateAgentRequest updateReq = new UpdateAgentRequest()
                    .withDescription(req.getDescription())
                    .withArtifactSource(req.getArtifactSource())
                    .withStorageConfig(req.getStorageConfig())
                    .withInvokeConfig(req.getInvokeConfig())
                    .withNetworkConfig(req.getNetworkConfig())
                    .withObservability(req.getObservability())
                    .withExecutionAgencyName(req.getExecutionAgencyName())
                    .withAgentGatewayId(req.getAgentGatewayId())
                    .withEnvironmentVariables(req.getEnvironmentVariables())
                    .withTags(req.getTags());
            return updateAgent(existing.getId(), updateReq);
        } else {
            req.setName(agentName);
            return createAgent(req);
        }
    }

    public AgentListResponse getAgents(String agentName, int offset, int limit) {
        Map<String, List<String>> query = new LinkedHashMap<>();
        query.put("offset", List.of(String.valueOf(offset)));
        query.put("limit", List.of(String.valueOf(limit)));
        if (JsonUtils.isNotBlank(agentName)) {
            query.put("name", List.of(agentName));
        }
        RequestResult result = getControlClient()
                .request("GET", "/runtimes", null, null, query).block();
        return parseResult(result, AgentListResponse.class, "get_agents");
    }

    public AgentListResponse getAgents() {
        return getAgents(null, 1, 10);
    }

    public AgentInfo findAgentByName(String agentName) {
        AgentListResponse response = getAgents(agentName, 1, 10);
        if (response.getItems() == null) return null;
        for (AgentInfo a : response.getItems()) {
            if (agentName.equals(a.getName())) return a;
        }
        return null;
    }

    public AgentInfo findAgentById(String agentId) {
        RequestResult result = getControlClient().get(
                "/runtimes/" + UrlUtils.encodePathSegment(agentId, "agentId")).block();
        return parseResult(result, AgentInfo.class, "find_agent_by_id");
    }

    public boolean deleteAgentByName(String agentName) {
        AgentInfo agent = findAgentByName(agentName);
        if (agent == null) return false;
        RequestResult result = getControlClient().delete(
                "/runtimes/" + UrlUtils.encodePathSegment(agent.getId(), "agentId")).block();
        if (result == null || !result.isSuccess()) {
            throw new APIException(
                    result == null ? 0 : result.getStatusCode(),
                    "delete_agent_by_name",
                    result == null ? "null response" : result.getError());
        }
        return true;
    }

    // ========================
    // Control Plane: Endpoint CRUD
    // ========================

    public AgentEndpointInfo createAgentEndpoint(String agentId, String endpointName,
                                                   String endpointType, Map<String, Object> config,
                                                   String targetVersionName) {
        CreateAgentEndpointRequest req = new CreateAgentEndpointRequest()
                .withEndpointName(endpointName)
                .withName(endpointName)
                .withAgentId(agentId)
                .withEndpointType(endpointType)
                .withTargetVersionName(targetVersionName)
                .withConfig(config);

        RequestResult result = getControlClient().post(
                "/runtimes/" + UrlUtils.encodePathSegment(agentId, "agentId") + "/endpoints", req).block();
        return parseResult(result, AgentEndpointInfo.class, "create_agent_endpoint");
    }

    public AgentEndpointInfo createAgentEndpoint(String agentId, String endpointName) {
        return createAgentEndpoint(agentId, endpointName, "invocations", null, "v1");
    }

    /**
     * Update an existing agent endpoint's config.
     *
     * @param agentId    agent the endpoint belongs to
     * @param endpointId endpoint ID (the UUID returned by {@link #createAgentEndpoint})
     * @param config     new endpoint configuration
     */
    public AgentEndpointInfo updateAgentEndpoint(String agentId, String endpointId,
                                                   Map<String, Object> config) {
        UpdateAgentEndpointRequest req = new UpdateAgentEndpointRequest()
                .withConfig(config);

        RequestResult result = getControlClient().put(
                "/runtimes/" + UrlUtils.encodePathSegment(agentId, "agentId")
                        + "/endpoints/" + UrlUtils.encodePathSegment(endpointId, "endpointId"), req).block();
        return parseResult(result, AgentEndpointInfo.class, "update_agent_endpoint");
    }

    /**
     * Delete an agent endpoint.
     *
     * @param agentId    agent the endpoint belongs to
     * @param endpointId endpoint ID (the UUID returned by {@link #createAgentEndpoint})
     */
    public AgentEndpointInfo deleteAgentEndpoint(String agentId, String endpointId) {
        RequestResult result = getControlClient().delete(
                "/runtimes/" + UrlUtils.encodePathSegment(agentId, "agentId")
                        + "/endpoints/" + UrlUtils.encodePathSegment(endpointId, "endpointId")).block();
        return parseResult(result, AgentEndpointInfo.class, "delete_agent_endpoint");
    }

    /**
     * Find an agent endpoint by ID.
     *
     * @param agentId    agent the endpoint belongs to
     * @param endpointId endpoint ID (the UUID returned by {@link #createAgentEndpoint})
     * @return the endpoint info
     */
    public AgentEndpointInfo findAgentEndpoint(String agentId, String endpointId) {
        RequestResult result = getControlClient().get(
                "/runtimes/" + UrlUtils.encodePathSegment(agentId, "agentId")
                        + "/endpoints/" + UrlUtils.encodePathSegment(endpointId, "endpointId")).block();
        return parseResult(result, AgentEndpointInfo.class, "find_agent_endpoint");
    }

    // ========================
    // Data Plane: Invoke / Exec
    // ========================

    @SuppressWarnings("unchecked")
    public Map<String, Object> invokeAgent(String agentName, String sessionId, String payload,
                                            String bearerToken, String endpoint, int timeout,
                                            String userId, String customPath) {
        try (RequestResult result = invokeAgentRaw(
                agentName, sessionId, payload, bearerToken, endpoint, timeout, userId, customPath)) {
            if (result.isStreaming()) {
                throw new APIException(result.getStatusCode(), "invoke_agent",
                        "streaming response requires invokeAgentRaw");
            }
            return check(result, "invoke_agent");
        }
    }

    /** Invoke an agent and return the raw response, including SSE or NDJSON streams. */
    public RequestResult invokeAgentRaw(String agentName, String sessionId, String payload,
                                         String bearerToken, String endpoint, int timeout,
                                         String userId, String customPath) {
        String path = "/runtimes/" + UrlUtils.encodePathSegment(agentName, "agentName") + "/invocations";
        if (JsonUtils.isNotBlank(customPath)) {
            path += "/" + UrlUtils.encodeRelativePath(customPath, "customPath");
        }

        Map<String, String> headers = buildDataHeaders(sessionId, bearerToken, userId);
        RequestResult result = getDataClient().request(
                "POST", dataUrl(path, endpoint), headers, payload, null, (double) timeout).block();
        if (result == null) {
            throw new APIException(0, "invoke_agent", "null response");
        }
        if (!result.isSuccess()) {
            try (result) {
                throw new APIException(result.getStatusCode(), "invoke_agent", result.getError());
            }
        }
        return result;
    }

    public Map<String, Object> invokeAgent(String agentName, String sessionId, String payload) {
        return invokeAgent(agentName, sessionId, payload, null, null, 900, null, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> execCommand(String agentName, String sessionId, List<String> command,
                                            boolean chunked, String bearerToken, String endpoint,
                                            String userId, int timeout) {
        try (RequestResult result = execCommandRaw(agentName, sessionId, command, chunked,
                bearerToken, endpoint, userId, timeout)) {
            if (result.isStreaming()) {
                throw new APIException(result.getStatusCode(), "exec_command",
                        "streaming response requires execCommandRaw");
            }
            return check(result, "exec_command");
        }
    }

    /**
     * Execute a command and return the raw response, including an NDJSON stream in chunked mode.
     */
    public RequestResult execCommandRaw(
            String agentName, String sessionId, List<String> command,
            boolean chunked, String bearerToken, String endpoint,
            String userId, int timeout) {
        ExecCommandRequest req = new ExecCommandRequest().withCommand(command);

        Map<String, String> headers = buildDataHeaders(sessionId, bearerToken, userId);
        if (chunked) {
            headers.put("Command-Type", "chunked");
        }
        RequestResult result = getDataClient().request(
                "POST", dataUrl("/runtimes/" + UrlUtils.encodePathSegment(agentName, "agentName")
                        + "/commands", endpoint),
                headers, req, null, (double) timeout).block();
        if (result == null) {
            throw new APIException(0, "exec_command", "null response");
        }
        if (!result.isSuccess()) {
            try (result) {
                throw new APIException(result.getStatusCode(), "exec_command", result.getError());
            }
        }
        return result;
    }

    public Map<String, Object> execCommand(String agentName, String sessionId, String command) {
        return execCommand(agentName, sessionId, List.of(command), false, null, null, null, 900);
    }

    // ========================
    // Data Plane: File Operations
    // ========================

    @SuppressWarnings("unchecked")
    public Map<String, Object> uploadFiles(String agentName, String sessionId,
                                            List<Map<String, Object>> files, String remotePath,
                                            Integer fileUserId, Integer fileGroupId, String fileMode,
                                            String bearerToken, String endpoint, String userId,
                                            int timeout) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be empty");
        }
        String path = (remotePath != null && !remotePath.isBlank()) ? remotePath : "/home/user/";

        Map<String, String> headers = buildDataHeaders(sessionId, bearerToken, userId);
        // file metadata travels as query params (matches the reference CLI wire format).
        Map<String, List<String>> params = new LinkedHashMap<>();
        if (fileUserId != null) params.put("user_id", List.of(String.valueOf(fileUserId)));
        if (fileGroupId != null) params.put("group_id", List.of(String.valueOf(fileGroupId)));
        if (fileMode != null) params.put("file_mode", List.of(fileMode));

        final String uploadUrl = "/runtimes/" + UrlUtils.encodePathSegment(agentName, "agentName")
                + "/upload-files";
        RequestResult result;
        if (files.size() == 1) {
            // Single file: application/octet-stream, raw bytes streamed, path = remote FILE path.
            Map<String, Object> file = files.get(0);
            byte[] content = fileContentBytes(file.get("content"));
            if (content == null) {
                throw new IllegalArgumentException(
                        "File \"content\" (bytes) is required for single-file upload");
            }
            String filename = (file.get("filename") != null)
                    ? String.valueOf(file.get("filename")) : "file_0";
            String fileRemote = (file.get("path") != null && !String.valueOf(file.get("path")).isBlank())
                    ? String.valueOf(file.get("path"))
                    : (path.endsWith("/") ? path + filename : path + "/" + filename);
            params.put("path", List.of(fileRemote));
            headers.put("Content-Type", "application/octet-stream");
            result = getDataClient().request(
                    "POST", dataUrl(uploadUrl, endpoint), headers, content, params, (double) timeout).block();
        } else {
            // Multiple files: multipart/form-data, path = remote DIRECTORY.
            params.put("path", List.of(path));
            String boundary = "----agentarts-" + java.util.UUID.randomUUID();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
                long maximumBodyBytes = getDataClient().getConfig().getMaxRequestBodyBytes();
                int idx = 0;
                for (Map<String, Object> file : files) {
                    byte[] content = fileContentBytes(file.get("content"));
                    if (content == null) {
                        continue;
                    }
                    String filename = (file.get("filename") != null)
                            ? String.valueOf(file.get("filename")) : "file_" + idx;
                    filename = sanitizeMultipartFilename(filename);
                    String partHead = "--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"file\"; filename=\""
                            + filename + "\"\r\n"
                            + "Content-Type: application/octet-stream\r\n\r\n";
                    byte[] partHeader = partHead.getBytes(StandardCharsets.UTF_8);
                    ensureMultipartCapacity(out.size(), partHeader.length, content.length,
                            crlf.length, maximumBodyBytes);
                    out.write(partHeader);
                    out.write(content);
                    out.write(crlf);
                    idx++;
                }
                byte[] closingBoundary = ("--" + boundary + "--\r\n")
                        .getBytes(StandardCharsets.UTF_8);
                ensureMultipartCapacity(out.size(), closingBoundary.length, 0, 0,
                        maximumBodyBytes);
                out.write(closingBoundary);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Failed to build multipart upload body", e);
            }
            headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);
            result = getDataClient().request(
                    "POST", dataUrl(uploadUrl, endpoint), headers, out.toByteArray(), params,
                    (double) timeout).block();
        }
        return check(result, "upload_files");
    }

    /** Coerce a file spec's "content" field to raw bytes (byte[] or UTF-8 String). */
    private static byte[] fileContentBytes(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof byte[]) {
            return (byte[]) content;
        }
        if (content instanceof String) {
            return ((String) content).getBytes(StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String sanitizeMultipartFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Multipart filename must not be blank");
        }
        for (int index = 0; index < filename.length(); index++) {
            char value = filename.charAt(index);
            if (value == '\r' || value == '\n' || value == '\0' || Character.isISOControl(value)) {
                throw new IllegalArgumentException("Multipart filename contains control characters");
            }
        }
        return filename.replace("\\", "%5C").replace("\"", "%22");
    }

    private static void ensureMultipartCapacity(long currentSize, long headerSize,
                                                long contentSize, long suffixSize,
                                                long maximumBodyBytes) {
        long nextSize;
        try {
            nextSize = Math.addExact(currentSize,
                    Math.addExact(headerSize, Math.addExact(contentSize, suffixSize)));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Multipart request size overflow", e);
        }
        if (nextSize > maximumBodyBytes) {
            throw new IllegalArgumentException(
                    "Multipart request exceeds configured limit of "
                            + maximumBodyBytes + " bytes");
        }
    }

    public Map<String, Object> uploadFiles(String agentName, String sessionId, List<Map<String, Object>> files) {
        return uploadFiles(agentName, sessionId, files, null, null, null, null, null, null, null, 900);
    }

    public RequestResult downloadFiles(String agentName, String sessionId, String path,
                                        boolean recursive, String bearerToken, String endpoint,
                                        String userId, int timeout) {
        Map<String, String> headers = buildDataHeaders(sessionId, bearerToken, userId);
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("path", List.of(path));
        if (recursive) params.put("recursive", List.of("true"));
        return getDataClient().requestBinaryStream(
                "GET", dataUrl("/runtimes/" + UrlUtils.encodePathSegment(agentName, "agentName")
                        + "/download-files", endpoint),
                headers, null, params, (double) timeout).block();
    }

    public RequestResult downloadFiles(String agentName, String sessionId, String path) {
        return downloadFiles(agentName, sessionId, path, false, null, null, null, 900);
    }

    // ========================
    // Data Plane: Session Management
    // ========================

    @SuppressWarnings("unchecked")
    public Map<String, Object> startSession(String agentName, String bearerToken,
                                             String endpoint, String userId, int timeout) {
        Map<String, String> headers = buildDataHeaders(null, bearerToken, userId);
        RequestResult result = getDataClient().request(
                "POST", dataUrl("/runtimes/" + UrlUtils.encodePathSegment(agentName, "agentName")
                        + "/sessions-start", endpoint),
                headers, Map.of(), null, (double) timeout).block();
        return check(result, "start_session");
    }

    public Map<String, Object> startSession(String agentName) {
        return startSession(agentName, null, null, null, 30);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> stopSession(String agentName, String sessionId,
                                            String bearerToken, String endpoint,
                                            String userId, int timeout) {
        StopSessionRequest req = new StopSessionRequest()
                .withSessionId(sessionId);

        Map<String, String> headers = buildDataHeaders(sessionId, bearerToken, userId);
        RequestResult result = getDataClient().request(
                "POST", dataUrl("/runtimes/" + UrlUtils.encodePathSegment(agentName, "agentName")
                        + "/sessions-stop", endpoint),
                headers, req, null, (double) timeout).block();
        return check(result, "stop_session");
    }

    public Map<String, Object> stopSession(String agentName, String sessionId) {
        return stopSession(agentName, sessionId, null, null, null, 30);
    }

    // ========================
    // Helpers
    // ========================

    private Map<String, String> buildDataHeaders(String sessionId, String bearerToken, String userId) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (JsonUtils.isNotBlank(sessionId)) {
            headers.put(Constants.SESSION_HEADER, sessionId);
        }
        if (JsonUtils.isNotBlank(userId)) {
            headers.put(Constants.USER_ID_HEADER, userId);
        }
        if (JsonUtils.isNotBlank(bearerToken)) {
            headers.put("Authorization", "Bearer " + bearerToken.trim());
        }
        return headers;
    }

    private String dataUrl(String path, String endpoint) {
        if (JsonUtils.isBlank(endpoint)) {
            return path;
        }
        String base = Constants.ensureHttps(endpoint);
        if (base.endsWith("/") && path.startsWith("/")) {
            return base + path.substring(1);
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }

    /**
     * Parse response into a typed POJO.
     */
    private <T> T parseResult(RequestResult result, Class<T> type, String operation) {
        if (result == null) {
            throw new APIException(0, operation, "null response");
        }
        if (!result.isSuccess()) {
            throw new APIException(result.getStatusCode(), operation, result.getError());
        }
        try {
            JsonNode data = result.getDataAsJson();
            if (data != null) return MAPPER.treeToValue(data, type);
            return null;
        } catch (Exception e) {
            throw new APIException(result.getStatusCode(), operation,
                    "failed to parse response: " + e.getMessage(), e);
        }
    }

    /**
     * Parse response as Map (for flexible/opaque responses like invoke, exec, upload).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> check(RequestResult result, String operation) {
        return parseResult(result, Map.class, operation);
    }

    @Override
    public synchronized void close() {
        if (controlClient != null) {
            controlClient.close();
            controlClient = null;
        }
        if (dataClient != null) {
            dataClient.close();
            dataClient = null;
        }
    }
}
