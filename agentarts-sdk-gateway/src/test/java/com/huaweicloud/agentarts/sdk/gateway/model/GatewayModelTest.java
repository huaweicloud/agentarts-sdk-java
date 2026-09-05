package com.huaweicloud.agentarts.sdk.gateway.model;

import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all Gateway model classes.
 */
class GatewayModelTest {

    // ============================================================
    // CreateGatewayRequest
    // ============================================================

    @Nested
    class CreateGatewayRequestTests {

        @Test
        void defaultValues() {
            CreateGatewayRequest req = new CreateGatewayRequest();
            assertNull(req.getName());
            assertNull(req.getDescription());
            assertNull(req.getProtocolType());
            assertNull(req.getAuthorizerType());
            assertNull(req.getAgencyName());
            assertNull(req.getAuthorizerConfiguration());
            assertNull(req.getProtocolConfiguration());
            assertNull(req.getLogDeliveryConfiguration());
            assertNull(req.getOutboundNetworkConfiguration());
            assertNull(req.getTags());
        }

        @Test
        void fluentApi() {
            CreateGatewayRequest req = new CreateGatewayRequest()
                    .withName("my-gateway")
                    .withDescription("Test gateway")
                    .withProtocolType("mcp")
                    .withAuthorizerType("iam")
                    .withAgencyName("my-agency")
                    .withAuthorizerConfiguration(Map.of("type", "iam"))
                    .withProtocolConfiguration(Map.of("version", "2025-06-18"))
                    .withLogDeliveryConfiguration(Map.of("enabled", true))
                    .withOutboundNetworkConfiguration(Map.of("network_mode", "public"))
                    .withTags(List.of(Map.of("key", "env", "value", "test")));

            assertEquals("my-gateway", req.getName());
            assertEquals("Test gateway", req.getDescription());
            assertEquals("mcp", req.getProtocolType());
            assertEquals("iam", req.getAuthorizerType());
            assertEquals("my-agency", req.getAgencyName());
            assertEquals("iam", req.getAuthorizerConfiguration().get("type"));
            assertEquals("2025-06-18", req.getProtocolConfiguration().get("version"));
            assertEquals(true, req.getLogDeliveryConfiguration().get("enabled"));
            assertEquals("public", req.getOutboundNetworkConfiguration().get("network_mode"));
            assertEquals("env", req.getTags().get(0).get("key"));
        }

