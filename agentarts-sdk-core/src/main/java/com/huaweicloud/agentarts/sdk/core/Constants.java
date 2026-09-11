package com.huaweicloud.agentarts.sdk.core;

import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;

/**
 * Centralized constants and environment variable accessors for AgentArts SDK.
 *
 * <p>Provides centralized constants and environment variable accessors.
 * All values are loaded from environment variables.</p>
 *
 * <p>Environment variable priority for region:
 * <ol>
 *   <li>{@code HUAWEICLOUD_SDK_REGION}</li>
 *   <li>{@code HUAWEICLOUD_REGION}</li>
 *   <li>{@code OS_REGION_NAME} (OpenStack compatibility)</li>
 *   <li>Default: {@code cn-southwest-2}</li>
 * </ol>
 */
public final class Constants {

    private Constants() {
        // utility class
    }

    // ========================
    // Environment variable names
    // ========================

    public static final String ENV_HUAWEICLOUD_SDK_AK = "HUAWEICLOUD_SDK_AK";
    public static final String ENV_HUAWEICLOUD_SDK_SK = "HUAWEICLOUD_SDK_SK";
    public static final String ENV_HUAWEICLOUD_SDK_SECURITY_TOKEN = "HUAWEICLOUD_SDK_SECURITY_TOKEN";
    public static final String ENV_HUAWEICLOUD_SDK_REGION = "HUAWEICLOUD_SDK_REGION";
    public static final String ENV_HUAWEICLOUD_REGION = "HUAWEICLOUD_REGION";
    public static final String ENV_OS_REGION_NAME = "OS_REGION_NAME";

    public static final String ENV_HUAWEICLOUD_SDK_IDP_ID = "HUAWEICLOUD_SDK_IDP_ID";
    public static final String ENV_HUAWEICLOUD_SDK_ID_TOKEN_FILE = "HUAWEICLOUD_SDK_ID_TOKEN_FILE";
    public static final String ENV_HUAWEICLOUD_SDK_PROJECT_ID = "HUAWEICLOUD_SDK_PROJECT_ID";

    public static final String ENV_AGENTARTS_CONTROL_ENDPOINT = "AGENTARTS_CONTROL_ENDPOINT";
    public static final String ENV_AGENTARTS_RUNTIME_DATA_ENDPOINT = "AGENTARTS_RUNTIME_DATA_ENDPOINT";
    public static final String ENV_AGENTARTS_MEMORY_DATA_ENDPOINT = "AGENTARTS_MEMORY_DATA_ENDPOINT";
    public static final String ENV_AGENTARTS_CODEINTERPRETER_DATA_ENDPOINT = "AGENTARTS_CODEINTERPRETER_DATA_ENDPOINT";

    public static final String ENV_HUAWEICLOUD_SDK_IAM_ENDPOINT = "HUAWEICLOUD_SDK_IAM_ENDPOINT";
    public static final String ENV_HUAWEICLOUD_SDK_SWR_ENDPOINT = "HUAWEICLOUD_SDK_SWR_ENDPOINT";
    public static final String ENV_HUAWEICLOUD_SDK_SWR_LOGIN_SERVER = "HUAWEICLOUD_SDK_SWR_LOGIN_SERVER";
    public static final String ENV_HUAWEICLOUD_SDK_AGENTIDENTITY_ENDPOINT = "HUAWEICLOUD_SDK_AGENTIDENTITY_ENDPOINT";

    public static final String ENV_AGENTARTS_LOG_LEVEL = "AGENTARTS_LOG_LEVEL";
    public static final String ENV_AGENTARTS_BIND_IP = "AGENTARTS_BIND_IP";

    // ========================
    // HTTP Header constants for AgentArts runtime
    // ========================

    public static final String SESSION_HEADER = "x-hw-agentarts-session-id";
    public static final String CODE_INTERPRETER_SESSION_HEADER = "x-HW-Agentarts-Code-Interpreter-Session-Id";
    public static final String ACCESS_TOKEN_HEADER = "X-HW-AgentGateway-Workload-Access-Token";
    public static final String USER_ID_HEADER = "X-HW-AgentGateway-User-Id";
    public static final String CUSTOM_HEADER_PREFIX = "X-Hw-AgentArts-Runtime-Custom-";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    // ========================
    // Default values
    // ========================

    public static final String DEFAULT_REGION = "cn-southwest-2";
    public static final int DEFAULT_PORT = 8080;
    public static final int DEFAULT_MAX_CONCURRENCY = 15;
    public static final double DEFAULT_TIMEOUT_SECONDS = 30.0;

