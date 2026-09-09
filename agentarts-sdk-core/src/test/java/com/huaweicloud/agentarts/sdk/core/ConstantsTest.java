package com.huaweicloud.agentarts.sdk.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link Constants}.
 *
 * <p>Note: Tests that involve environment variables test the logic
 * of the methods. Since System.getenv() cannot be easily mocked without
 * additional libraries, these tests validate behavior with unset env vars
 * (relying on defaults) and the ensureHttps helper directly.</p>
 */
class ConstantsTest {

    // ============================================================
    // Environment variable names match Python
    // ============================================================

    @Nested
    class EnvVarNames {

        @Test
        void akEnvVarName() {
            assertEquals("HUAWEICLOUD_SDK_AK", Constants.ENV_HUAWEICLOUD_SDK_AK);
        }

        @Test
        void skEnvVarName() {
            assertEquals("HUAWEICLOUD_SDK_SK", Constants.ENV_HUAWEICLOUD_SDK_SK);
        }

        @Test
        void securityTokenEnvVarName() {
            assertEquals("HUAWEICLOUD_SDK_SECURITY_TOKEN", Constants.ENV_HUAWEICLOUD_SDK_SECURITY_TOKEN);
        }

        @Test
        void regionEnvVarNames() {
            assertEquals("HUAWEICLOUD_SDK_REGION", Constants.ENV_HUAWEICLOUD_SDK_REGION);
            assertEquals("HUAWEICLOUD_REGION", Constants.ENV_HUAWEICLOUD_REGION);
            assertEquals("OS_REGION_NAME", Constants.ENV_OS_REGION_NAME);
        }

        @Test
        void idpEnvVarNames() {
            assertEquals("HUAWEICLOUD_SDK_IDP_ID", Constants.ENV_HUAWEICLOUD_SDK_IDP_ID);
            assertEquals("HUAWEICLOUD_SDK_ID_TOKEN_FILE", Constants.ENV_HUAWEICLOUD_SDK_ID_TOKEN_FILE);
            assertEquals("HUAWEICLOUD_SDK_PROJECT_ID", Constants.ENV_HUAWEICLOUD_SDK_PROJECT_ID);
        }

        @Test
        void endpointEnvVarNames() {
            assertEquals("AGENTARTS_CONTROL_ENDPOINT", Constants.ENV_AGENTARTS_CONTROL_ENDPOINT);
            assertEquals("AGENTARTS_RUNTIME_DATA_ENDPOINT", Constants.ENV_AGENTARTS_RUNTIME_DATA_ENDPOINT);
            assertEquals("AGENTARTS_MEMORY_DATA_ENDPOINT", Constants.ENV_AGENTARTS_MEMORY_DATA_ENDPOINT);
            assertEquals("AGENTARTS_CODEINTERPRETER_DATA_ENDPOINT", Constants.ENV_AGENTARTS_CODEINTERPRETER_DATA_ENDPOINT);
        }

        @Test
        void serviceEndpointEnvVarNames() {
            assertEquals("HUAWEICLOUD_SDK_IAM_ENDPOINT", Constants.ENV_HUAWEICLOUD_SDK_IAM_ENDPOINT);
            assertEquals("HUAWEICLOUD_SDK_SWR_ENDPOINT", Constants.ENV_HUAWEICLOUD_SDK_SWR_ENDPOINT);
            assertEquals("HUAWEICLOUD_SDK_SWR_LOGIN_SERVER", Constants.ENV_HUAWEICLOUD_SDK_SWR_LOGIN_SERVER);
            assertEquals("HUAWEICLOUD_SDK_AGENTIDENTITY_ENDPOINT", Constants.ENV_HUAWEICLOUD_SDK_AGENTIDENTITY_ENDPOINT);
        }
    }

    // ============================================================
    // HTTP Header constants
    // ============================================================

    @Nested
    class HeaderConstants {

        @Test
        void sessionHeader() {
            assertEquals("x-hw-agentarts-session-id", Constants.SESSION_HEADER);
        }

        @Test
        void codeInterpreterSessionHeader() {
            assertEquals("x-HW-Agentarts-Code-Interpreter-Session-Id", Constants.CODE_INTERPRETER_SESSION_HEADER);
        }

        @Test
        void accessTokenHeader() {
            assertEquals("X-HW-AgentGateway-Workload-Access-Token", Constants.ACCESS_TOKEN_HEADER);
        }

        @Test
        void userIdHeader() {
            assertEquals("X-HW-AgentGateway-User-Id", Constants.USER_ID_HEADER);
        }

