package com.huaweicloud.agentarts.sdk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.agentarts.sdk.core.APIException;
import com.huaweicloud.agentarts.sdk.core.Constants;
import com.huaweicloud.agentarts.sdk.core.SignMode;
import com.huaweicloud.agentarts.sdk.core.util.UrlUtils;
import com.huaweicloud.agentarts.sdk.service.http.BaseHttpClient;
import com.huaweicloud.agentarts.sdk.service.http.RequestConfig;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import com.huaweicloud.agentarts.sdk.tools.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Code Interpreter client with dual-plane architecture.
 *
 * <p>Control plane uses AK/SK signing. Data plane uses API Key or IAM.</p>
 *
 * <h2>Control Plane (AK/SK):</h2>
 * create/list/update/get/delete_code_interpreter
 *
 * <h2>Data Plane (API Key or IAM):</h2>
 * start_session/stop_session/get_session/invoke/execute_code/execute_command/
 * upload_file/upload_files/download_file/download_files/install_packages/clear_context
 */
public class CodeInterpreterClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CodeInterpreterClient.class);
    private static final ObjectMapper MAPPER = com.huaweicloud.agentarts.sdk.core.util.JsonUtils.MAPPER;
    private static final String DEFAULT_PATH = "/home/user";

    private final String region;
    private final String dataEndpoint;
    private final String authType;
    private final boolean verifySsl;

    private BaseHttpClient controlClient;
    private BaseHttpClient dataClient;
    private String codeInterpreterName;
    private String sessionId;

    public CodeInterpreterClient(String region, String dataEndpoint, String authType, boolean verifySsl) {
        this(region, dataEndpoint, authType, verifySsl, null, null);
    }

    CodeInterpreterClient(String region, String dataEndpoint, String authType, boolean verifySsl,
                          BaseHttpClient controlClient, BaseHttpClient dataClient) {
        this.region = region != null ? region : Constants.getRegion();
        this.dataEndpoint = dataEndpoint;
        String normalizedAuthType = authType != null ? authType.trim().toUpperCase(java.util.Locale.ROOT) : "API_KEY";
        if (!"API_KEY".equals(normalizedAuthType) && !"IAM".equals(normalizedAuthType)) {
            throw new IllegalArgumentException("authType must be API_KEY or IAM");
        }
        this.authType = normalizedAuthType;
        this.verifySsl = verifySsl;
        this.controlClient = controlClient;
        this.dataClient = dataClient;
    }

    public CodeInterpreterClient(String region, String dataEndpoint) {
        this(region, dataEndpoint, "API_KEY", true);
    }

    public CodeInterpreterClient(String region) {
        this(region, null, "API_KEY", true);
    }

    // ========================
    // Properties
    // ========================

    public String getCodeInterpreterName() { return codeInterpreterName; }
    public void setCodeInterpreterName(String name) { this.codeInterpreterName = name; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    // ========================
    // Lazy clients
    // ========================

    private synchronized BaseHttpClient getControlClient() {
        if (controlClient == null) {
            String endpoint = Constants.getControlPlaneEndpoint(region) + "/v1/core";
            RequestConfig config = RequestConfig.builder().baseUrl(endpoint).verifySsl(verifySsl).build();
            controlClient = new BaseHttpClient(config, true, SignMode.SDK_HMAC_SHA256, region);
        }
        return controlClient;
    }

    private synchronized BaseHttpClient getDataClient() {
        if (dataClient == null) {
            String endpoint = Constants.getCodeInterpreterDataPlaneEndpoint(dataEndpoint);
            if (endpoint.isBlank()) {
                throw new IllegalStateException(
                        "Code Interpreter data endpoint is required; pass dataEndpoint or set "
                                + "AGENTARTS_CODEINTERPRETER_DATA_ENDPOINT");
            }
            boolean useAkSk = "IAM".equals(authType);
            RequestConfig config = RequestConfig.builder().baseUrl(endpoint).verifySsl(verifySsl).build();
            dataClient = new BaseHttpClient(config, useAkSk, SignMode.SDK_HMAC_SHA256, region);
            if (!useAkSk) {
                String key = System.getenv("HUAWEICLOUD_SDK_CODE_INTERPRETER_API_KEY");
                if (com.huaweicloud.agentarts.sdk.core.util.JsonUtils.isNotBlank(key)) {
                    dataClient.setAuthToken("Bearer", key);
                } else {
                    dataClient.close();
                    dataClient = null;
                    throw new IllegalStateException(
                            "Code Interpreter API key is required for API_KEY auth; "
                                    + "set HUAWEICLOUD_SDK_CODE_INTERPRETER_API_KEY");
                }
            }
        }
        return dataClient;
    }

    // ========================
    // Control Plane: CRUD
    // ========================

    private static final java.util.regex.Pattern NAME_PATTERN =
            java.util.regex.Pattern.compile("[a-z][a-z0-9-]{0,38}[a-z0-9]$");

    /**
     * Create a Code Interpreter with full configuration.
     *
     * @param req create request with all fields
     * @return created interpreter info
     */
    public CodeInterpreterInfo createCodeInterpreter(CreateCodeInterpreterRequest req) {
        Objects.requireNonNull(req, "CreateCodeInterpreterRequest must not be null");
        String name = req.getName();
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Name must start with lowercase letter, end with lowercase letter or digit, " +
                    "contain only lowercase letters, digits, and hyphens, and be 2-40 characters long.");
        }
        String resolvedAuthType = req.getAuthType() != null ? req.getAuthType() : "API_KEY";
        if ("API_KEY".equals(resolvedAuthType) && req.getApiKeyName() == null) {
            throw new IllegalArgumentException("API_KEY auth_type requires api_key_name.");
        }
        req.setAuthType(resolvedAuthType);

        RequestResult r = getControlClient().post("/code-interpreters", null, req).block();
        return parseResult(r, CodeInterpreterInfo.class);
    }

    /** Convenience: create with name only (IAM auth). */
    public CodeInterpreterInfo createCodeInterpreter(String name) {
        return createCodeInterpreter(new CreateCodeInterpreterRequest()
                .withName(name).withAuthType("IAM"));
    }

    /** Convenience: create with name and description (IAM auth). */
    public CodeInterpreterInfo createCodeInterpreter(String name, String description) {
        return createCodeInterpreter(new CreateCodeInterpreterRequest()
                .withName(name).withAuthType("IAM").withDescription(description));
    }

    public CodeInterpreterListResponse listCodeInterpreters(String name, int limit, int offset) {
        Map<String, List<String>> query = new LinkedHashMap<>();
        query.put("limit", List.of(String.valueOf(limit)));
        query.put("offset", List.of(String.valueOf(offset)));
        if (name != null) {
            query.put("name", List.of(name));
        }
        RequestResult r = getControlClient()
                .request("GET", "/code-interpreters", null, null, query).block();
        return parseResult(r, CodeInterpreterListResponse.class);
    }

    public CodeInterpreterListResponse listCodeInterpreters() {
        return listCodeInterpreters(null, 10, 0);
    }

    /**
     * Update a Code Interpreter with typed parameters.
     *
     * @param codeInterpreterId interpreter ID
     * @param observability     observability configuration (nullable)
     * @param tags              tag list (nullable)
     * @return updated interpreter data
     */
    public CodeInterpreterInfo updateCodeInterpreter(String codeInterpreterId,
                                                       Map<String, Object> observability,
                                                       List<Map<String, String>> tags) {
        UpdateCodeInterpreterRequest req = new UpdateCodeInterpreterRequest()
                .withObservability(observability)
                .withTags(tags);

        RequestResult r = getControlClient().put(
                "/code-interpreters/" + UrlUtils.encodePathSegment(
                        codeInterpreterId, "codeInterpreterId"), null, req).block();
        return parseResult(r, CodeInterpreterInfo.class);
    }

    /** Convenience overload: update with a generic map (backward compatible). */
    @SuppressWarnings("unchecked")
    public CodeInterpreterInfo updateCodeInterpreter(String codeInterpreterId, Map<String, Object> updates) {
        Map<String, Object> observability = (Map<String, Object>) updates.get("observability");
        List<Map<String, String>> tags = (List<Map<String, String>>) updates.get("tags");
        return updateCodeInterpreter(codeInterpreterId, observability, tags);
    }

    public CodeInterpreterInfo getCodeInterpreter(String codeInterpreterId) {
        RequestResult r = getControlClient().get(
                "/code-interpreters/" + UrlUtils.encodePathSegment(
                        codeInterpreterId, "codeInterpreterId")).block();
        return parseResult(r, CodeInterpreterInfo.class);
    }

    public void deleteCodeInterpreter(String codeInterpreterId) {
        ensureSuccess(getControlClient().delete(
                "/code-interpreters/" + UrlUtils.encodePathSegment(
                        codeInterpreterId, "codeInterpreterId")).block());
    }

    // ========================
    // Data Plane: Sessions
    // ========================

    /**
     * Start a code interpreter session.
     * PUT /v1/code-interpreters/{name}/sessions-start
     */
    public String startSession(String codeInterpreterName, String sessionName, Integer sessionTimeout) {
        StartCodeInterpreterSessionRequest req = new StartCodeInterpreterSessionRequest()
                .withName(sessionName)
                .withSessionTimeout(sessionTimeout);

        String url = "/v1/code-interpreters/" + UrlUtils.encodePathSegment(
                codeInterpreterName, "codeInterpreterName") + "/sessions-start";
        RequestResult r = getDataClient().put(url, null, req).block();
        Map<String, Object> data = parseMap(r);
        this.codeInterpreterName = codeInterpreterName;
        this.sessionId = (String) data.get("session_id");
        return this.sessionId;
    }

    public String startSession(String codeInterpreterName, String sessionName) {
        return startSession(codeInterpreterName, sessionName, 900);
    }

    /**
     * Get session details.
     * GET /v1/code-interpreters/{name}/sessions-get
     */
    public CodeInterpreterSessionInfo getSession(String codeInterpreterName, String sessionId) {
        String sid = sessionId != null ? sessionId : this.sessionId;
        String url = "/v1/code-interpreters/" + UrlUtils.encodePathSegment(
                codeInterpreterName, "codeInterpreterName") + "/sessions-get";
        Map<String, String> headers = sid != null
                ? Map.of(Constants.CODE_INTERPRETER_SESSION_HEADER, sid) : null;
        RequestResult r = getDataClient().get(url, headers).block();
        return parseResult(r, CodeInterpreterSessionInfo.class);
    }

    /**
     * Stop the current session.
     * PUT /v1/code-interpreters/{name}/sessions-stop
     */
    public boolean stopSession() {
        if (sessionId == null || codeInterpreterName == null) return true;

        String url = "/v1/code-interpreters/" + UrlUtils.encodePathSegment(
                codeInterpreterName, "codeInterpreterName") + "/sessions-stop";
        Map<String, String> headers = Map.of(Constants.CODE_INTERPRETER_SESSION_HEADER, sessionId);
        RequestResult r = getDataClient().put(url, headers, null).block();
        ensureSuccess(r);
        this.codeInterpreterName = null;
        this.sessionId = null;
        return true;
    }

    // ========================
    // Data Plane: Invoke
    // ========================

    /**
     * Invoke a code interpreter session.
     * POST /v1/code-interpreters/{name}/invoke
     */
    public Map<String, Object> invoke(String operateType, Map<String, Object> arguments) {
        if (sessionId == null || codeInterpreterName == null) {
            throw new IllegalStateException("No active session — call startSession() first");
        }

        CodeInterpreterInvokeRequest req = new CodeInterpreterInvokeRequest()
                .withOperateType(operateType)
                .withArguments(arguments);

        String url = "/v1/code-interpreters/" + UrlUtils.encodePathSegment(
                codeInterpreterName, "codeInterpreterName") + "/invoke";
        Map<String, String> headers = Map.of(Constants.CODE_INTERPRETER_SESSION_HEADER, sessionId);
        RequestResult r = getDataClient().post(url, headers, req).block();
        return parseMap(r);
    }

    private static final List<String> VALID_LANGUAGES = List.of("python");
    private static final java.util.regex.Pattern COMMAND_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9_\\-\\.=\\s/\\.:]+$");

    public Map<String, Object> executeCode(String code, String language, boolean clearContext) {
        String lang = language != null ? language : "python";
        if (!VALID_LANGUAGES.contains(lang)) {
            throw new IllegalArgumentException(
                    "Invalid language. Supported languages are: " + String.join(", ", VALID_LANGUAGES));
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("code", code);
        args.put("language", lang);
        args.put("clear_context", clearContext);
        return invoke("execute_code", args);
    }

    public Map<String, Object> executeCode(String code) {
        return executeCode(code, "python", false);
    }

    public Map<String, Object> executeCommand(String command) {
        if (!COMMAND_PATTERN.matcher(command).matches()) {
            throw new IllegalArgumentException("Invalid command format");
        }
        return invoke("execute_command", Map.of("command", command));
    }

    /**
     * Upload a file using write_files operation.
     *
     * @param path        file path
     * @param content     file content (text)
     * @param description file description
     * @return upload result
     */
    public Map<String, Object> uploadFile(String path, String content, String description) {
        String resolvedPath = resolvePath(path);
        Map<String, Object> fileContent = new LinkedHashMap<>();
        fileContent.put("path", resolvedPath);
        fileContent.put("text", content);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("write_contents", List.of(fileContent));
        return invoke("write_files", args);
    }

    /**
     * Upload multiple files using write_files operation.
     *
     * @param files list of file specs with "path" and "content" keys
     * @return upload result
     */
    public Map<String, Object> uploadFiles(List<Map<String, String>> files) {
        List<Map<String, Object>> writeContents = new ArrayList<>();
        for (Map<String, String> file : files) {
            Map<String, Object> fc = new LinkedHashMap<>();
            fc.put("path", resolvePath(file.get("path")));
            fc.put("text", file.get("content"));
            writeContents.add(fc);
        }

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("write_contents", writeContents);
        return invoke("write_files", args);
    }

    /**
     * Download a file using read_files operation.
     *
     * @param path file path (must start with /home/user)
     * @return file content (String for text files, byte[] for binary, or raw Map on parse failure)
     */
    @SuppressWarnings("unchecked")
    public Object downloadFile(String path) {
        String resolvedPath = validateDownloadPath(path);
        Map<String, Object> result = invoke("read_files", Map.of("paths", List.of(resolvedPath)));
        if (result == null) return null;

        // Parse response: result.result.content[0]
        Object resultObj = result.get("result");
        if (resultObj instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) resultObj;
            Object contentList = resultMap.get("content");
            if (contentList instanceof List) {
                List<Map<String, Object>> contents = (List<Map<String, Object>>) contentList;
                if (!contents.isEmpty()) {
                    return extractFileContent(contents.get(0));
                }
            }
        }
        // Fallback: return raw "content" field if present
        return result.get("content");
    }

    /**
     * Download multiple files using read_files operation.
     *
     * <p>Each content item is processed by its {@code type} field, matching the
     * Python SDK:
     * <ul>
     *   <li>{@code text}: key from the item's {@code uri}, value is the text</li>
     *   <li>{@code image}: key from the item's {@code uri}, value is the decoded bytes</li>
     *   <li>{@code resource}: key from the resource's {@code uri}; a {@code text}
     *       resource yields its text, a {@code blob} resource is Base64-decoded and
     *       returned as a UTF-8 String when possible, otherwise as raw bytes</li>
     * </ul>
     *
     * @param paths list of file paths
     * @return map of path → content (String for text, byte[] for binary)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> downloadFiles(List<String> paths) {
        List<String> resolvedPaths = new ArrayList<>(paths.size());
        for (String path : paths) {
            resolvedPaths.add(validateDownloadPath(path));
        }
        Map<String, Object> result = invoke("read_files", Map.of("paths", resolvedPaths));
        if (result == null) {
            throw new IllegalStateException("Could not read files: missing result");
        }
        Object resultObj = result.get("result");
        if (!(resultObj instanceof Map)) {
            throw new IllegalStateException("Could not read files: missing result");
        }
        Object contentList = ((Map<String, Object>) resultObj).get("content");
        if (!(contentList instanceof List)) {
            throw new IllegalStateException("Could not read files: missing content");
        }

        Map<String, Object> files = new LinkedHashMap<>();
        for (Map<String, Object> item : (List<Map<String, Object>>) contentList) {
            String type = (String) item.get("type");
            if ("text".equals(type)) {
                String uri = (String) item.getOrDefault("uri", "");
                files.put(uri.replace("file://", ""), item.getOrDefault("text", ""));
            } else if ("image".equals(type)) {
                String uri = (String) item.getOrDefault("uri", "");
                files.put(uri.replace("file://", ""),
                        Base64.getDecoder().decode((String) item.getOrDefault("data", "")));
            } else if ("resource".equals(type)) {
                Map<String, Object> resource = (Map<String, Object>) item.getOrDefault(
                        "resource", Collections.emptyMap());
                String resourceUri = (String) resource.getOrDefault("uri", "");
                String filePath = resourceUri.replace("file://", "");
                if ("text".equals(resource.get("type"))) {
                    files.put(filePath, resource.get("text"));
                } else if ("blob".equals(resource.get("type"))) {
                    byte[] raw = Base64.getDecoder().decode((String) resource.get("blob"));
                    files.put(filePath, decodeUtf8OrRaw(raw));
                }
            }
        }
        return files;
    }

    /**
     * Install Python packages via pip.
     *
     * @param packages list of package names (supports version specs like "numpy==1.24.3")
     * @param upgrade  whether to upgrade existing packages
     * @return command execution result
     */
    public Map<String, Object> installPackages(List<String> packages, boolean upgrade) {
        if (packages == null || packages.isEmpty()) {
            throw new IllegalArgumentException("Package list cannot be empty");
        }
        for (String pkg : packages) {
            if (pkg.chars().anyMatch(c -> c == ';' || c == '&' || c == '|' || c == '`' || c == '$')) {
                throw new IllegalArgumentException("Invalid package name: " + pkg);
            }
        }
        String packageStr = String.join(" ", packages);
        String upgradeFlag = upgrade ? " --upgrade" : "";
        String command = "pip install " + packageStr + upgradeFlag;
        return invoke("execute_command", Map.of("command", command));
    }

    public Map<String, Object> installPackages(List<String> packages) {
        return installPackages(packages, false);
    }

    /**
     * Clear interpreter context by executing code with clear_context=true.
     *
     * @return execution result
     */
    public Map<String, Object> clearContext() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("code", "# Context cleared");
        args.put("language", "python");
        args.put("clear_context", true);
        return invoke("execute_code", args);
    }

    // ========================
    // Helpers
    // ========================

    /**
     * Resolve file path: relative paths are joined with DEFAULT_PATH,
     * absolute paths must start with DEFAULT_PATH.
     */
    private String resolvePath(String path) {
        Objects.requireNonNull(path, "path must not be null");
        if (path.isBlank() || path.chars().anyMatch(c -> c < 0x20)) {
            throw new IllegalArgumentException("Path must not be blank or contain control characters");
        }
        String absolutePath = path.startsWith("/") ? path : DEFAULT_PATH + "/" + path;
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : absolutePath.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
            } else {
                segments.addLast(segment);
            }
        }
        String normalized = "/" + String.join("/", segments);
        if (!normalized.equals(DEFAULT_PATH) && !normalized.startsWith(DEFAULT_PATH + "/")) {
            throw new IllegalArgumentException("Invalid path. Path must start with " + DEFAULT_PATH);
        }
        return normalized;
    }

    /**
     * Validate download path: must start with DEFAULT_PATH.
     */
    private String validateDownloadPath(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("Invalid path. Path must start with " + DEFAULT_PATH);
        }
        return resolvePath(path);
    }

    /**
     * Parse response into a typed POJO.
     */
    private <T> T parseResult(RequestResult result, Class<T> type) {
        if (result == null || !result.isSuccess()) {
            int status = result != null ? result.getStatusCode() : 0;
            String err = result != null ? result.getError() : "null response";
            throw new APIException(status, "code_interpreter", err);
        }
        try {
            JsonNode data = result.getDataAsJson();
            if (data != null) return MAPPER.treeToValue(data, type);
            return null;
        } catch (Exception e) {
            throw new APIException(result.getStatusCode(), "code_interpreter",
                    "Failed to parse response: " + e.getMessage(), e);
        }
    }

    private void ensureSuccess(RequestResult result) {
        if (result == null || !result.isSuccess()) {
            int status = result != null ? result.getStatusCode() : 0;
            String err = result != null ? result.getError() : "null response";
            throw new APIException(status, "code_interpreter", err);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(RequestResult result) {
        return parseResult(result, Map.class);
    }

    /**
     * Extract file content from a content item (text/image/resource types).
     */
    @SuppressWarnings("unchecked")
    private Object extractFileContent(Map<String, Object> item) {
        String type = (String) item.get("type");
        if ("text".equals(type)) {
            return item.getOrDefault("text", "");
        }
        if ("image".equals(type)) {
            String data = (String) item.getOrDefault("data", "");
            return Base64.getDecoder().decode(data);
        }
        if ("resource".equals(type)) {
            Map<String, Object> resource = (Map<String, Object>) item.get("resource");
            if (resource != null) {
                String resourceType = (String) resource.get("type");
                if ("text".equals(resourceType)) {
                    return resource.get("text");
                }
                if ("blob".equals(resourceType)) {
                    return Base64.getDecoder().decode((String) resource.get("blob"));
                }
            }
        }
        return item;
    }

    /**
     * Decode bytes as strict UTF-8 text; fall back to raw bytes when not valid UTF-8.
     * Mirrors the Python SDK: {@code raw.decode("utf-8")} with a raw-bytes fallback.
     */
    private static Object decodeUtf8OrRaw(byte[] raw) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString();
        } catch (CharacterCodingException e) {
            return raw;
        }
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