    // ========================
    // Region accessor
    // ========================

    /**
     * Get the current Huawei Cloud region.
     *
     * <p>Checks environment variables in order:
     * HUAWEICLOUD_SDK_REGION, HUAWEICLOUD_REGION, OS_REGION_NAME.
     * Falls back to {@value #DEFAULT_REGION}.</p>
     */
    public static String getRegion() {
        String region = getEnv(ENV_HUAWEICLOUD_SDK_REGION);
        if (JsonUtils.isNotBlank(region)) {
            return region;
        }
        region = getEnv(ENV_HUAWEICLOUD_REGION);
        if (JsonUtils.isNotBlank(region)) {
            return region;
        }
        region = getEnv(ENV_OS_REGION_NAME);
        if (JsonUtils.isNotBlank(region)) {
            return region;
        }
        return DEFAULT_REGION;
    }

    // ========================
    // Credential accessors
    // ========================

    /** Get Huawei Cloud Access Key ID from environment. */
    public static String getAk() {
        return getEnvOrDefault(ENV_HUAWEICLOUD_SDK_AK, "");
    }

    /** Get Huawei Cloud Secret Access Key from environment. */
    public static String getSk() {
        return getEnvOrDefault(ENV_HUAWEICLOUD_SDK_SK, "");
    }

    /** Get Huawei Cloud Security Token from environment (for STS). */
    public static String getSecurityToken() {
        return getEnvOrDefault(ENV_HUAWEICLOUD_SDK_SECURITY_TOKEN, "");
    }

    /** Get Huawei Cloud Project ID from environment. */
    public static String getProjectId() {
        return getEnvOrDefault(ENV_HUAWEICLOUD_SDK_PROJECT_ID, "");
    }

    // ========================
    // Endpoint constructors
    // ========================

    /**
     * Get the AgentArts control plane endpoint URL.
     *
     * <p>Example: {@code https://agentarts.cn-southwest-2.myhuaweicloud.com}</p>
     */
    public static String getControlPlaneEndpoint(String region) {
        String endpoint = getEnv(ENV_AGENTARTS_CONTROL_ENDPOINT);
        if (JsonUtils.isNotBlank(endpoint)) {
            return ensureHttps(endpoint);
        }
        String r = JsonUtils.isNotBlank(region) ? region : getRegion();
        return "https://agentarts." + r + ".myhuaweicloud.com";
    }

    public static String getControlPlaneEndpoint() {
        return getControlPlaneEndpoint(null);
    }

    /**
     * Get the AgentArts runtime data plane endpoint URL.
     */
    public static String getRuntimeDataPlaneEndpoint() {
        String endpoint = getEnv(ENV_AGENTARTS_RUNTIME_DATA_ENDPOINT);
        return ensureHttps(endpoint != null ? endpoint : "");
    }

    /**
     * Get the AgentArts code interpreter data plane endpoint URL.
     */
    public static String getCodeInterpreterDataPlaneEndpoint(String endpoint) {
        String codeEndpoint = getEnv(ENV_AGENTARTS_CODEINTERPRETER_DATA_ENDPOINT);
        if (JsonUtils.isNotBlank(codeEndpoint)) {
            return ensureHttps(codeEndpoint);
        }
        if (JsonUtils.isNotBlank(endpoint)) {
            return ensureHttps(endpoint);
        }
        String runtimeEndpoint = getEnvOrDefault(ENV_AGENTARTS_RUNTIME_DATA_ENDPOINT, "");
        return ensureHttps(runtimeEndpoint);
    }

    public static String getCodeInterpreterDataPlaneEndpoint() {
        return getCodeInterpreterDataPlaneEndpoint(null);
    }

    /**
     * Get the AgentArts memory service endpoint URL.
     *
     * @param endpointType "control" or "data"
     * @param region       Huawei Cloud region (nullable, auto-detected if null)
     */
    public static String getMemoryEndpoint(String endpointType, String region) {
        if ("control".equals(endpointType)) {
            return getControlPlaneEndpoint(region);
        }
        if ("data".equals(endpointType)) {
            String memoryEndpoint = getEnv(ENV_AGENTARTS_MEMORY_DATA_ENDPOINT);
            if (JsonUtils.isNotBlank(memoryEndpoint)) {
                return ensureHttps(memoryEndpoint);
            }
            String r = JsonUtils.isNotBlank(region) ? region : getRegion();
            return "https://memory." + r + ".huaweicloud-agentarts.com";
        }
        throw new IllegalArgumentException("Invalid endpoint type: " + endpointType);
    }