        @Test
        void customHeaderPrefix() {
            assertEquals("X-Hw-AgentArts-Runtime-Custom-", Constants.CUSTOM_HEADER_PREFIX);
        }

        @Test
        void requestIdHeader() {
            assertEquals("X-Request-Id", Constants.REQUEST_ID_HEADER);
        }
    }

    // ============================================================
    // Default values
    // ============================================================

    @Nested
    class Defaults {

        @Test
        void defaultRegion() {
            assertEquals("cn-southwest-2", Constants.DEFAULT_REGION);
        }

        @Test
        void defaultPort() {
            assertEquals(8080, Constants.DEFAULT_PORT);
        }

        @Test
        void defaultMaxConcurrency() {
            assertEquals(15, Constants.DEFAULT_MAX_CONCURRENCY);
        }

        @Test
        void defaultTimeout() {
            assertEquals(30.0, Constants.DEFAULT_TIMEOUT_SECONDS);
        }
    }

    // ============================================================
    // ensureHttps helper
    // ============================================================

    @Nested
    class EnsureHttps {

        @Test
        void addsHttpsWhenMissing() {
            assertEquals("https://example.com", Constants.ensureHttps("example.com"));
        }

        @Test
        void preservesHttps() {
            assertEquals("https://example.com", Constants.ensureHttps("https://example.com"));
        }

        @Test
        void preservesHttp() {
            assertEquals("http://example.com", Constants.ensureHttps("http://example.com"));
        }

        @Test
        void emptyStringReturnsEmpty() {
            // Python: if not endpoint: return endpoint -> "" returns ""
            assertEquals("", Constants.ensureHttps(""));
        }

        @Test
        void nullReturnsEmpty() {
            // Python: if not endpoint (None is falsy) -> returns None
            // Java: null -> isBlank -> return ""
            assertEquals("", Constants.ensureHttps(null));
        }

        @Test
        void preservesPath() {
            assertEquals("https://example.com/path", Constants.ensureHttps("example.com/path"));
        }

        @Test
        void preservesPort() {
            assertEquals("https://example.com:8080", Constants.ensureHttps("example.com:8080"));
        }

        @Test
        void doesNotDoubleAddPrefix() {
            String result = Constants.ensureHttps("https://already-has-prefix.com");
            assertEquals("https://already-has-prefix.com", result);
            assertFalse(result.startsWith("https://https://"));
        }
    }

    // ============================================================
    // Region accessor (uses defaults when env vars not set)
    // ============================================================

    @Nested
    class GetRegion {

        @Test
        void returnsDefaultWhenNoEnvSet() {
            // Without any env vars set, should return default
            // Note: this test may fail if env vars ARE set in the test environment
            String region = Constants.getRegion();
            assertNotNull(region);
            assertFalse(region.isEmpty());
        }
    }

    // ============================================================
    // Endpoint constructors
    // ============================================================

    @Nested
    class EndpointConstructors {

        @Test
        void controlPlaneEndpointFormat() {
            String endpoint = Constants.getControlPlaneEndpoint("cn-southwest-2");
            assertEquals("https://agentarts.cn-southwest-2.myhuaweicloud.com", endpoint);
        }

        @Test
        void controlPlaneEndpointDefaultRegion() {
            // Should use default region when null
            String endpoint = Constants.getControlPlaneEndpoint(null);
            assertTrue(endpoint.startsWith("https://agentarts."));
            assertTrue(endpoint.endsWith(".myhuaweicloud.com"));
        }

        @Test
        void controlPlaneEndpointNoArg() {
            String endpoint = Constants.getControlPlaneEndpoint();
            assertTrue(endpoint.startsWith("https://agentarts."));
        }

        @Test
        void iamEndpointFormat() {
            String endpoint = Constants.getIamEndpoint("cn-north-4");
            assertEquals("https://iam.cn-north-4.myhuaweicloud.com", endpoint);
        }

        @Test
        void iamEndpointNoArg() {
            String endpoint = Constants.getIamEndpoint();
            assertTrue(endpoint.startsWith("https://iam."));
        }

        @Test
        void swrEndpointFormat() {
            String endpoint = Constants.getSwrEndpoint("cn-southwest-2");
            assertEquals("https://swr-api.cn-southwest-2.myhuaweicloud.com", endpoint);
        }

        @Test
        void swrEndpointNoArg() {
            String endpoint = Constants.getSwrEndpoint();
            assertTrue(endpoint.startsWith("https://swr-api."));
        }

