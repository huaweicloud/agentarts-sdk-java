package com.huaweicloud.agentarts.sdk.service.runtime.model;

import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all runtime model classes.
 */
class RuntimeModelTest {

    // ============================================================
    // CreateAgentRequest
    // ============================================================

    @Nested
    class CreateAgentRequestTests {

        @Test
        void defaultValues() {
            CreateAgentRequest req = new CreateAgentRequest();
            assertNull(req.getName());
            assertNull(req.getDescription());
            assertNull(req.getArtifactSource());
            assertNull(req.getStorageConfig());
            assertNull(req.getIdentityConfiguration());
            assertNull(req.getInvokeConfig());
            assertNull(req.getNetworkConfig());
            assertNull(req.getObservability());
            assertNull(req.getExecutionAgencyName());
            assertNull(req.getAgentGatewayId());
            assertNull(req.getEnvironmentVariables());
            assertNull(req.getTags());
        }

        @Test
        void fluentApiReturnsSameInstance() {
            CreateAgentRequest req = new CreateAgentRequest();
            assertSame(req, req.withName("test"));
            assertSame(req, req.withDescription("desc"));
            assertSame(req, req.withArtifactSource(Map.of()));
            assertSame(req, req.withStorageConfig(Map.of()));
            assertSame(req, req.withIdentityConfiguration(Map.of()));
            assertSame(req, req.withInvokeConfig(Map.of()));
            assertSame(req, req.withNetworkConfig(Map.of()));
            assertSame(req, req.withObservability(Map.of()));
            assertSame(req, req.withExecutionAgencyName("agency"));
            assertSame(req, req.withAgentGatewayId("gw"));
            assertSame(req, req.withEnvironmentVariables(List.of()));
            assertSame(req, req.withTags(List.of()));
        }

        @Test
        void fluentApiSetsValues() {
            CreateAgentRequest req = new CreateAgentRequest()
                    .withName("my-agent")
                    .withDescription("A test agent")
                    .withExecutionAgencyName("my-agency")
                    .withAgentGatewayId("gw-123");

            assertEquals("my-agent", req.getName());
            assertEquals("A test agent", req.getDescription());
            assertEquals("my-agency", req.getExecutionAgencyName());
            assertEquals("gw-123", req.getAgentGatewayId());
        }

        @Test
        void standardSetters() {
            CreateAgentRequest req = new CreateAgentRequest();
            req.setName("agent1");
            req.setDescription("desc1");
            Map<String, Object> artifact = Map.of("type", "swr");
            req.setArtifactSource(artifact);
            req.setExecutionAgencyName("agency1");
            req.setAgentGatewayId("gw1");

            assertEquals("agent1", req.getName());
            assertEquals("desc1", req.getDescription());
            assertEquals("swr", req.getArtifactSource().get("type"));
            assertEquals("agency1", req.getExecutionAgencyName());
            assertEquals("gw1", req.getAgentGatewayId());
        }

