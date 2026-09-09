package com.huaweicloud.agentarts.sdk.core.config;

import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all config model classes.
 */
class ConfigTest {

    // ============================================================
    // BaseConfig
    // ============================================================

    @Nested
    class BaseConfigTests {

        @Test
        void defaultValues() {
            BaseConfig config = new BaseConfig();
            assertNull(config.getName());
            assertNull(config.getEntrypoint());
            assertNull(config.getDependencyFile());
            assertNull(config.getRegion());
            assertEquals("linux/amd64", config.getPlatform());
            assertEquals("java17", config.getLanguage());
            assertEquals("docker", config.getContainerRuntime());
            assertEquals("eclipse-temurin:17-jre", config.getBaseImage());
        }

        @Test
        void settersAndGetters() {
            BaseConfig config = new BaseConfig();
            config.setName("my-agent");
            config.setEntrypoint("com.example.Main");
            config.setDependencyFile("pom.xml");
            config.setRegion("cn-north-4");
            config.setPlatform("linux/arm64");
            config.setLanguage("java21");
            config.setContainerRuntime("podman");
            config.setBaseImage("openjdk:21-jre");

            assertEquals("my-agent", config.getName());
            assertEquals("com.example.Main", config.getEntrypoint());
            assertEquals("pom.xml", config.getDependencyFile());
            assertEquals("cn-north-4", config.getRegion());
            assertEquals("linux/arm64", config.getPlatform());
            assertEquals("java21", config.getLanguage());
            assertEquals("podman", config.getContainerRuntime());
            assertEquals("openjdk:21-jre", config.getBaseImage());
        }

        @Test
        void jsonSerialization() {
            BaseConfig config = new BaseConfig();
            config.setName("test-agent");
            config.setEntrypoint("Main.run");

            String json = JsonUtils.toJson(config);
            assertTrue(json.contains("\"name\":\"test-agent\""));
            assertTrue(json.contains("\"entrypoint\":\"Main.run\""));
            assertTrue(json.contains("\"platform\":\"linux/amd64\""));
            assertTrue(json.contains("\"language\":\"java17\""));
        }

        @Test
        void jsonDeserialization() throws Exception {
            String json = "{\"name\":\"my-agent\",\"entrypoint\":\"Main\",\"platform\":\"linux/arm64\","
                    + "\"language\":\"java21\",\"container_runtime\":\"podman\",\"base_image\":\"openjdk:21-jre\","
                    + "\"dependency_file\":\"pom.xml\",\"region\":\"cn-north-4\"}";
            BaseConfig config = JsonUtils.MAPPER.readValue(json, BaseConfig.class);
            assertEquals("my-agent", config.getName());
            assertEquals("Main", config.getEntrypoint());
            assertEquals("linux/arm64", config.getPlatform());
            assertEquals("java21", config.getLanguage());
            assertEquals("podman", config.getContainerRuntime());
            assertEquals("openjdk:21-jre", config.getBaseImage());
            assertEquals("pom.xml", config.getDependencyFile());
            assertEquals("cn-north-4", config.getRegion());
        }

        @Test
        void ignoresUnknownFields() throws Exception {
            String json = "{\"name\":\"test\",\"unknown_field\":\"value\"}";
            BaseConfig config = JsonUtils.MAPPER.readValue(json, BaseConfig.class);
            assertEquals("test", config.getName());
        }

        @Test
        void partialDeserialization() throws Exception {
            String json = "{\"name\":\"partial\"}";
            BaseConfig config = JsonUtils.MAPPER.readValue(json, BaseConfig.class);
            assertEquals("partial", config.getName());
            // Defaults should still apply for fields not in JSON
            assertEquals("linux/amd64", config.getPlatform());
            assertEquals("java17", config.getLanguage());
        }
    }

    // ============================================================
    // SWRConfig
    // ============================================================

    @Nested
    class SWRConfigTests {

        @Test
        void defaultValues() {
            SWRConfig config = new SWRConfig();
            assertNull(config.getOrganization());
            assertNull(config.getRepository());
            assertFalse(config.isOrganizationAutoCreate());
            assertFalse(config.isRepositoryAutoCreate());
        }

        @Test
        void settersAndGetters() {
            SWRConfig config = new SWRConfig();
            config.setOrganization("my-org");
            config.setRepository("my-repo");
            config.setOrganizationAutoCreate(true);
            config.setRepositoryAutoCreate(true);

            assertEquals("my-org", config.getOrganization());
            assertEquals("my-repo", config.getRepository());
            assertTrue(config.isOrganizationAutoCreate());
            assertTrue(config.isRepositoryAutoCreate());
        }

        @Test
        void jsonRoundTrip() throws Exception {
            String json = "{\"organization\":\"org1\",\"repository\":\"repo1\","
                    + "\"organization_auto_create\":true,\"repository_auto_create\":false}";
            SWRConfig config = JsonUtils.MAPPER.readValue(json, SWRConfig.class);
            assertEquals("org1", config.getOrganization());
            assertEquals("repo1", config.getRepository());
            assertTrue(config.isOrganizationAutoCreate());
            assertFalse(config.isRepositoryAutoCreate());
        }
    }