    public static String getMemoryEndpoint(String endpointType) {
        return getMemoryEndpoint(endpointType, null);
    }

    /**
     * Get the Huawei Cloud IAM endpoint URL.
     *
     * <p>Example: {@code https://iam.cn-southwest-2.myhuaweicloud.com}</p>
     */
    public static String getIamEndpoint(String region) {
        String endpoint = getEnv(ENV_HUAWEICLOUD_SDK_IAM_ENDPOINT);
        if (JsonUtils.isNotBlank(endpoint)) {
            return ensureHttps(endpoint);
        }
        String r = JsonUtils.isNotBlank(region) ? region : getRegion();
        return "https://iam." + r + ".myhuaweicloud.com";
    }

    public static String getIamEndpoint() {
        return getIamEndpoint(null);
    }

    /**
     * Get the Huawei Cloud SWR endpoint URL.
     *
     * <p>Example: {@code https://swr-api.cn-southwest-2.myhuaweicloud.com}</p>
     */
    public static String getSwrEndpoint(String region) {
        String endpoint = getEnv(ENV_HUAWEICLOUD_SDK_SWR_ENDPOINT);
        if (JsonUtils.isNotBlank(endpoint)) {
            return ensureHttps(endpoint);
        }
        String r = JsonUtils.isNotBlank(region) ? region : getRegion();
        return "https://swr-api." + r + ".myhuaweicloud.com";
    }

    public static String getSwrEndpoint() {
        return getSwrEndpoint(null);
    }

    /**
     * Get the SWR registry login server (Docker login server) for the given region.
     *
     * <p>Checks the environment variable
     * {@value #ENV_HUAWEICLOUD_SDK_SWR_LOGIN_SERVER} first — some regions use a
     * different Docker registry host than the default format. Falls back to
     * {@code swr.{region}.myhuaweicloud.com} when the variable is unset.</p>
     */
    public static String getSwrLoginServer(String region) {
        return resolveSwrLoginServer(getEnv(ENV_HUAWEICLOUD_SDK_SWR_LOGIN_SERVER), region);
    }

    public static String getSwrLoginServer() {
        return getSwrLoginServer(null);
    }

    /**
     * Resolve the SWR login server from an optional env override, falling back
     * to the default {@code swr.{region}.myhuaweicloud.com} format. Package-visible
     * so tests can exercise the override path without mutating the process env.
     */
    static String resolveSwrLoginServer(String envOverride, String region) {
        if (JsonUtils.isNotBlank(envOverride)) {
            return envOverride;
        }
        String r = JsonUtils.isNotBlank(region) ? region : getRegion();
        return "swr." + r + ".myhuaweicloud.com";
    }

    /**
     * Get the Huawei Cloud Agent Identity endpoint URL.
     *
     * <p>Example: {@code https://agent-identity.cn-southwest-2.myhuaweicloud.com}</p>
     */
    public static String getIdentityEndpoint(String region) {
        String endpoint = getEnv(ENV_HUAWEICLOUD_SDK_AGENTIDENTITY_ENDPOINT);
        if (JsonUtils.isNotBlank(endpoint)) {
            return ensureHttps(endpoint);
        }
        String r = JsonUtils.isNotBlank(region) ? region : getRegion();
        return "https://agent-identity." + r + ".myhuaweicloud.com";
    }

    public static String getIdentityEndpoint() {
        return getIdentityEndpoint(null);
    }

    // ========================
    // Internal helpers
    // ========================

    /**
     * Ensure endpoint has https:// prefix.
     *
     * <p>Public because it is called from other packages (e.g. the runtime /
     * code-interpreter / memory service clients) to normalize user-provided
     * endpoint overrides before signing.</p>
     */
    public static String ensureHttps(String endpoint) {
        if (JsonUtils.isBlank(endpoint)) {
            return "";
        }
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            return "https://" + endpoint;
        }
        return endpoint;
    }

    private static String getEnv(String name) {
        String value = System.getenv(name);
        if (JsonUtils.isNotBlank(value)) {
            return value;
        }
        // System-property fallback so tests and embedded launchers can inject
        // endpoints without mutating the process environment.
        return System.getProperty(name);
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = getEnv(name);
        return JsonUtils.isNotBlank(value) ? value : defaultValue;
    }
}
