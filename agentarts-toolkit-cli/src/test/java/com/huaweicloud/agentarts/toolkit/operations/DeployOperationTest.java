package com.huaweicloud.agentarts.toolkit.operations;

import com.huaweicloud.agentarts.sdk.core.config.AgentArtsConfig;
import com.huaweicloud.agentarts.sdk.core.config.AgentArtsConfigList;
import com.huaweicloud.agentarts.sdk.core.config.BaseConfig;
import com.huaweicloud.agentarts.sdk.core.config.InvokeConfig;
import com.huaweicloud.agentarts.sdk.core.config.RuntimeConfig;
import com.huaweicloud.agentarts.sdk.service.runtime.model.CreateAgentRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployOperationTest {

    @AfterEach
    void clearConfigOverride() {
        ConfigOperation.clearConfigFileOverride();
    }

    @Test
    void buildsSafeRuntimeRequestDefaults() {
        RuntimeConfig runtime = new RuntimeConfig();

        CreateAgentRequest request = DeployOperation.buildCreateRequest(
                "sample-agent", "registry.example/sample:latest", runtime, null);

        assertEquals("sample-agent", request.getName());
        assertTrue(request.getDescription().contains("registry.example/sample:latest"));
        assertEquals("registry.example/sample:latest", request.getArtifactSource().get("url"));
        assertEquals(List.of(), request.getArtifactSource().get("commands"));
        assertEquals("HTTP", request.getInvokeConfig().get("protocol"));
        assertEquals(8080, request.getInvokeConfig().get("port"));
        assertEquals(Map.of("enabled", false),
                request.getInvokeConfig().get("file_transfer_config"));
        assertEquals("ACCURATE_MATCH", request.getInvokeConfig().get("url_match_type"));
        assertEquals("arm64", request.getArch());
        assertNull(request.getEnvironmentVariables());
        assertNull(request.getTags());
    }

    @Test
    void preservesConfiguredRuntimeFieldsAndFiltersNullEntries() {
        RuntimeConfig runtime = new RuntimeConfig();
        runtime.setArtifactSource(new LinkedHashMap<>(Map.of("commands", List.of("run"))));
        InvokeConfig invoke = new InvokeConfig();
        invoke.setProtocol("HTTPS");
        invoke.setPort(9443);
        invoke.setUrlMatchType("PREFIX");
        invoke.setFileTransferConfig(new LinkedHashMap<>(Map.of("mode", "stream")));
        runtime.setInvokeConfig(invoke);
        runtime.setNetworkConfig(Map.of("network_mode", "PUBLIC"));
        runtime.setObservability(Map.of("logging", true));
        runtime.setExecutionAgencyName("agency");
        runtime.setAgentGatewayId("gateway");
        runtime.setIdentityConfiguration(Map.of("type", "api_key"));
        runtime.setArch("ARM64");

        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("VALID", "value");
        environment.put("IGNORED", null);
        runtime.setEnvironmentVariables(environment);
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("team", "sdk");
        tags.put(null, "ignored");
        runtime.setTags(tags);

        CreateAgentRequest request = DeployOperation.buildCreateRequest(
                "sample", "new-image", runtime, "custom description");

        assertEquals("custom description", request.getDescription());
        assertEquals(List.of("run"), request.getArtifactSource().get("commands"));
        assertEquals("new-image", request.getArtifactSource().get("url"));
        assertEquals("HTTPS", request.getInvokeConfig().get("protocol"));
        assertEquals(9443, request.getInvokeConfig().get("port"));
        assertEquals(Map.of("mode", "stream", "enabled", false),
                request.getInvokeConfig().get("file_transfer_config"));
        assertEquals("PREFIX", request.getInvokeConfig().get("url_match_type"));
        assertEquals(List.of(Map.of("key", "VALID", "value", "value")),
                request.getEnvironmentVariables());
        assertEquals(List.of(Map.of("key", "team", "value", "sdk")), request.getTags());
        assertEquals("ARM64", request.getArch());
        assertEquals(runtime.getNetworkConfig(), request.getNetworkConfig());
        assertEquals(runtime.getObservability(), request.getObservability());
        assertEquals("agency", request.getExecutionAgencyName());
        assertEquals("gateway", request.getAgentGatewayId());
        assertNotNull(request.getIdentityConfiguration());
    }

    @Test
    void resolvesExplicitDefaultAndFirstAgentKeys() {
        AgentArtsConfigList config = new AgentArtsConfigList();
        config.addAgent("first", new AgentArtsConfig());
        config.addAgent("second", new AgentArtsConfig());
        config.setDefaultAgent("second");

        assertEquals("first", DeployOperation.resolveAgentKey(config, "first"));
        assertEquals("second", DeployOperation.resolveAgentKey(config, "unknown"));
        config.setDefaultAgent("missing");
        assertEquals("first", DeployOperation.resolveAgentKey(config, null));
        config.setAgents(null);
        assertNull(DeployOperation.resolveAgentKey(config, null));
    }

    @Test
    void deployValidationFailsBeforeStartingExternalProcesses(@TempDir Path tempDir) {
        ConfigOperation.setConfigFileOverride(tempDir.resolve("config.yaml").toFile());

        assertThrows(IllegalArgumentException.class, () -> DeployOperation.deployProject(
                null, "unsupported", null, null, null, null, null, false, false, null));
        assertThrows(IllegalArgumentException.class, () -> DeployOperation.deployProject(
                null, "cloud", "invalid tag", null, null, null, null, false, false, null));
        assertThrows(IllegalArgumentException.class, () -> DeployOperation.deployProject(
                null, "local", "latest", 0, null, null, null, false, false, null));

        ConfigOperation.saveConfig(new AgentArtsConfigList());
        assertThrows(IllegalStateException.class, () -> DeployOperation.deployProject(
                null, "cloud", "latest", null, null, null, null, true, false, null));

        AgentArtsConfigList config = new AgentArtsConfigList();
        AgentArtsConfig malformed = new AgentArtsConfig();
        malformed.setBase(null);
        config.addAgent("sample", malformed);
        config.setDefaultAgent("sample");
        ConfigOperation.saveConfig(config);
        assertThrows(IllegalStateException.class, () -> DeployOperation.deployProject(
                null, "cloud", "latest", null, null, null, null, true, false, null));

        BaseConfig base = new BaseConfig();
        base.setName("sample");
        base.setRegion("cn-test-1");
        malformed.setBase(base);
        ConfigOperation.saveConfig(config);
        assertThrows(IllegalArgumentException.class, () -> DeployOperation.deployProject(
                null, "local", "latest", null, null, null, null, true, false, null));
        assertThrows(IllegalStateException.class, () -> DeployOperation.deployProject(
                null, "cloud", "latest", null, null, null, null, true, false, null));
        assertThrows(IllegalStateException.class, () -> DeployOperation.deployProject(
                null, "cloud", "latest", null, null, null, null, false, false, null));

        base.setName(null);
        base.setRegion(" ");
        InvokeConfig invoke = new InvokeConfig();
        invoke.setPort(9090);
        malformed.getRuntime().setInvokeConfig(invoke);
        ConfigOperation.saveConfig(config);
        assertThrows(IllegalArgumentException.class, () -> DeployOperation.deployProject(
                null, "local", null, 9090, null, null, null, true, true, null));
    }
}
