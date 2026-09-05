package com.huaweicloud.agentarts.sdk.tests.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import com.huaweicloud.agentarts.sdk.gateway.GatewayClient;
import com.huaweicloud.agentarts.toolkit.AgentArtsCli;
import com.huaweicloud.agentarts.toolkit.commands.CliSupport;
import org.junit.jupiter.api.*;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Gateway CLI e2e tests — exercises the picocli command path exclusively.
 *
 * <p>Every assertion comes from the CLI's stdout JSON; the {@link GatewayClient}
 * is constructed only to register LIFO cleanup for resources the CLI creates (the
 * cleanup path is not what is under test). The read-only {@code list-gateways}
 * runs in the default tier; the {@code create → get → delete} lifecycle requires
 * {@code AGENTARTS_TEST_ALLOW_CREATE=1}.</p>
 */
@Tag("e2e")
@DisplayName("CLI Gateway E2E Tests")
class CliGatewayE2ETest {

    private CommandLine cli;
    private GatewayClient client;
    private E2EResourceRegistry registry;
    private String runId;

    /** Captured stdout/stderr from the last {@link #runCli(String...)} call. */
    private String stdout;
    private String stderr;

    @BeforeAll
    static void requireCredentials() {
        assumeTrue(E2EConfig.hasCloudCredentials(),
                "Set HUAWEICLOUD_SDK_AK and HUAWEICLOUD_SDK_SK to run cloud tests");
    }

    @BeforeEach
    void setUp() {
        cli = CliSupport.withCleanExit(new CommandLine(new AgentArtsCli()));
        // Suppress picocli's own usage/version output (command bodies use System.out).
        cli.setOut(new PrintWriter(new StringWriter()));
        cli.setErr(new PrintWriter(new StringWriter()));
        client = new GatewayClient();
        registry = new E2EResourceRegistry();
        runId = E2EConfig.getRunId();
    }

    @AfterEach
    void tearDown() {
        if (registry != null) registry.cleanupAll();
        if (client != null) client.close();
    }

    /** Read-only {@code gateway list-gateways --limit 1} — no resources created. */
    @Test
    @DisplayName("CLI gateway list-gateways --limit 1 prints JSON")
    void test_cli_gateway_list_readonly() {
        int exit = runCli("gateway", "list-gateways", "--limit", "1");
        // Tolerate 403 when the tenant hasn't enabled the Gateway service.
        if (exit != 0 && stderr.contains("403")) {
            assumeTrue(false, "Gateway service not enabled for this tenant");
        }
        assertEquals(0, exit, "CLI list-gateways should exit 0; stderr=" + stderr);
        JsonNode node = parseJson(stdout);
        assertNotNull(node, "CLI list-gateways should print JSON; stdout=" + stdout);
        // The list response must carry a real payload (gateways array / items / total),
        // not just be any JSON object.
        assertTrue(node.has("gateways") || node.has("items") || node.has("total"),
                "list response should carry gateways/items/total; stdout=" + stdout);
    }

    /** {@code create → get → list → delete} through the CLI, asserting the CLI's JSON output. */
    @Test
    @DisplayName("CLI gateway create→get→delete lifecycle")
    void test_cli_gateway_lifecycle() {
        assumeTrue(E2EConfig.allowCreate(),
                "Set AGENTARTS_TEST_ALLOW_CREATE=1 to run create/delete lifecycle tests");

        String name = E2EHelpers.uniqueName("cli-gw", runId);

        // 1. Create via CLI — parse the gateway id from the CLI's JSON stdout.
        int createExit = runCli("gateway", "create-gateway",
                "--name", name, "--description", "aa-it");
        assertEquals(0, createExit, "CLI create-gateway should exit 0; stderr=" + stderr);
        JsonNode created = parseJson(stdout);
        assertNotNull(created, "create should print JSON; stdout=" + stdout);
        String gatewayId = extractId(created);
        assertNotNull(gatewayId, "create response should contain an id; stdout=" + stdout);
        // Register SDK cleanup as a safety net (CLI delete below should reclaim it).
        final String id = gatewayId;
        registry.register(() -> {
            try { client.deleteGateway(id); } catch (Exception ignored) { /* already deleted */ }
        }, "gateway:" + id);

        // 2. Get via CLI — the returned id must match the created one.
        int getExit = runCli("gateway", "get-gateway", id);
        assertEquals(0, getExit, "CLI get-gateway should exit 0; stderr=" + stderr);
        JsonNode got = parseJson(stdout);
        assertNotNull(got, "get should print JSON; stdout=" + stdout);
        assertEquals(id, extractId(got), "get response id should match; stdout=" + stdout);

        // 3. List via CLI — must succeed and return a JSON object.
        int listExit = runCli("gateway", "list-gateways", "--limit", "5");
        assertEquals(0, listExit, "CLI list-gateways should exit 0; stderr=" + stderr);
        JsonNode listed = parseJson(stdout);
        assertNotNull(listed, "list should print JSON; stdout=" + stdout);
        assertTrue(listed.isObject(), "list response should be a JSON object; stdout=" + stdout);
        // The just-created gateway must actually appear in the list — scan the
        // gateways/items array for an entry whose id / gateway_id matches gatewayId.
        JsonNode arr = listed.has("gateways") ? listed.get("gateways")
                : (listed.has("items") ? listed.get("items") : null);
        boolean foundCreated = false;
        if (arr != null && arr.isArray()) {
            for (JsonNode g : arr) {
                String gid = findText(g, "id", "gateway_id");
                if (gatewayId.equals(gid)) {
                    foundCreated = true;
                    break;
                }
            }
        }
        assertTrue(foundCreated,
                "list gateways should contain the just-created gateway id " + gatewayId
                        + "; stdout=" + stdout);

        // 4. Delete via CLI.
        int deleteExit = runCli("gateway", "delete-gateway", id);
        assertEquals(0, deleteExit, "CLI delete-gateway should exit 0; stderr=" + stderr);
    }

    // ========================
    // Helpers
    // ========================

    /** Execute the CLI while capturing System.out / System.err. */
    private int runCli(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        int exit;
        try {
            exit = cli.execute(args);
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        stdout = out.toString(StandardCharsets.UTF_8);
        stderr = err.toString(StandardCharsets.UTF_8);
        return exit;
    }

    private static JsonNode parseJson(String text) {
        try {
            return JsonUtils.MAPPER.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    /** Pull a resource id out of a response node, trying "id" then "gateway_id". */
    private static String extractId(JsonNode node) {
        if (node == null) return null;
        String top = findText(node, "id", "gateway_id");
        if (top != null) return top;
        for (JsonNode child : node) {
            if (child == null) continue;
            String s = findText(child, "id", "gateway_id");
            if (s != null) return s;
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
}
