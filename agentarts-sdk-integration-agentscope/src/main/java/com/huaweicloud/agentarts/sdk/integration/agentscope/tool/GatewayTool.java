package com.huaweicloud.agentarts.sdk.integration.agentscope.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import com.huaweicloud.agentarts.sdk.gateway.GatewayClient;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeRequestContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpContentConverter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * AgentScope tool that invokes a tool exposed by an AgentArts Gateway.
 *
 * <p>The gateway management client is used only to resolve the gateway's
 * {@code endpoint_url}. Tool execution then uses AgentScope's standard MCP
 * Streamable HTTP client against {@code endpoint_url/mcp}; no synthetic success
 * response is produced.</p>
 *
 * <p>Authentication is resolved per invocation from the AgentScope runtime
 * context. A preformatted Authorization value can be stored under
 * {@code gatewayAuthorization}; otherwise {@code workloadAccessToken} is sent
 * as a Bearer token. Gateways configured without inbound authentication do not
 * require either value.</p>
 */
public final class GatewayTool implements AgentTool {

    private static final String TOOL_NAME = "gateway_call";
    private static final String TOOL_DESCRIPTION =
            "Call a tool through a Gateway. Provide the gateway_id, "
                    + "the discovered tool_name, and its arguments.";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Duration INVOCATION_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_TOOL_NAME_LENGTH = 256;
    private static final int MAX_ARGUMENT_JSON_LENGTH = 1_000_000;
    private static final int MAX_ENDPOINT_LENGTH = 2048;
    private static final int MAX_HEADER_VALUE_LENGTH = 4096;

    private final GatewayClient gatewayClient;
    private final GatewayMcpInvoker invoker;

    public GatewayTool(GatewayClient gatewayClient) {
        this(gatewayClient, GatewayTool::invokeMcp);
    }

