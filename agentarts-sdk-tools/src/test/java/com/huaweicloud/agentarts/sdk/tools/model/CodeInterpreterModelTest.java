package com.huaweicloud.agentarts.sdk.tools.model;

import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all code interpreter model classes.
 */
class CodeInterpreterModelTest {

    // ============================================================
    // CreateCodeInterpreterRequest
    // ============================================================

    @Nested
    class CreateCodeInterpreterRequestTests {

        @Test
        void defaultValues() {
            CreateCodeInterpreterRequest req = new CreateCodeInterpreterRequest();
            assertNull(req.getName());
            assertNull(req.getAuthType());
            assertNull(req.getApiKeyName());
            assertNull(req.getDescription());
            assertNull(req.getExecutionAgencyName());
            assertNull(req.getObservability());
            assertNull(req.getNetworkConfig());
            assertNull(req.getAgentGatewayId());
            assertNull(req.getTags());
        }

        @Test
        void fluentApi() {
            CreateCodeInterpreterRequest req = new CreateCodeInterpreterRequest()
                    .withName("my-interpreter")
                    .withAuthType("API_KEY")
                    .withApiKeyName("my-key")
                    .withDescription("A test interpreter")
                    .withExecutionAgencyName("agency")
                    .withAgentGatewayId("gw-1");

            assertEquals("my-interpreter", req.getName());
            assertEquals("API_KEY", req.getAuthType());
            assertEquals("my-key", req.getApiKeyName());
            assertEquals("A test interpreter", req.getDescription());
            assertEquals("agency", req.getExecutionAgencyName());
            assertEquals("gw-1", req.getAgentGatewayId());
        }