    // ============================================================
    // InvokeConfig
    // ============================================================

    @Nested
    class InvokeConfigTests {

        @Test
        void defaultValues() {
            InvokeConfig config = new InvokeConfig();
            assertEquals("HTTP", config.getProtocol());
            assertEquals(8080, config.getPort());
            assertNull(config.getFileTransferConfig());
            assertEquals("ACCURATE_MATCH", config.getUrlMatchType());
        }

        @Test
        void settersAndGetters() {
            InvokeConfig config = new InvokeConfig();
            config.setProtocol("HTTPS");
            config.setPort(9090);
            config.setUrlMatchType("PREFIX_MATCH");
            Map<String, Object> ftc = new LinkedHashMap<>();
            ftc.put("max_size_mb", 100);
            config.setFileTransferConfig(ftc);

            assertEquals("HTTPS", config.getProtocol());
            assertEquals(9090, config.getPort());
            assertEquals("PREFIX_MATCH", config.getUrlMatchType());
            assertNotNull(config.getFileTransferConfig());
            assertEquals(100, config.getFileTransferConfig().get("max_size_mb"));
        }

        @Test
        void jsonDeserialization() throws Exception {
            String json = "{\"protocol\":\"HTTPS\",\"port\":9090,\"url_match_type\":\"PREFIX_MATCH\","
                    + "\"file_transfer_config\":{\"max_size_mb\":100}}";
            InvokeConfig config = JsonUtils.MAPPER.readValue(json, InvokeConfig.class);
            assertEquals("HTTPS", config.getProtocol());
            assertEquals(9090, config.getPort());
            assertEquals("PREFIX_MATCH", config.getUrlMatchType());
            assertNotNull(config.getFileTransferConfig());
        }

        @Test
        void jsonSerialization() {
            InvokeConfig config = new InvokeConfig();
            config.setPort(9090);
            String json = JsonUtils.toJson(config);
            assertTrue(json.contains("\"port\":9090"));
            assertTrue(json.contains("\"protocol\":\"HTTP\""));
        }
    }

    // ============================================================
    // RuntimeConfig
    // ============================================================

    @Nested
    class RuntimeConfigTests {

        @Test
        void defaultValues() {
            RuntimeConfig config = new RuntimeConfig();
            assertNull(config.getAgentId());
            assertNull(config.getAgentGatewayId());
            assertEquals("arm64", config.getArch());
            assertNull(config.getExecutionAgencyName());
            assertNull(config.getIdentityConfiguration());
            assertNull(config.getNetworkConfig());
            assertNull(config.getInvokeConfig());
            assertNull(config.getObservability());
            assertNull(config.getArtifactSource());
            assertNull(config.getEnvironmentVariables());
            assertNull(config.getTags());
        }

        @Test
        void settersAndGetters() {
            RuntimeConfig config = new RuntimeConfig();
            config.setAgentId("agent-123");
            config.setAgentGatewayId("gw-456");
            config.setArch("ARM_64");
            config.setExecutionAgencyName("my-agency");

            Map<String, String> envVars = Map.of("KEY", "VALUE");
            config.setEnvironmentVariables(envVars);

            Map<String, String> tags = Map.of("env", "test");
            config.setTags(tags);

            assertEquals("agent-123", config.getAgentId());
            assertEquals("gw-456", config.getAgentGatewayId());
            assertEquals("ARM_64", config.getArch());
            assertEquals("my-agency", config.getExecutionAgencyName());
            assertEquals("VALUE", config.getEnvironmentVariables().get("KEY"));
            assertEquals("test", config.getTags().get("env"));
        }

        @Test
        void jsonDeserializationWithNested() throws Exception {
            String json = "{\"agent_id\":\"a1\",\"agent_gateway_id\":\"g1\",\"arch\":\"arm64\","
                    + "\"invoke_config\":{\"protocol\":\"HTTP\",\"port\":8080},"
                    + "\"environment_variables\":{\"K\":\"V\"},\"tags\":{\"t1\":\"v1\"}}";
            RuntimeConfig config = JsonUtils.MAPPER.readValue(json, RuntimeConfig.class);
            assertEquals("a1", config.getAgentId());
            assertEquals("g1", config.getAgentGatewayId());
            assertNotNull(config.getInvokeConfig());
            assertEquals("HTTP", config.getInvokeConfig().getProtocol());
            assertEquals(8080, config.getInvokeConfig().getPort());
            assertEquals("V", config.getEnvironmentVariables().get("K"));
            assertEquals("v1", config.getTags().get("t1"));
        }
    }

    // ============================================================
    // AgentArtsConfig
    // ============================================================

    @Nested
    class AgentArtsConfigTests {

