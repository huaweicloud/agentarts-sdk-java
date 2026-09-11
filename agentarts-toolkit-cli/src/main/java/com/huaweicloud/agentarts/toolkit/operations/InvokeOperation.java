package com.huaweicloud.agentarts.toolkit.operations;

import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import com.huaweicloud.agentarts.sdk.service.runtime.LocalRuntimeClient;
import com.huaweicloud.agentarts.sdk.service.runtime.RuntimeClient;
import com.huaweicloud.agentarts.toolkit.commands.CliSupport;

import java.util.UUID;

/**
 * Invoke operation: send a JSON payload to a local or cloud agent.
 *
 * <p>Operation: invoke an agent with a JSON payload (local or cloud).
 * Cloud mode resolves the agent's data-plane endpoint and auth mode from
 * {@code .agentarts_config.yaml} + a control-plane lookup, then calls
 * {@link RuntimeClient#invokeAgent}.</p>
 */
public class InvokeOperation {

    /**
     * Invoke an agent with a JSON payload (local or cloud).
     *
     * @param payload      JSON payload string
     * @param agentName    agent name (for cloud mode, uses config default when null)
     * @param mode         invocation mode: "local" or "cloud"
     * @param region       Huawei Cloud region (for cloud mode, nullable)
     * @param port         local server port (for local mode, default 8080)
     * @param endpoint     custom endpoint name (nullable)
     * @param sessionId    session ID for context continuity (nullable; auto-generated in cloud)
     * @param bearerToken  bearer token for cloud auth (nullable for IAM agents)
     * @param timeout      request timeout in seconds
     * @param skipSsl      skip SSL verification
     * @param userId       user ID header (nullable)
     * @param customPath   custom invocation path (nullable)
     */
    public static void invokeAgent(String payload, String agentName, String mode,
                                    String region, Integer port, String endpoint,
                                    String sessionId, String bearerToken, int timeout,
                                    boolean skipSsl, String userId, String customPath) throws Exception {
        // Validate JSON payload (best-effort normalization for shell-quote stripping).
        String normalized = normalizePayload(payload);
        try {
            JsonUtils.MAPPER.readTree(normalized);
        } catch (Exception e) {
            CliSupport.failCli("Payload must be valid JSON: " + e.getMessage());
            return;
        }
        if (timeout <= 0) {
            CliSupport.failCli("timeout must be greater than zero");
        }
        String normalizedCustomPath = normalizeCustomPath(customPath);

        if ("local".equals(mode)) {
            int p = port != null ? port : 8080;
            if (p < 1 || p > 65535) {
                CliSupport.failCli("port must be between 1 and 65535");
            }
            invokeLocal(normalized, p, sessionId, bearerToken, endpoint,
                    timeout, userId, normalizedCustomPath);
            return;
        }
        if (!"cloud".equals(mode)) {
            CliSupport.failCli("mode must be 'local' or 'cloud'");
            return;
        }
        invokeCloud(normalized, agentName, region, endpoint, sessionId, bearerToken,
                timeout, skipSsl, userId, normalizedCustomPath);
    }

    private static void invokeLocal(String payload, int port, String sessionId,
                                    String bearerToken, String endpoint, int timeout,
                                    String userId, String customPath) {
        try (LocalRuntimeClient client = new LocalRuntimeClient(port, "localhost", timeout);
             RequestResult result = client.invokeAgentRaw(
                     payload, sessionId, bearerToken, endpoint, userId, customPath)) {
            printInvocationResponse(result);
        }
    }

    private static void invokeCloud(String payload, String agentName, String region,
                                     String endpoint, String sessionId, String bearerToken,
                                     int timeout, boolean skipSsl, String userId,
                                     String customPath) throws Exception {
        String resolvedAgentName = RuntimeResolver.resolveAgentName(agentName);
        if (!JsonUtils.isNotBlank(resolvedAgentName)) {
            CliSupport.failCli("No agent specified and no default agent configured");
        }
        String actualSessionId = (sessionId != null && !sessionId.isBlank())
                ? sessionId : UUID.randomUUID().toString();
        try (RuntimeClient client = RuntimeResolver.resolve(
                resolvedAgentName, region, !skipSsl, bearerToken);
             RequestResult result = client.invokeAgentRaw(
                     resolvedAgentName, actualSessionId, payload, bearerToken,
                     endpoint, timeout, userId, customPath)) {
            printInvocationResponse(result);
        } catch (Exception e) {
            CliSupport.failCli("Failed to invoke cloud agent: " + e.getMessage());
        }
    }

    private static void printInvocationResponse(RequestResult result) {
        if (result.isStreaming()) {
            result.iterLines().doOnNext(System.out::println).blockLast();
        } else if (result.getDataAsJson() != null) {
            CliSupport.printJson(result.getDataAsJson());
        } else if (result.getDataAsString() != null) {
            System.out.println(result.getDataAsString());
        } else {
            CliSupport.failCli("Invocation returned an unsupported binary response");
        }
    }

    private static String normalizeCustomPath(String customPath) {
        if (customPath == null || customPath.isBlank()) return null;
        String normalized = customPath.trim().replaceAll("^/+|/+$", "");
        if (normalized.isEmpty()) return null;
        if (!normalized.matches("[a-zA-Z0-9_./-]+") || normalized.contains("..")) {
            CliSupport.failCli("custom-path contains invalid or unsafe path characters");
        }
        return normalized;
    }

    /**
     * Normalize a JSON payload that may have had its quotes stripped by a shell
     * (e.g. PowerShell). Returns the payload unchanged when it already parses.
     */
    private static String normalizePayload(String payload) {
        if (payload == null) return "{}";
        payload = payload.trim();
        if (payload.isEmpty()) return "{}";
        try {
            JsonUtils.MAPPER.readTree(payload);
            return payload;
        } catch (Exception ignored) {
            // fall through to best-effort fixups
        }
        if (payload.startsWith("'") && payload.endsWith("'")) {
            payload = payload.substring(1, payload.length() - 1);
        }
        if (payload.contains("\\\"")) {
            payload = payload.replace("\\\"", "\"");
        }
        return payload;
    }
}