        @Test
        void jsonSerialization() {
            CreateCodeInterpreterRequest req = new CreateCodeInterpreterRequest()
                    .withName("test-ci")
                    .withAuthType("API_KEY")
                    .withApiKeyName("key1");
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"test-ci\""));
            assertTrue(json.contains("\"auth_type\":\"API_KEY\""));
            assertTrue(json.contains("\"api_key_name\":\"key1\""));
        }

        @Test
        void jsonSerializationExcludesNulls() {
            CreateCodeInterpreterRequest req = new CreateCodeInterpreterRequest()
                    .withName("test");
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"test\""));
            assertFalse(json.contains("\"auth_type\""));
            assertFalse(json.contains("\"description\""));
        }

        @Test
        void jsonDeserialization() throws Exception {
            String json = "{\"name\":\"from-json\",\"auth_type\":\"IAM\","
                    + "\"description\":\"test\",\"execution_agency_name\":\"agency\","
                    + "\"agent_gateway_id\":\"gw-1\"}";
            CreateCodeInterpreterRequest req = JsonUtils.MAPPER.readValue(json, CreateCodeInterpreterRequest.class);
            assertEquals("from-json", req.getName());
            assertEquals("IAM", req.getAuthType());
            assertEquals("test", req.getDescription());
            assertEquals("agency", req.getExecutionAgencyName());
            assertEquals("gw-1", req.getAgentGatewayId());
        }

        @Test
        void equalityAndHashCode() {
            CreateCodeInterpreterRequest a = new CreateCodeInterpreterRequest()
                    .withName("test").withAuthType("API_KEY").withDescription("desc");
            CreateCodeInterpreterRequest b = new CreateCodeInterpreterRequest()
                    .withName("test").withAuthType("API_KEY").withDescription("desc");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void toStringContainsName() {
            CreateCodeInterpreterRequest req = new CreateCodeInterpreterRequest()
                    .withName("my-ci");
            assertTrue(req.toString().contains("my-ci"));
        }
    }

    // ============================================================
    // StartCodeInterpreterSessionRequest
    // ============================================================

    @Nested
    class StartCodeInterpreterSessionRequestTests {

        @Test
        void fluentApi() {
            StartCodeInterpreterSessionRequest req = new StartCodeInterpreterSessionRequest()
                    .withName("my-session")
                    .withSessionTimeout(900);
            assertEquals("my-session", req.getName());
            assertEquals(900, req.getSessionTimeout());
        }

        @Test
        void jsonSerialization() {
            StartCodeInterpreterSessionRequest req = new StartCodeInterpreterSessionRequest()
                    .withName("session-1")
                    .withSessionTimeout(600);
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"session-1\""));
            assertTrue(json.contains("\"session_timeout\":600"));
        }

        @Test
        void jsonDeserialization() throws Exception {
            String json = "{\"name\":\"s1\",\"session_timeout\":1800}";
            StartCodeInterpreterSessionRequest req = JsonUtils.MAPPER.readValue(json, StartCodeInterpreterSessionRequest.class);
            assertEquals("s1", req.getName());
            assertEquals(1800, req.getSessionTimeout());
        }

        @Test
        void equalityAndHashCode() {
            StartCodeInterpreterSessionRequest a = new StartCodeInterpreterSessionRequest()
                    .withName("s").withSessionTimeout(900);
            StartCodeInterpreterSessionRequest b = new StartCodeInterpreterSessionRequest()
                    .withName("s").withSessionTimeout(900);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    // ============================================================
    // CodeInterpreterInvokeRequest
    // ============================================================

    @Nested
    class CodeInterpreterInvokeRequestTests {

        @Test
        void fluentApi() {
            CodeInterpreterInvokeRequest req = new CodeInterpreterInvokeRequest()
                    .withOperateType("execute_code")
                    .withArguments(Map.of("code", "print('hello')", "language", "python"));
            assertEquals("execute_code", req.getOperateType());
            assertEquals("print('hello')", req.getArguments().get("code"));
        }

        @Test
        void jsonSerialization() {
            CodeInterpreterInvokeRequest req = new CodeInterpreterInvokeRequest()
                    .withOperateType("execute_command")
                    .withArguments(Map.of("command", "ls -la"));
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"operate_type\":\"execute_command\""));
            assertTrue(json.contains("\"command\""));
        }

        @Test
        void equalityAndHashCode() {
            CodeInterpreterInvokeRequest a = new CodeInterpreterInvokeRequest()
                    .withOperateType("execute_code");
            CodeInterpreterInvokeRequest b = new CodeInterpreterInvokeRequest()
                    .withOperateType("execute_code");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    // ============================================================
    // UpdateCodeInterpreterRequest
    // ============================================================

    @Nested
    class UpdateCodeInterpreterRequestTests {

        @Test
        void fluentApi() {
            UpdateCodeInterpreterRequest req = new UpdateCodeInterpreterRequest()
                    .withObservability(Map.of("tracing", true))
                    .withTags(List.of(Map.of("key", "env", "value", "prod")));
            assertNotNull(req.getObservability());
            assertEquals(1, req.getTags().size());
        }

        @Test
        void jsonSerialization() {
            UpdateCodeInterpreterRequest req = new UpdateCodeInterpreterRequest()
                    .withObservability(Map.of("enabled", true));
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"observability\""));
            assertTrue(json.contains("\"enabled\":true"));
        }

        @Test
        void equalityAndHashCode() {
            UpdateCodeInterpreterRequest a = new UpdateCodeInterpreterRequest()
                    .withObservability(Map.of("k", "v"));
            UpdateCodeInterpreterRequest b = new UpdateCodeInterpreterRequest()
                    .withObservability(Map.of("k", "v"));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    // ============================================================
    // CodeInterpreterInfo (response model)
    // ============================================================

    @Nested
    class CodeInterpreterInfoTests {

        @Test
        void deserializesFromJson() throws Exception {
            String json = "{\"id\":\"ci-123\",\"name\":\"my-interpreter\","
                    + "\"description\":\"test\",\"auth_type\":\"API_KEY\","
                    + "\"api_key_name\":\"key1\",\"created_at\":\"2025-01-01\","
                    + "\"updated_at\":\"2025-01-02\","
                    + "\"execution_agency_name\":\"agency\","
                    + "\"agent_gateway_id\":\"gw-1\","
                    + "\"access_endpoint\":\"https://ci.example.com\"}";
            CodeInterpreterInfo info = JsonUtils.MAPPER.readValue(json, CodeInterpreterInfo.class);
            assertEquals("ci-123", info.getId());
            assertEquals("my-interpreter", info.getName());
            assertEquals("test", info.getDescription());
            assertEquals("API_KEY", info.getAuthType());
            assertEquals("key1", info.getApiKeyName());
            assertEquals("agency", info.getExecutionAgencyName());
            assertEquals("gw-1", info.getAgentGatewayId());
            assertEquals("https://ci.example.com", info.getAccessEndpoint());
        }

        @Test
        void ignoresUnknownFields() throws Exception {
            String json = "{\"id\":\"ci-1\",\"extra\":\"ignored\"}";
            CodeInterpreterInfo info = JsonUtils.MAPPER.readValue(json, CodeInterpreterInfo.class);
            assertEquals("ci-1", info.getId());
        }

        @Test
        void nullFieldsWhenMissing() throws Exception {
            String json = "{\"id\":\"ci-1\"}";
            CodeInterpreterInfo info = JsonUtils.MAPPER.readValue(json, CodeInterpreterInfo.class);
            assertEquals("ci-1", info.getId());
            assertNull(info.getName());
            assertNull(info.getDescription());
        }
    }

    // ============================================================
    // CodeInterpreterListResponse (response model)
    // ============================================================

    @Nested
    class CodeInterpreterListResponseTests {

        @Test
        void deserializesFromJson() throws Exception {
            String json = "{\"items\":[{\"id\":\"ci-1\",\"name\":\"interp1\"}],"
                    + "\"total_count\":1}";
            CodeInterpreterListResponse resp = JsonUtils.MAPPER.readValue(json, CodeInterpreterListResponse.class);
            assertEquals(1, resp.getTotalCount());
            assertNotNull(resp.getItems());
            assertEquals(1, resp.getItems().size());
            assertEquals("interp1", resp.getItems().get(0).getName());
        }

        @Test
        void itemsAreListItemNotFullInfo() throws Exception {
            // The list API returns a reduced field set; items must not expose
            // create-only fields like authType / networkConfig.
            String json = "{\"items\":[{\"id\":\"ci-1\",\"name\":\"interp1\","
                    + "\"auth_type\":\"API_KEY\",\"network_config\":{}}],"
                    + "\"total_count\":1}";
            CodeInterpreterListResponse resp = JsonUtils.MAPPER.readValue(json, CodeInterpreterListResponse.class);
            assertEquals(1, resp.getItems().size());
            assertEquals(CodeInterpreterListItem.class, resp.getItems().get(0).getClass());
            assertNotEquals(CodeInterpreterInfo.class, resp.getItems().get(0).getClass());
        }

        @Test
        void emptyList() throws Exception {
            String json = "{\"items\":[],\"total_count\":0}";
            CodeInterpreterListResponse resp = JsonUtils.MAPPER.readValue(json, CodeInterpreterListResponse.class);
            assertEquals(0, resp.getTotalCount());
            assertTrue(resp.getItems().isEmpty());
        }
    }

    // ============================================================
    // CodeInterpreterSessionInfo (response model)
    // ============================================================

    @Nested
    class CodeInterpreterSessionInfoTests {

        @Test
        void deserializesFromJson() throws Exception {
            String json = "{\"code_interpreter_id\":\"ci-123\","
                    + "\"session_id\":\"sess-456\","
                    + "\"session_name\":\"my-session\","
                    + "\"session_timeout\":900,"
                    + "\"created_at\":\"2025-01-01T00:00:00Z\"}";
            CodeInterpreterSessionInfo info = JsonUtils.MAPPER.readValue(json, CodeInterpreterSessionInfo.class);
            assertEquals("ci-123", info.getCodeInterpreterId());
            assertEquals("sess-456", info.getSessionId());
            assertEquals("my-session", info.getSessionName());
            assertEquals(900, info.getSessionTimeout());
            assertEquals("2025-01-01T00:00:00Z", info.getCreatedAt());
        }
    }
}