    /**
     * Create a gateway tool with a custom invocation transport.
     *
     * <p>This overload is useful when applications need custom observability or
     * transport policies around the standard MCP call.</p>
     */
    public GatewayTool(GatewayClient gatewayClient, GatewayMcpInvoker invoker) {
        this.gatewayClient = Objects.requireNonNull(gatewayClient, "gatewayClient");
        this.invoker = Objects.requireNonNull(invoker, "invoker");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        properties.put("gateway_id", Map.of(
                "type", "string", "description", "Gateway UUID"));
        properties.put("tool_name", Map.of(
                "type", "string", "description", "Tool name returned by MCP tools/list"));
        properties.put("arguments", Map.of(
                "type", "object", "description", "Arguments defined by the discovered tool schema"));
        schema.put("properties", properties);
        schema.put("required", List.of("gateway_id", "tool_name"));
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        return null;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        Validation validation = validate(param);
        if (validation.error != null) {
            return Mono.just(error(validation.error));
        }

        return Mono.fromCallable(() -> resolveInvocationEndpoint(validation.gatewayId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(endpoint -> invoker.invoke(
                        endpoint,
                        resolveAuthorization(param),
                        resolveSessionId(param),
                        validation.toolName,
                        validation.arguments))
                .map(GatewayTool::ensureTerminalState)
                .onErrorResume(ignored -> Mono.just(error("gateway call failed")));
    }

    private Validation validate(ToolCallParam param) {
        if (param == null || param.getInput() == null) {
            return Validation.error("Tool input is required");
        }
        Map<String, Object> input = param.getInput();
        Object gatewayId = input.get("gateway_id");
        Object toolName = input.get("tool_name");
        Object arguments = input.getOrDefault("arguments", Map.of());
        if (!(gatewayId instanceof String) || !UUID_PATTERN.matcher((String) gatewayId).matches()) {
            return Validation.error("gateway_id must be a lowercase UUID");
        }
        if (!(toolName instanceof String) || ((String) toolName).isBlank()) {
            return Validation.error("tool_name must be a non-blank string");
        }
        if (((String) toolName).length() > MAX_TOOL_NAME_LENGTH) {
            return Validation.error("tool_name exceeds 256 characters");
        }
        if (!(arguments instanceof Map<?, ?>)) {
            return Validation.error("arguments must be an object");
        }
        Map<?, ?> argumentMap = (Map<?, ?>) arguments;
        if (argumentMap.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            return Validation.error("argument names must be strings");
        }
        try {
            if (JsonUtils.toJson(argumentMap).length() > MAX_ARGUMENT_JSON_LENGTH) {
                return Validation.error("arguments exceed the maximum serialized size");
            }
        } catch (RuntimeException e) {
            return Validation.error("arguments must be JSON serializable");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typedArguments = (Map<String, Object>) arguments;
        return Validation.valid((String) gatewayId, (String) toolName,
                Collections.unmodifiableMap(new HashMap<>(typedArguments)));
    }

    private String resolveInvocationEndpoint(String gatewayId) {
        RequestResult gateway = gatewayClient.getGateway(gatewayId);
        if (gateway == null || !gateway.isSuccess()) {
            throw new IllegalStateException("Gateway lookup failed");
        }
        JsonNode data = gateway.getDataAsJson();
        String endpoint = data != null && data.hasNonNull("endpoint_url")
                ? data.get("endpoint_url").asText() : null;
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("Gateway response did not contain endpoint_url");
        }
        if (endpoint.length() > MAX_ENDPOINT_LENGTH) {
            throw new IllegalStateException("Gateway endpoint_url exceeds 2048 characters");
        }

        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Gateway endpoint_url is invalid", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("Gateway endpoint_url must be an HTTPS origin");
        }
        return endpoint.endsWith("/") ? endpoint + "mcp" : endpoint + "/mcp";
    }

    private static String resolveAuthorization(ToolCallParam param) {
        ToolExecutionContext context = param.getContext();
        if (context == null) {
            return null;
        }
        String authorization = context.get("gatewayAuthorization", String.class);
        if (authorization != null && !authorization.isBlank()) {
            if (authorization.length() > MAX_HEADER_VALUE_LENGTH) {
                throw new IllegalArgumentException("gatewayAuthorization exceeds 4096 characters");
            }
            return authorization.trim();
        }
        AgentscopeRequestContext requestContext = context.get(AgentscopeRequestContext.class);
        String workloadToken = requestContext != null
                ? requestContext.workloadAccessToken() : null;
        if (workloadToken == null || workloadToken.isBlank()) {
            return null;
        }
        if (workloadToken.length() > MAX_HEADER_VALUE_LENGTH - "Bearer ".length()) {
            throw new IllegalArgumentException("workloadAccessToken exceeds the Authorization limit");
        }
        return "Bearer " + workloadToken.trim();
    }

    private static String resolveSessionId(ToolCallParam param) {
        ToolExecutionContext context = param.getContext();
        AgentscopeRequestContext requestContext = context != null
                ? context.get(AgentscopeRequestContext.class) : null;
        if (requestContext == null || requestContext.sessionId() == null) {
            return null;
        }
        String sessionId = requestContext.sessionId();
        if (sessionId.length() > MAX_HEADER_VALUE_LENGTH) {
            throw new IllegalArgumentException("sessionId exceeds 4096 characters");
        }
        for (int i = 0; i < sessionId.length(); i++) {
            char ch = sessionId.charAt(i);
            if (ch < 0x21 || ch > 0x7e) {
                throw new IllegalArgumentException("sessionId must contain visible ASCII characters only");
            }
        }
        return sessionId;
    }

    private static Mono<ToolResultBlock> invokeMcp(
            String endpoint,
            String authorization,
            String sessionId,
            String toolName,
            Map<String, Object> arguments) {
        McpClientBuilder builder = McpClientBuilder.create("agentarts-gateway")
                .streamableHttpTransport(endpoint)
                .timeout(INVOCATION_TIMEOUT)
                .initializationTimeout(INITIALIZATION_TIMEOUT);
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header("Mcp-Session-Id", sessionId);
        }

        return builder.buildAsync()
                .flatMap(client -> invokeAndClose(client, toolName, arguments));
    }

    private static Mono<ToolResultBlock> invokeAndClose(
            McpClientWrapper client,
            String toolName,
            Map<String, Object> arguments) {
        return Mono.defer(() -> client.callTool(toolName, arguments)
                        .map(McpContentConverter::convertCallToolResult))
                .doFinally(ignored -> closeQuietly(client));
    }

    private static void closeQuietly(McpClientWrapper client) {
        try {
            client.close();
        } catch (RuntimeException ignored) {
            // The invocation result has already been determined; close is best-effort.
        }
    }

    private static ToolResultBlock ensureTerminalState(ToolResultBlock result) {
        if (result == null) {
            return error("gateway returned no tool result");
        }
        return result;
    }

    private static ToolResultBlock error(String message) {
        return ToolResultBlock.error(message);
    }

    /** Transport abstraction for MCP invocation and application-specific policies. */
    @FunctionalInterface
    public interface GatewayMcpInvoker {
        Mono<ToolResultBlock> invoke(
                String endpoint,
                String authorization,
                String sessionId,
                String toolName,
                Map<String, Object> arguments);
    }

    private static final class Validation {
        private final String gatewayId;
        private final String toolName;
        private final Map<String, Object> arguments;
        private final String error;

        private Validation(
                String gatewayId,
                String toolName,
                Map<String, Object> arguments,
                String error) {
            this.gatewayId = gatewayId;
            this.toolName = toolName;
            this.arguments = arguments;
            this.error = error;
        }

        private static Validation valid(
                String gatewayId, String toolName, Map<String, Object> arguments) {
            return new Validation(gatewayId, toolName, arguments, null);
        }

        private static Validation error(String message) {
            return new Validation(null, null, null, message);
        }
    }
}