        @Test
        void jsonSerializationNonNullFieldsOnly() {
            CreateAgentRequest req = new CreateAgentRequest()
                    .withName("test-agent")
                    .withDescription("desc");
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"test-agent\""));
            assertTrue(json.contains("\"description\":\"desc\""));
            // Null fields should NOT appear in JSON due to @JsonInclude(NON_NULL)
            assertFalse(json.contains("artifact_source"));
            assertFalse(json.contains("identity_configuration"));
        }

        @Test
        void jsonSerializationWithAllFields() {
            CreateAgentRequest req = new CreateAgentRequest()
                    .withName("full-agent")
                    .withDescription("Full agent")
                    .withArtifactSource(Map.of("type", "swr", "image", "repo:latest"))
                    .withIdentityConfiguration(Map.of("agency", "my-agency"))
                    .withInvokeConfig(Map.of("protocol", "HTTP", "port", 8080))
                    .withNetworkConfig(Map.of("vpc_id", "vpc-123"))
                    .withObservability(Map.of("tracing", true))
                    .withExecutionAgencyName("agency")
                    .withAgentGatewayId("gw-1");

            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"full-agent\""));
            assertTrue(json.contains("\"artifact_source\""));
            assertTrue(json.contains("\"identity_configuration\""));
            assertTrue(json.contains("\"invoke_config\""));
            assertTrue(json.contains("\"network_config\""));
            assertTrue(json.contains("\"execution_agency_name\":\"agency\""));
        }

        @Test
        void jsonDeserialization() throws Exception {
            String json = "{\"name\":\"from-json\",\"description\":\"test\","
                    + "\"artifact_source\":{\"type\":\"swr\"},"
                    + "\"execution_agency_name\":\"agency1\","
                    + "\"agent_gateway_id\":\"gw-1\"}";
            CreateAgentRequest req = JsonUtils.MAPPER.readValue(json, CreateAgentRequest.class);
            assertEquals("from-json", req.getName());
            assertEquals("test", req.getDescription());
            assertEquals("swr", req.getArtifactSource().get("type"));
            assertEquals("agency1", req.getExecutionAgencyName());
            assertEquals("gw-1", req.getAgentGatewayId());
        }

        @Test
        void ignoresUnknownFields() throws Exception {
            String json = "{\"name\":\"test\",\"unknown_field\":\"value\"}";
            CreateAgentRequest req = JsonUtils.MAPPER.readValue(json, CreateAgentRequest.class);
            assertEquals("test", req.getName());
        }

        @Test
        void equalityAndHashCode() {
            CreateAgentRequest a = new CreateAgentRequest().withName("test").withDescription("desc");
            CreateAgentRequest b = new CreateAgentRequest().withName("test").withDescription("desc");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void inequality() {
            CreateAgentRequest a = new CreateAgentRequest().withName("a");
            CreateAgentRequest b = new CreateAgentRequest().withName("b");
            assertNotEquals(a, b);
        }

        @Test
        void toStringContainsName() {
            CreateAgentRequest req = new CreateAgentRequest().withName("test-agent");
            assertTrue(req.toString().contains("test-agent"));
        }

        @Test
        void environmentVariablesField() {
            List<Map<String, String>> envVars = List.of(
                    Map.of("key", "KEY1", "value", "val1"),
                    Map.of("key", "KEY2", "value", "val2")
            );
            CreateAgentRequest req = new CreateAgentRequest().withEnvironmentVariables(envVars);
            assertEquals(2, req.getEnvironmentVariables().size());
            assertEquals("KEY1", req.getEnvironmentVariables().get(0).get("key"));
        }

        @Test
        void toStringRedactsCredentialBearingConfiguration() {
            String marker = "unit-sensitive-marker";
            CreateAgentRequest req = new CreateAgentRequest()
                    .withName("agent")
                    .withIdentityConfiguration(Map.of("token", marker))
                    .withInvokeConfig(Map.of("authorization", marker))
                    .withEnvironmentVariables(List.of(Map.of("key", "API_KEY", "value", marker)));

            assertFalse(req.toString().contains(marker));
            assertTrue(req.toString().contains("[REDACTED]"));
        }

        @Test
        void tagsField() {
            List<Map<String, String>> tags = List.of(Map.of("key", "env", "value", "prod"));
            CreateAgentRequest req = new CreateAgentRequest().withTags(tags);
            assertEquals(1, req.getTags().size());
            assertEquals("env", req.getTags().get(0).get("key"));
        }
    }

    // ============================================================
    // UpdateAgentRequest
    // ============================================================

    @Nested
    class UpdateAgentRequestTests {

        @Test
        void jsonSerialization() {
            UpdateAgentRequest req = new UpdateAgentRequest()
                    .withDescription("Updated description");
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"description\":\"Updated description\""));
            // Should NOT have name field
            assertFalse(json.contains("\"name\""));
        }

        @Test
        void equalityAndHashCode() {
            UpdateAgentRequest a = new UpdateAgentRequest().withDescription("d1");
            UpdateAgentRequest b = new UpdateAgentRequest().withDescription("d1");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void toStringContainsDescription() {
            UpdateAgentRequest req = new UpdateAgentRequest().withDescription("test desc");
            assertTrue(req.toString().contains("test desc"));
        }

        @Test
        void toStringRedactsEnvironmentAndInvokeConfiguration() {
            String marker = "unit-sensitive-marker";
            UpdateAgentRequest req = new UpdateAgentRequest()
                    .withInvokeConfig(Map.of("token", marker))
                    .withEnvironmentVariables(List.of(Map.of("key", "SECRET", "value", marker)));

            assertFalse(req.toString().contains(marker));
            assertTrue(req.toString().contains("[REDACTED]"));
        }
    }

    // ============================================================
    // CreateAgentEndpointRequest
    // ============================================================

    @Nested
    class CreateAgentEndpointRequestTests {

        @Test
        void fluentApi() {
            CreateAgentEndpointRequest req = new CreateAgentEndpointRequest()
                    .withEndpointName("my-endpoint")
                    .withName("test")
                    .withAgentId("agent-123")
                    .withEndpointType("default")
                    .withTargetVersionName("v1")
                    .withConfig(Map.of("key", "value"));

            assertEquals("my-endpoint", req.getEndpointName());
            assertEquals("test", req.getName());
            assertEquals("agent-123", req.getAgentId());
            assertEquals("default", req.getEndpointType());
            assertEquals("v1", req.getTargetVersionName());
            assertEquals("value", req.getConfig().get("key"));
        }

        @Test
        void jsonSerialization() {
            CreateAgentEndpointRequest req = new CreateAgentEndpointRequest()
                    .withEndpointName("ep1")
                    .withAgentId("a1");
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"endpoint_name\":\"ep1\""));
            assertTrue(json.contains("\"agent_id\":\"a1\""));
        }

        @Test
        void equalityAndHashCode() {
            CreateAgentEndpointRequest a = new CreateAgentEndpointRequest()
                    .withEndpointName("ep").withAgentId("a1");
            CreateAgentEndpointRequest b = new CreateAgentEndpointRequest()
                    .withEndpointName("ep").withAgentId("a1");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void toStringContainsFields() {
            CreateAgentEndpointRequest req = new CreateAgentEndpointRequest()
                    .withEndpointName("my-ep").withAgentId("a-123");
            String str = req.toString();
            assertTrue(str.contains("my-ep"));
            assertTrue(str.contains("a-123"));
        }
    }

    // ============================================================
    // UpdateAgentEndpointRequest
    // ============================================================

    @Nested
    class UpdateAgentEndpointRequestTests {

        @Test
        void fluentApi() {
            UpdateAgentEndpointRequest req = new UpdateAgentEndpointRequest()
                    .withConfig(Map.of("timeout", 30));
            assertEquals(30, req.getConfig().get("timeout"));
        }

        @Test
        void jsonSerialization() {
            UpdateAgentEndpointRequest req = new UpdateAgentEndpointRequest()
                    .withConfig(Map.of("key", "val"));
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"config\""));
            assertTrue(json.contains("\"key\":\"val\""));
        }

        @Test
        void equalityAndHashCode() {
            UpdateAgentEndpointRequest a = new UpdateAgentEndpointRequest()
                    .withConfig(Map.of("k", "v"));
            UpdateAgentEndpointRequest b = new UpdateAgentEndpointRequest()
                    .withConfig(Map.of("k", "v"));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    // ============================================================
    // ExecCommandRequest
    // ============================================================

    @Nested
    @SuppressWarnings("deprecation")
    class ExecCommandRequestTests {

        @Test
        void defaultValues() {
            ExecCommandRequest req = new ExecCommandRequest();
            assertNull(req.getCommand());
            assertFalse(req.isChunked());
        }

        @Test
        void fluentApi() {
            ExecCommandRequest req = new ExecCommandRequest()
                    .withCommand(List.of("ls", "-la"))
                    .withChunked(true);
            assertEquals(List.of("ls", "-la"), req.getCommand());
            assertTrue(req.isChunked());
        }

        @Test
        void jsonSerialization() {
            ExecCommandRequest req = new ExecCommandRequest()
                    .withCommand(List.of("echo", "hello"))
                    .withChunked(false);
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"command\""));
            assertTrue(json.contains("\"echo\""));
            assertTrue(json.contains("\"hello\""));
            assertFalse(json.contains("\"chunked\""));
        }

        @Test
        void jsonDeserialization() throws Exception {
            String json = "{\"command\":[\"python\",\"script.py\"],\"chunked\":true}";
            ExecCommandRequest req = JsonUtils.MAPPER.readValue(json, ExecCommandRequest.class);
            assertEquals(2, req.getCommand().size());
            assertEquals("python", req.getCommand().get(0));
            assertFalse(req.isChunked(),
                    "chunked is a protocol header and must be ignored in JSON");
        }

        @Test
        void equalityAndHashCode() {
            ExecCommandRequest a = new ExecCommandRequest()
                    .withCommand(List.of("ls")).withChunked(true);
            ExecCommandRequest b = new ExecCommandRequest()
                    .withCommand(List.of("ls")).withChunked(true);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void toStringRedactsCommand() {
            ExecCommandRequest req = new ExecCommandRequest()
                    .withCommand(List.of("command", "unit-sensitive-marker"));
            assertFalse(req.toString().contains("unit-sensitive-marker"));
            assertTrue(req.toString().contains("arguments=2"));
        }
    }

    // ============================================================
    // UploadFilesRequest
    // ============================================================

    @Nested
    class UploadFilesRequestTests {

        @Test
        void defaultValues() {
            UploadFilesRequest req = new UploadFilesRequest();
            assertNull(req.getFiles());
            assertNull(req.getPath());
            assertNull(req.getFileUserId());
            assertNull(req.getFileGroupId());
            assertNull(req.getFileMode());
        }

        @Test
        void fluentApi() {
            UploadFilesRequest req = new UploadFilesRequest()
                    .withPath("/tmp/test")
                    .withFileUserId(1000)
                    .withFileGroupId(1000)
                    .withFileMode("0644");

            assertEquals("/tmp/test", req.getPath());
            assertEquals(1000, req.getFileUserId());
            assertEquals(1000, req.getFileGroupId());
            assertEquals("0644", req.getFileMode());
        }

        @Test
        void jsonDeserialization() throws Exception {
            String json = "{\"path\":\"/uploads\",\"file_user_id\":1000,"
                    + "\"file_group_id\":1000,\"file_mode\":\"0755\","
                    + "\"files\":[{\"name\":\"test.txt\",\"content\":\"aGVsbG8=\"}]}";
            UploadFilesRequest req = JsonUtils.MAPPER.readValue(json, UploadFilesRequest.class);
            assertEquals("/uploads", req.getPath());
            assertEquals(1000, req.getFileUserId());
            assertEquals(1000, req.getFileGroupId());
            assertEquals("0755", req.getFileMode());
            assertEquals(1, req.getFiles().size());
        }

        @Test
        void equalityAndHashCode() {
            UploadFilesRequest a = new UploadFilesRequest()
                    .withPath("/tmp").withFileMode("0644");
            UploadFilesRequest b = new UploadFilesRequest()
                    .withPath("/tmp").withFileMode("0644");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void toStringContainsPath() {
            UploadFilesRequest req = new UploadFilesRequest()
                    .withPath("/test/path")
                    .withFiles(List.of(Map.of("content", "unit-sensitive-marker")));
            assertTrue(req.toString().contains("/test/path"));
            assertFalse(req.toString().contains("unit-sensitive-marker"));
            assertTrue(req.toString().contains("count=1"));
        }
    }

    // ============================================================
    // StopSessionRequest
    // ============================================================

    @Nested
    class StopSessionRequestTests {

        @Test
        void fluentApi() {
            StopSessionRequest req = new StopSessionRequest()
                    .withSessionId("session-123");
            assertEquals("session-123", req.getSessionId());
        }

        @Test
        void jsonSerialization() {
            StopSessionRequest req = new StopSessionRequest()
                    .withSessionId("s-456");
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"session_id\":\"s-456\""));
        }

        @Test
        void jsonDeserialization() throws Exception {
            String json = "{\"session_id\":\"s-789\"}";
            StopSessionRequest req = JsonUtils.MAPPER.readValue(json, StopSessionRequest.class);
            assertEquals("s-789", req.getSessionId());
        }

        @Test
        void equalityAndHashCode() {
            StopSessionRequest a = new StopSessionRequest().withSessionId("s1");
            StopSessionRequest b = new StopSessionRequest().withSessionId("s1");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void inequality() {
            StopSessionRequest a = new StopSessionRequest().withSessionId("s1");
            StopSessionRequest b = new StopSessionRequest().withSessionId("s2");
            assertNotEquals(a, b);
        }

        @Test
        void toStringContainsSessionId() {
            StopSessionRequest req = new StopSessionRequest().withSessionId("s-test");
            assertTrue(req.toString().contains("s-test"));
        }

        @Test
        void nullSessionId() {
            StopSessionRequest req = new StopSessionRequest();
            assertNull(req.getSessionId());
            // Serialization should exclude null
            String json = JsonUtils.toJson(req);
            assertFalse(json.contains("session_id"));
        }
    }

    // ============================================================
    // AgentInfo (response model)
    // ============================================================

    @Nested
    class AgentInfoTests {

        @Test
        void deserializesFromJson() throws Exception {
            String json = "{\"id\":\"agent-123\",\"name\":\"test-agent\","
                    + "\"description\":\"A test agent\",\"status\":\"running\","
                    + "\"created_at\":\"2025-01-01T00:00:00Z\","
                    + "\"updated_at\":\"2025-01-02T00:00:00Z\","
                    + "\"execution_agency_name\":\"agency\","
                    + "\"agent_gateway_id\":\"gw-1\","
                    + "\"artifact_source\":{\"type\":\"swr\"},"
                    + "\"invoke_config\":{\"protocol\":\"HTTP\"}}";
            AgentInfo info = JsonUtils.MAPPER.readValue(json, AgentInfo.class);
            assertEquals("agent-123", info.getId());
            assertEquals("test-agent", info.getName());
            assertEquals("A test agent", info.getDescription());
            assertEquals("running", info.getStatus());
            assertEquals("2025-01-01T00:00:00Z", info.getCreatedAt());
            assertEquals("2025-01-02T00:00:00Z", info.getUpdatedAt());
            assertEquals("agency", info.getExecutionAgencyName());
            assertEquals("gw-1", info.getAgentGatewayId());
            assertEquals("swr", info.getArtifactSource().get("type"));
            assertEquals("HTTP", info.getInvokeConfig().get("protocol"));
        }

        @Test
        void ignoresUnknownFields() throws Exception {
            String json = "{\"id\":\"a1\",\"name\":\"test\",\"extra\":\"ignored\"}";
            AgentInfo info = JsonUtils.MAPPER.readValue(json, AgentInfo.class);
            assertEquals("a1", info.getId());
        }

        @Test
        void nullFieldsWhenMissing() throws Exception {
            String json = "{\"id\":\"a1\"}";
            AgentInfo info = JsonUtils.MAPPER.readValue(json, AgentInfo.class);
            assertEquals("a1", info.getId());
            assertNull(info.getName());
            assertNull(info.getDescription());
            assertNull(info.getStatus());
        }

        @Test
        void deserializesWithEnvVarsAndTags() throws Exception {
            String json = "{\"id\":\"a1\","
                    + "\"environment_variables\":[{\"key\":\"K\",\"value\":\"V\"}],"
                    + "\"tags\":[{\"key\":\"env\",\"value\":\"prod\"}]}";
            AgentInfo info = JsonUtils.MAPPER.readValue(json, AgentInfo.class);
            assertNotNull(info.getEnvironmentVariables());
            assertEquals(1, info.getEnvironmentVariables().size());
            assertEquals("K", info.getEnvironmentVariables().get(0).get("key"));
            assertNotNull(info.getTags());
            assertEquals("env", info.getTags().get(0).get("key"));
        }
    }

    // ============================================================
    // AgentListResponse (response model)
    // ============================================================

    @Nested
    class AgentListResponseTests {

        @Test
        void deserializesFromJson() throws Exception {
            String json = "{\"items\":[{\"id\":\"a1\",\"name\":\"agent1\"}],"
                    + "\"total\":1,\"limit\":10,\"offset\":0}";
            AgentListResponse resp = JsonUtils.MAPPER.readValue(json, AgentListResponse.class);
            assertEquals(1, resp.getTotal());
            assertEquals(10, resp.getLimit());
            assertEquals(0, resp.getOffset());
            assertNotNull(resp.getItems());
            assertEquals(1, resp.getItems().size());
            assertEquals("agent1", resp.getItems().get(0).getName());
        }

        @Test
        void emptyItems() throws Exception {
            String json = "{\"items\":[],\"total\":0,\"limit\":10,\"offset\":0}";
            AgentListResponse resp = JsonUtils.MAPPER.readValue(json, AgentListResponse.class);
            assertEquals(0, resp.getTotal());
            assertTrue(resp.getItems().isEmpty());
        }

        @Test
        void ignoresUnknownFields() throws Exception {
            String json = "{\"items\":[],\"total\":0,\"extra\":\"ignored\"}";
            AgentListResponse resp = JsonUtils.MAPPER.readValue(json, AgentListResponse.class);
            assertEquals(0, resp.getTotal());
        }
    }

    // ============================================================
    // AgentEndpointInfo (response model)
    // ============================================================

    @Nested
    class AgentEndpointInfoTests {

        @Test
        void deserializesFromJson() throws Exception {
            String json = "{\"id\":\"ep-123\",\"name\":\"my-endpoint\","
                    + "\"agent_id\":\"a-456\",\"endpoint_type\":\"default\","
                    + "\"target_version_name\":\"v1\","
                    + "\"config\":{\"timeout\":30},"
                    + "\"created_at\":\"2025-01-01T00:00:00Z\","
                    + "\"updated_at\":\"2025-01-02T00:00:00Z\"}";
            AgentEndpointInfo info = JsonUtils.MAPPER.readValue(json, AgentEndpointInfo.class);
            assertEquals("ep-123", info.getId());
            assertEquals("my-endpoint", info.getName());
            assertEquals("a-456", info.getAgentId());
            assertEquals("default", info.getEndpointType());
            assertEquals("v1", info.getTargetVersionName());
            assertEquals(30, info.getConfig().get("timeout"));
            assertEquals("2025-01-01T00:00:00Z", info.getCreatedAt());
        }
    }
}