        @Test
        void jsonSerialization() {
            CreateGatewayRequest req = new CreateGatewayRequest()
                    .withName("gw-1")
                    .withProtocolType("mcp")
                    .withAuthorizerType("iam")
                    .withProtocolConfiguration(Map.of("version", "1"))
                    .withTags(List.of(Map.of("key", "env", "value", "test")));
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"gw-1\""));
            assertTrue(json.contains("\"protocol_type\":\"mcp\""));
            assertTrue(json.contains("\"authorizer_type\":\"iam\""));
            assertTrue(json.contains("\"protocol_configuration\""));
            assertTrue(json.contains("\"tags\""));
        }

        @Test
        void jsonSerializationExcludesNulls() {
            CreateGatewayRequest req = new CreateGatewayRequest()
                    .withName("gw-1");
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"gw-1\""));
            assertFalse(json.contains("\"description\""));
            assertFalse(json.contains("\"agency_name\""));
        }

        @Test
        void jsonDeserialization() throws Exception {
            String json = "{\"name\":\"from-json\",\"description\":\"desc\","
                    + "\"protocol_type\":\"mcp\",\"authorizer_type\":\"iam\","
                    + "\"agency_name\":\"agency\",\"protocol_configuration\":{\"version\":\"1\"},"
                    + "\"tags\":[{\"key\":\"env\",\"value\":\"test\"}]}";
            CreateGatewayRequest req = JsonUtils.MAPPER.readValue(json, CreateGatewayRequest.class);
            assertEquals("from-json", req.getName());
            assertEquals("desc", req.getDescription());
            assertEquals("mcp", req.getProtocolType());
            assertEquals("iam", req.getAuthorizerType());
            assertEquals("agency", req.getAgencyName());
            assertEquals("1", req.getProtocolConfiguration().get("version"));
            assertEquals("env", req.getTags().get(0).get("key"));
        }

        @Test
        void equalityAndHashCode() {
            CreateGatewayRequest a = new CreateGatewayRequest()
                    .withName("gw").withDescription("d").withProtocolType("mcp")
                    .withAuthorizerType("iam").withAgencyName("a");
            CreateGatewayRequest b = new CreateGatewayRequest()
                    .withName("gw").withDescription("d").withProtocolType("mcp")
                    .withAuthorizerType("iam").withAgencyName("a");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void inequality() {
            CreateGatewayRequest a = new CreateGatewayRequest().withName("gw1");
            CreateGatewayRequest b = new CreateGatewayRequest().withName("gw2");
            assertNotEquals(a, b);
        }

        @Test
        void toStringContainsName() {
            CreateGatewayRequest req = new CreateGatewayRequest().withName("my-gw");
            assertTrue(req.toString().contains("my-gw"));
        }

        @Test
        void standardSetters() {
            CreateGatewayRequest req = new CreateGatewayRequest();
            req.setName("gw");
            req.setDescription("desc");
            req.setProtocolType("mcp");
            req.setAuthorizerType("iam");
            req.setAgencyName("agency");

            assertEquals("gw", req.getName());
            assertEquals("desc", req.getDescription());
            assertEquals("mcp", req.getProtocolType());
            assertEquals("iam", req.getAuthorizerType());
            assertEquals("agency", req.getAgencyName());
        }
    }

    // ============================================================
    // UpdateGatewayRequest
    // ============================================================

    @Nested
    class UpdateGatewayRequestTests {

        @Test
        void fluentApi() {
            UpdateGatewayRequest req = new UpdateGatewayRequest()
                    .withDescription("Updated description")
                    .withProtocolConfiguration(Map.of("version", "1"))
                    .withLogDeliveryConfiguration(Map.of("enabled", true))
                    .withTags(List.of(Map.of("key", "env", "value", "prod")));
            assertEquals("Updated description", req.getDescription());
            assertEquals("1", req.getProtocolConfiguration().get("version"));
            assertEquals(true, req.getLogDeliveryConfiguration().get("enabled"));
            assertEquals("prod", req.getTags().get(0).get("value"));
        }

        @Test
        void jsonSerialization() {
            UpdateGatewayRequest req = new UpdateGatewayRequest()
                    .withDescription("Updated")
                    .withProtocolConfiguration(Map.of("version", "1"))
                    .withLogDeliveryConfiguration(Map.of("enabled", false))
                    .withTags(List.of(Map.of("key", "env", "value", "prod")));
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"description\":\"Updated\""));
            assertTrue(json.contains("\"protocol_configuration\""));
            assertTrue(json.contains("\"log_delivery_configuration\""));
            assertTrue(json.contains("\"tags\""));
        }

        @Test
        void jsonDeserialization() throws Exception {
            String json = "{\"description\":\"new desc\",\"protocol_configuration\":{\"version\":\"1\"},"
                    + "\"log_delivery_configuration\":{\"enabled\":true},"
                    + "\"tags\":[{\"key\":\"env\",\"value\":\"prod\"}]}";
            UpdateGatewayRequest req = JsonUtils.MAPPER.readValue(json, UpdateGatewayRequest.class);
            assertEquals("new desc", req.getDescription());
            assertEquals("1", req.getProtocolConfiguration().get("version"));
            assertEquals(true, req.getLogDeliveryConfiguration().get("enabled"));
            assertEquals("prod", req.getTags().get(0).get("value"));
        }

        @Test
        void equalityAndHashCode() {
            UpdateGatewayRequest a = new UpdateGatewayRequest().withDescription("d");
            UpdateGatewayRequest b = new UpdateGatewayRequest().withDescription("d");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    // ============================================================
    // CreateGatewayTargetRequest
    // ============================================================

    @Nested
    class CreateGatewayTargetRequestTests {

        @Test
        void defaultValues() {
            CreateGatewayTargetRequest req = new CreateGatewayTargetRequest();
            assertNull(req.getName());
            assertNull(req.getDescription());
            assertNull(req.getTargetConfiguration());
            assertNull(req.getCredentialProviderConfiguration());
        }

        @Test
        void fluentApi() {
            CreateGatewayTargetRequest req = new CreateGatewayTargetRequest()
                    .withName("my-target")
                    .withDescription("Test target")
                    .withTargetConfiguration(Map.of("url", "https://target.example.com"))
                    .withCredentialProviderConfiguration(Map.of("credential_provider_type", "none"));

            assertEquals("my-target", req.getName());
            assertEquals("Test target", req.getDescription());
            assertEquals("https://target.example.com", req.getTargetConfiguration().get("url"));
            assertEquals("none", req.getCredentialProviderConfiguration().get("credential_provider_type"));
        }

        @Test
        void jsonSerialization() {
            CreateGatewayTargetRequest req = new CreateGatewayTargetRequest()
                    .withName("target-1")
                    .withTargetConfiguration(Map.of("url", "https://example.com"));
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"target-1\""));
            assertTrue(json.contains("\"target_configuration\""));
            assertTrue(json.contains("\"url\":\"https://example.com\""));
        }

        @Test
        void jsonSerializationExcludesNulls() {
            CreateGatewayTargetRequest req = new CreateGatewayTargetRequest()
                    .withName("t1");
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"t1\""));
            assertFalse(json.contains("\"description\""));
            assertFalse(json.contains("\"target_configuration\""));
        }

        @Test
        void equalityAndHashCode() {
            CreateGatewayTargetRequest a = new CreateGatewayTargetRequest()
                    .withName("t").withDescription("d");
            CreateGatewayTargetRequest b = new CreateGatewayTargetRequest()
                    .withName("t").withDescription("d");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void toStringRedactsTargetAndCredentialConfiguration() {
            String marker = "unit-sensitive-marker";
            CreateGatewayTargetRequest req = new CreateGatewayTargetRequest()
                    .withTargetConfiguration(Map.of("authorization", marker))
                    .withCredentialProviderConfiguration(Map.of("api_key", marker));

            assertFalse(req.toString().contains(marker));
            assertTrue(req.toString().contains("[REDACTED]"));
        }
    }

    // ============================================================
    // UpdateGatewayTargetRequest
    // ============================================================

    @Nested
    class UpdateGatewayTargetRequestTests {

        @Test
        void fluentApi() {
            UpdateGatewayTargetRequest req = new UpdateGatewayTargetRequest()
                    .withName("updated-target")
                    .withDescription("Updated")
                    .withTargetConfiguration(Map.of("url", "https://new.example.com"));

            assertEquals("updated-target", req.getName());
            assertEquals("Updated", req.getDescription());
            assertEquals("https://new.example.com", req.getTargetConfiguration().get("url"));
        }

        @Test
        void jsonSerialization() {
            UpdateGatewayTargetRequest req = new UpdateGatewayTargetRequest()
                    .withName("t1")
                    .withCredentialProviderConfiguration(Map.of("type", "api_key"));
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"t1\""));
            assertTrue(json.contains("\"credential_provider_configuration\""));
        }

        @Test
        void jsonDeserialization() throws Exception {
            String json = "{\"name\":\"t1\",\"description\":\"d\","
                    + "\"target_configuration\":{\"url\":\"https://example.com\"},"
                    + "\"credential_provider_configuration\":{\"type\":\"none\"}}";
            UpdateGatewayTargetRequest req = JsonUtils.MAPPER.readValue(json, UpdateGatewayTargetRequest.class);
            assertEquals("t1", req.getName());
            assertEquals("d", req.getDescription());
            assertNotNull(req.getTargetConfiguration());
            assertNotNull(req.getCredentialProviderConfiguration());
        }

        @Test
        void equalityAndHashCode() {
            UpdateGatewayTargetRequest a = new UpdateGatewayTargetRequest()
                    .withName("t").withDescription("d");
            UpdateGatewayTargetRequest b = new UpdateGatewayTargetRequest()
                    .withName("t").withDescription("d");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void toStringRedactsTargetAndCredentialConfiguration() {
            String marker = "unit-sensitive-marker";
            UpdateGatewayTargetRequest req = new UpdateGatewayTargetRequest()
                    .withTargetConfiguration(Map.of("authorization", marker))
                    .withCredentialProviderConfiguration(Map.of("api_key", marker));

            assertFalse(req.toString().contains(marker));
            assertTrue(req.toString().contains("[REDACTED]"));
        }
    }
}
