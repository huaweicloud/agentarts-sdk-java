package com.huaweicloud.agentarts.sdk.tests.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.huaweicloud.agentarts.sdk.gateway.GatewayClient;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Gateway lifecycle tests — (6 tests: gateway + target CRUD).
 *
 * <p>{@code createGateway} auto-creates (or reuses) the IAM agency
 * {@code AgentArtsCoreGateway} with a {@code sts:agencies:assume} trust policy
 * and attaches the {@code AgentArtsCoreGatewayIdentityAgencyPolicy} system
 * policy, so gateway creation succeeds without a pre-existing agency.</p>
 *
 * <p>Requires {@code AGENTARTS_TEST_ALLOW_CREATE=1}. The shared IAM agency is
 * intentionally not deleted (it is reused across gateways).</p>
 */
@Tag("e2e")
@DisplayName("Gateway Lifecycle Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayLifecycleTest {

    private static GatewayClient client;
    private static E2EResourceRegistry registry;
    private static String runId;
    private static String gatewayId;
    private static String targetId;

    @BeforeAll
    static void setUp() {
        assumeTrue(E2EConfig.hasCloudCredentials(),
                "Set HUAWEICLOUD_SDK_AK and HUAWEICLOUD_SDK_SK to run cloud tests");
        assumeTrue(E2EConfig.allowCreate(),
                "Set AGENTARTS_TEST_ALLOW_CREATE=1 to run create/delete lifecycle tests");

        client = new GatewayClient();
        registry = new E2EResourceRegistry();
        runId = E2EConfig.getRunId();

        // Create gateway (auto-creates the IAM agency with policy on first run)
        String gwName = E2EHelpers.uniqueName("gw", runId);
        RequestResult result = client.createGateway(gwName, "e2e test gateway");
        assertTrue(result.isSuccess(),
                "create_gateway failed: " + result.getError());
        assertNotNull(result.getData(), "create_gateway returned no data");
        gatewayId = extractId((JsonNode) result.getData(), "id", "gateway_id");
        assertNotNull(gatewayId, "create_gateway returned no id");
        registry.register(() -> client.deleteGateway(gatewayId), "gateway:" + gatewayId);

        // Create target
        String targetName = E2EHelpers.uniqueName("tgt", runId);
        Map<String, Object> targetConfig = Map.of(
                "mcp_server", Map.of(
                        "endpoint", "https://example.com/mcp",
                        "server_type", "sse"));
        RequestResult tgtResult = client.createGatewayTarget(
                gatewayId, targetName, "e2e target", targetConfig, null);
        assertTrue(tgtResult.isSuccess(),
                "create_gateway_target failed: " + tgtResult.getError());
        targetId = extractId((JsonNode) tgtResult.getData(), "id", "target_id");
        assertNotNull(targetId, "create_gateway_target returned no id");
        registry.register(
                () -> client.deleteGatewayTarget(gatewayId, targetId),
                "target:" + targetId);
    }

    /** Pull a resource id out of a response node, trying common key names at
     *  the top level and one level into nested objects/arrays (the gateway
     *  target create wraps the id under {@code target.target_id}). */
    private static String extractId(JsonNode node, String... keys) {
        if (node == null) return null;
        String top = findText(node, keys);
        if (top != null) return top;
        for (JsonNode child : node) {
            if (child == null) continue;
            String s = findText(child, keys);
            if (s != null) return s;
            if (child.isArray()) {
                for (JsonNode item : child) {
                    if (item == null) continue;
                    String s2 = findText(item, keys);
                    if (s2 != null) return s2;
                }
            }
        }
        return null;
    }

    private static String findText(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull() && !v.asText("").isEmpty()) {
                return v.asText();
            }
        }
        return null;
    }

    @AfterAll
    static void tearDown() {
        if (registry != null) registry.cleanupAll();
        if (client != null) client.close();
    }

    // 1. test_get_gateway
    @Test @Order(1)
    @DisplayName("get_gateway returns the created gateway with a matching id")
    void testGetGateway() {
        RequestResult result = client.getGateway(gatewayId);
        assertTrue(result.isSuccess(), result.getError());
        assertNotNull(result.getDataAsJson(), "get_gateway body should be parseable JSON");
        assertTrue(result.getDataAsJson().toString().contains(gatewayId),
                "get_gateway response should contain the requested gateway id " + gatewayId
                        + ", got: " + result.getDataAsJson());
    }

    // 2. test_list_gateways
    @Test @Order(2)
    @DisplayName("list_gateways returns a list containing the created gateway")
    void testListGateways() {
        RequestResult result = client.listGateways(null, 100, null);
        assertTrue(result.isSuccess(), result.getError());
        assertNotNull(result.getData());
        assertNotNull(result.getDataAsJson(), "list response body should be parseable JSON");
        assertTrue(result.getDataAsJson().toString().contains(gatewayId),
                "list_gateways response should contain the created gateway id " + gatewayId);
    }

    // 3. test_update_gateway
    @Test @Order(3)
    @DisplayName("update_gateway persists the new description")
    void testUpdateGateway() {
        RequestResult result = client.updateGateway(gatewayId, "updated description by aa-it");
        assertTrue(result.isSuccess(), result.getError());
        // Re-get and assert the description actually persisted.
        RequestResult refetched = client.getGateway(gatewayId);
        assertTrue(refetched.isSuccess(), refetched.getError());
        assertNotNull(refetched.getDataAsJson(), "get_gateway body should be parseable JSON");
        assertTrue(refetched.getDataAsJson().toString().contains("updated description by aa-it"),
                "updated description should be persisted on the gateway, got: " + refetched.getDataAsJson());
    }

    // 4. test_get_target
    @Test @Order(4)
    @DisplayName("get_gateway_target returns the created target with a matching id")
    void testGetTarget() {
        RequestResult result = client.getGatewayTarget(gatewayId, targetId);
        assertTrue(result.isSuccess(), result.getError());
        assertNotNull(result.getDataAsJson(), "get_gateway_target body should be parseable JSON");
        assertTrue(result.getDataAsJson().toString().contains(targetId),
                "get_gateway_target response should contain the requested target id " + targetId
                        + ", got: " + result.getDataAsJson());
    }

    // 5. test_list_targets
    @Test @Order(5)
    @DisplayName("list_gateway_targets returns a list containing the created target")
    void testListTargets() {
        RequestResult result = client.listGatewayTargets(gatewayId, 100, null);
        assertTrue(result.isSuccess(), result.getError());
        assertNotNull(result.getData());
        assertNotNull(result.getDataAsJson(), "list response body should be parseable JSON");
        assertTrue(result.getDataAsJson().toString().contains(targetId),
                "list_gateway_targets response should contain the created target id " + targetId);
    }

    // 6. test_update_target
    @Test @Order(6)
    @DisplayName("update_gateway_target persists the new description")
    void testUpdateTarget() {
        RequestResult result = client.updateGatewayTarget(
                gatewayId, targetId, null, "updated target by aa-it", null, null);
        assertTrue(result.isSuccess(), result.getError());
        // Re-get and assert the description actually persisted.
        RequestResult refetched = client.getGatewayTarget(gatewayId, targetId);
        assertTrue(refetched.isSuccess(), refetched.getError());
        assertNotNull(refetched.getDataAsJson(), "get_gateway_target body should be parseable JSON");
        assertTrue(refetched.getDataAsJson().toString().contains("updated target by aa-it"),
                "updated description should be persisted on the target, got: " + refetched.getDataAsJson());
    }
}