        @Test
        void swrLoginServerDefaultFormat() {
            // Env var unset: falls back to the default swr.{region}.myhuaweicloud.com format.
            String loginServer = Constants.getSwrLoginServer("cn-southwest-2");
            assertEquals("swr.cn-southwest-2.myhuaweicloud.com", loginServer);
        }

        @Test
        void swrLoginServerRespectsEnvOverride() {
            // The override path reads the env at runtime (not settable in the test JVM),
            // so exercise the resolution logic directly through the package-visible seam.
            assertEquals("registry.example.com",
                    Constants.resolveSwrLoginServer("registry.example.com", "cn-southwest-2"));
            assertEquals("swr.cn-north-4.myhuaweicloud.com",
                    Constants.resolveSwrLoginServer(null, "cn-north-4"));
            assertEquals("swr.cn-north-4.myhuaweicloud.com",
                    Constants.resolveSwrLoginServer("", "cn-north-4"));
        }

        @Test
        void identityEndpointFormat() {
            String endpoint = Constants.getIdentityEndpoint("cn-southwest-2");
            assertEquals("https://agent-identity.cn-southwest-2.myhuaweicloud.com", endpoint);
        }

        @Test
        void identityEndpointNoArg() {
            String endpoint = Constants.getIdentityEndpoint();
            assertTrue(endpoint.startsWith("https://agent-identity."));
        }

        @Test
        void memoryDataEndpointFormat() {
            String endpoint = Constants.getMemoryEndpoint("data", "cn-southwest-2");
            assertEquals("https://memory.cn-southwest-2.huaweicloud-agentarts.com", endpoint);
        }

        @Test
        void memoryControlEndpointDelegatesToControlPlane() {
            String controlEndpoint = Constants.getControlPlaneEndpoint("cn-southwest-2");
            String memoryControlEndpoint = Constants.getMemoryEndpoint("control", "cn-southwest-2");
            assertEquals(controlEndpoint, memoryControlEndpoint);
        }

        @Test
        void memoryEndpointInvalidTypeThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> Constants.getMemoryEndpoint("invalid", "cn-southwest-2"));
        }

        @Test
        void memoryEndpointNoArg() {
            String endpoint = Constants.getMemoryEndpoint("data");
            assertTrue(endpoint.startsWith("https://memory."));
        }

        @Test
        void runtimeDataPlaneEndpointReturnsValidUrl() {
            // These endpoints read env vars (AGENTARTS_RUNTIME_DATA_ENDPOINT, …).
            // When unset, the method routes "" through ensureHttps and returns "".
            // Either way the result must be empty (env unset) or an HTTPS URL —
            // never a bare host or null (ensureHttps invariant).
            String endpoint = Constants.getRuntimeDataPlaneEndpoint();
            assertTrue(endpoint.isEmpty() || endpoint.startsWith("https://"),
                    "endpoint must be empty (env unset) or an HTTPS URL");
        }

        @Test
        void codeInterpreterDataPlaneEndpointWithExplicitEndpoint() {
            String endpoint = Constants.getCodeInterpreterDataPlaneEndpoint("https://custom.endpoint.com");
            assertEquals("https://custom.endpoint.com", endpoint);
        }

        @Test
        void codeInterpreterDataPlaneEndpointNoArg() {
            String endpoint = Constants.getCodeInterpreterDataPlaneEndpoint();
            assertTrue(endpoint.isEmpty() || endpoint.startsWith("https://"),
                    "endpoint must be empty (env unset) or an HTTPS URL");
        }

        @Test
        void codeInterpreterDataPlaneEndpointWithNull() {
            String endpoint = Constants.getCodeInterpreterDataPlaneEndpoint(null);
            assertTrue(endpoint.isEmpty() || endpoint.startsWith("https://"),
                    "endpoint must be empty (env unset) or an HTTPS URL");
        }

        @Test
        void allEndpointsUseHttps() {
            String region = "cn-southwest-2";
            assertTrue(Constants.getControlPlaneEndpoint(region).startsWith("https://"));
            assertTrue(Constants.getIamEndpoint(region).startsWith("https://"));
            assertTrue(Constants.getSwrEndpoint(region).startsWith("https://"));
            assertTrue(Constants.getIdentityEndpoint(region).startsWith("https://"));
            assertTrue(Constants.getMemoryEndpoint("data", region).startsWith("https://"));
        }
    }

    // ============================================================
    // Utility class cannot be instantiated
    // ============================================================

    @Nested
    class ConstructionPrevention {
        @Test
        void constructorIsPrivate() throws Exception {
            var constructor = Constants.class.getDeclaredConstructor();
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        }
    }
}
