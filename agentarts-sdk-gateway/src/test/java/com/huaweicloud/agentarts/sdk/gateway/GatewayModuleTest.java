package com.huaweicloud.agentarts.sdk.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Gateway module: GatewayClient API parity.
 */
class GatewayModuleTest {

    @Nested
    @DisplayName("API verification: GatewayClient API methods")
    class PythonParityTests {

        @Test
        void gatewayCrudMethodsExist() throws Exception {
            // Gateway CRUD: create/update/delete/get/list_gateway
            Class<?> cls = GatewayClient.class;
            assertNotNull(cls.getMethod("createGateway", String.class, String.class, String.class, String.class, String.class));
            assertNotNull(cls.getMethod("createGateway", String.class, String.class));
            assertNotNull(cls.getMethod("updateGateway", String.class, String.class));
            assertNotNull(cls.getMethod("deleteGateway", String.class));
            assertNotNull(cls.getMethod("getGateway", String.class));
            assertNotNull(cls.getMethod("listGateways", String.class, Integer.class, Integer.class));
            assertNotNull(cls.getMethod("listGateways"));
        }

        @Test
        void targetCrudMethodsExist() throws Exception {
            // Gateway CRUD: create/update/delete/get/list_gateway_target
            Class<?> cls = GatewayClient.class;
            assertNotNull(cls.getMethod("createGatewayTarget", String.class, String.class, String.class, Map.class, Map.class));
            assertNotNull(cls.getMethod("createGatewayTarget", String.class, String.class, String.class));
            assertNotNull(cls.getMethod("updateGatewayTarget", String.class, String.class, String.class, String.class, Map.class, Map.class));
            assertNotNull(cls.getMethod("deleteGatewayTarget", String.class, String.class));
            assertNotNull(cls.getMethod("getGatewayTarget", String.class, String.class));
            assertNotNull(cls.getMethod("listGatewayTargets", String.class, Integer.class, Integer.class));
            assertNotNull(cls.getMethod("listGatewayTargets", String.class));
        }

        @Test
        void constructorMatchesPython() throws Exception {
            // Python: __init__(verify_ssl=True)
            Class<?> cls = GatewayClient.class;
            assertNotNull(cls.getConstructor(boolean.class));
            assertNotNull(cls.getConstructor());
        }

        @Test
        void implementsAutoCloseable() {
            assertTrue(AutoCloseable.class.isAssignableFrom(GatewayClient.class));
        }

        @Test
        void methodCountMatchesPython() {
            // Python GatewayClient has 10 public CRUD methods:
            // create/update/delete/get/list gateway + create/update/delete/get/list target
            Class<?> cls = GatewayClient.class;
            int gatewayMethods = 0;
            int targetMethods = 0;
            for (var m : cls.getDeclaredMethods()) {
                String name = m.getName();
                if (name.contains("Gateway") && !name.contains("Target")) gatewayMethods++;
                if (name.contains("GatewayTarget")) targetMethods++;
            }
            // At least 5 gateway + 5 target (may have overloads)
            assertTrue(gatewayMethods >= 5, "Expected ≥5 gateway methods, got " + gatewayMethods);
            assertTrue(targetMethods >= 5, "Expected ≥5 target methods, got " + targetMethods);
        }
    }
}
