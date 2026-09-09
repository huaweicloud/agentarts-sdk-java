package com.huaweicloud.agentarts.sdk.tests.e2e;

import com.huaweicloud.agentarts.sdk.gateway.GatewayClient;
import com.huaweicloud.agentarts.sdk.memory.MemoryClient;
import com.huaweicloud.agentarts.sdk.service.runtime.RuntimeClient;
import com.huaweicloud.agentarts.sdk.tools.CodeInterpreterClient;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Read-only list tests — (4 tests: list spaces, gateways, agents, interpreters).
 */
@Tag("e2e")
@DisplayName("Read-Only List Tests")
class ReadonlyListsTest {

    @BeforeAll
    static void checkCredentials() {
        assumeTrue(E2EConfig.hasCloudCredentials(),
                "Set HUAWEICLOUD_SDK_AK and HUAWEICLOUD_SDK_SK to run cloud tests");
    }

    // 1. test_list_spaces
    @Test
    @DisplayName("list_spaces returns a parseable response with a list of spaces")
    void testListSpaces() {
        try (MemoryClient client = new MemoryClient(E2EConfig.getRegion(), null)) {
            var result = client.listSpaces(1, 0);
            assertNotNull(result);
            assertNotNull(result.getItems(), "items should be a list");
        }
    }

    // 2. test_list_gateways
    @Test
    @DisplayName("list_gateways succeeds and returns a parseable JSON body")
    void testListGateways() {
        try (GatewayClient client = new GatewayClient()) {
            RequestResult result = client.listGateways(null, 1, null);
            // May fail with 403 if tenant hasn't enabled Gateway service
            // In Python this is: assert result.success, result.error
            if (!result.isSuccess() && result.getStatusCode() == 403) {
                System.err.println("Gateway service not enabled for tenant: " + result.getError());
                assumeTrue(false, "Gateway service not enabled for this tenant");
            }
            assertTrue(result.isSuccess(), result.getError());
            // Body must parse as JSON (not just an empty 200) — a real
            // connectivity + auth + deserialization probe.
            assertNotNull(result.getDataAsJson(), "list_gateways body should be parseable JSON");
        }
    }

    // 3. test_list_runtime_agents
    @Test
    @DisplayName("list_runtime_agents returns a parseable response with a list of agents")
    void testListRuntimeAgents() {
        try (RuntimeClient client = new RuntimeClient(E2EConfig.getRegion())) {
            var agents = client.getAgents(null, 1, 10);
            assertNotNull(agents, "agents response should not be null");
            assertNotNull(agents.getItems(), "items should be a list");
        }
    }

    // 4. test_list_code_interpreters
    @Test
    @DisplayName("list_code_interpreters returns a parseable response with a list of interpreters")
    void testListCodeInterpreters() {
        try (CodeInterpreterClient client = new CodeInterpreterClient(E2EConfig.getRegion())) {
            var result = client.listCodeInterpreters(null, 1, 0);
            assertNotNull(result, "result should not be null");
            assertNotNull(result.getItems(), "items should be a list");
        }
    }
}