        @Test
        void defaultValues() {
            AgentArtsConfig config = new AgentArtsConfig();
            assertNotNull(config.getBase());
            assertNotNull(config.getSwrConfig());
            assertNotNull(config.getRuntime());
        }

        @Test
        void settersAndGetters() {
            AgentArtsConfig config = new AgentArtsConfig();
            BaseConfig base = new BaseConfig();
            base.setName("test");
            config.setBase(base);

            SWRConfig swr = new SWRConfig();
            swr.setOrganization("org");
            config.setSwrConfig(swr);

            RuntimeConfig runtime = new RuntimeConfig();
            runtime.setAgentId("a1");
            config.setRuntime(runtime);

            assertEquals("test", config.getBase().getName());
            assertEquals("org", config.getSwrConfig().getOrganization());
            assertEquals("a1", config.getRuntime().getAgentId());
        }

        @Test
        void jsonRoundTrip() throws Exception {
            String json = "{\"base\":{\"name\":\"agent1\",\"entrypoint\":\"Main\"},"
                    + "\"swr_config\":{\"organization\":\"org\",\"repository\":\"repo\"},"
                    + "\"runtime\":{\"agent_id\":\"a1\"}}";
            AgentArtsConfig config = JsonUtils.MAPPER.readValue(json, AgentArtsConfig.class);
            assertEquals("agent1", config.getBase().getName());
            assertEquals("Main", config.getBase().getEntrypoint());
            assertEquals("org", config.getSwrConfig().getOrganization());
            assertEquals("a1", config.getRuntime().getAgentId());
        }

        @Test
        void ignoresUnknownFields() throws Exception {
            String json = "{\"base\":{\"name\":\"test\"},\"extra_field\":\"ignored\"}";
            AgentArtsConfig config = JsonUtils.MAPPER.readValue(json, AgentArtsConfig.class);
            assertEquals("test", config.getBase().getName());
        }
    }

    // ============================================================
    // AgentArtsConfigList
    // ============================================================

    @Nested
    class AgentArtsConfigListTests {

        @Test
        void defaultValues() {
            AgentArtsConfigList list = new AgentArtsConfigList();
            assertNull(list.getDefaultAgent());
            assertNotNull(list.getAgents());
            assertTrue(list.getAgents().isEmpty());
        }

        @Test
        void addAndGetAgent() {
            AgentArtsConfigList list = new AgentArtsConfigList();
            AgentArtsConfig config = new AgentArtsConfig();
            config.getBase().setName("test-agent");

            list.addAgent("agent1", config);

            assertEquals("test-agent", list.getAgent("agent1").getBase().getName());
        }

        @Test
        void removeAgent() {
            AgentArtsConfigList list = new AgentArtsConfigList();
            list.addAgent("agent1", new AgentArtsConfig());
            list.removeAgent("agent1");
            assertNull(list.getAgent("agent1"));
        }

        @Test
        void getNonexistentAgentReturnsNull() {
            AgentArtsConfigList list = new AgentArtsConfigList();
            assertNull(list.getAgent("nonexistent"));
        }

        @Test
        void setAgentsNullSafe() {
            AgentArtsConfigList list = new AgentArtsConfigList();
            list.setAgents(null);
            assertNotNull(list.getAgents());
            assertTrue(list.getAgents().isEmpty());
        }

        @Test
        void setDefaultAgent() {
            AgentArtsConfigList list = new AgentArtsConfigList();
            list.setDefaultAgent("primary");
            assertEquals("primary", list.getDefaultAgent());
        }

        @Test
        void jsonRoundTrip() throws Exception {
            String json = "{\"default_agent\":\"main\","
                    + "\"agents\":{\"main\":{\"base\":{\"name\":\"main-agent\"}}}}";
            AgentArtsConfigList list = JsonUtils.MAPPER.readValue(json, AgentArtsConfigList.class);
            assertEquals("main", list.getDefaultAgent());
            assertNotNull(list.getAgent("main"));
            assertEquals("main-agent", list.getAgent("main").getBase().getName());
        }

        @Test
        void multipleAgents() {
            AgentArtsConfigList list = new AgentArtsConfigList();
            AgentArtsConfig a1 = new AgentArtsConfig();
            a1.getBase().setName("agent1");
            AgentArtsConfig a2 = new AgentArtsConfig();
            a2.getBase().setName("agent2");

            list.addAgent("a1", a1);
            list.addAgent("a2", a2);

            assertEquals(2, list.getAgents().size());
            assertEquals("agent1", list.getAgent("a1").getBase().getName());
            assertEquals("agent2", list.getAgent("a2").getBase().getName());
        }

        @Test
        void removeNonexistentAgentIsNoOp() {
            AgentArtsConfigList list = new AgentArtsConfigList();
            list.addAgent("a1", new AgentArtsConfig());
            list.removeAgent("nonexistent");
            assertEquals(1, list.getAgents().size());
        }
    }
}
