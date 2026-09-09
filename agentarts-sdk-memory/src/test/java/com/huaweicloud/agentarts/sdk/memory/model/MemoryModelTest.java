package com.huaweicloud.agentarts.sdk.memory.model;

import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all memory model classes, focusing on
 * message serialization (toDict), filter serialization, request models,
 * and response model deserialization.
 */
class MemoryModelTest {

    @Nested
    class PythonExportParityTests {

        @Test
        void assetAndDataModelsMatchWireShape() {
            AssetRef asset = new AssetRef()
                    .withAssetId("asset-id")
                    .withUri("https://example.invalid/object")
                    .withMime("application/octet-stream")
                    .withSize(12)
                    .withFilename("object.bin")
                    .withMeta(Map.of("source", "test"));
            assertEquals("asset-id", asset.toDict().get("asset_id"));
            assertEquals(12L, asset.toDict().get("size"));

            DataMessage data = new DataMessage()
                    .withKind("summary")
                    .withCovers(List.of("message-id"))
                    .withContent(Map.of("summary", "example"));
            assertEquals("data", data.toDict().get("type"));
            assertEquals("summary", data.toDict().get("kind"));
        }

        @Test
        void messageRequestValidatesPartCountAndSerializesMetadata() {
            MessageRequest request = new MessageRequest(
                            "user",
                            List.of(Map.of("type", "text", "text", "hello")))
                    .withActorId("actor-id")
                    .withMeta(Map.of("tenant", "example"));

            assertEquals("actor-id", request.toDict().get("actor_id"));
            assertEquals(Map.of("tenant", "example"), request.toDict().get("meta"));
            assertThrows(IllegalArgumentException.class, () -> request.withParts(List.of()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> request.withParts(List.of(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));
        }

        @Test
        void exportedResponseModelsDeserialize() throws Exception {
            SessionListResponse sessions = JsonUtils.MAPPER.readValue(
                    "{\"items\":[{\"id\":\"session-id\"}],\"total\":1}",
                    SessionListResponse.class);
            assertEquals(1, sessions.getTotal());
            assertEquals(20, sessions.getLimit());

            ContextChainResponse chain = JsonUtils.MAPPER.readValue(
                    "{\"messages\":[{\"id\":\"message-id\"}],\"total_token_count\":42,\"compressed\":true}",
                    ContextChainResponse.class);
            assertEquals(42, chain.getTotalTokenCount());
            assertTrue(chain.isCompressed());

            ContextCompressionResponse compression = JsonUtils.MAPPER.readValue(
                    "{\"compression_id\":\"compression-id\",\"compressed_messages\":[],"
                            + "\"compression_ratio\":0.5,\"original_token_count\":100,"
                            + "\"compressed_token_count\":50}",
                    ContextCompressionResponse.class);
            assertEquals("compression-id", compression.getCompressionId());
            assertEquals(0.5, compression.getCompressionRatio());
            assertEquals(50, compression.getCompressedTokenCount());
        }
    }

    // ============================================================
    // TextMessage
    // ============================================================

    @Nested
    class TextMessageTests {

        @Test
        void defaultValues() {
            TextMessage msg = new TextMessage();
            assertEquals("user", msg.getRole());
            assertEquals("", msg.getContent());
            assertNull(msg.getActorId());
            assertNull(msg.getAssistantId());
            assertNull(msg.getMeta());
        }

        @Test
        void twoArgConstructor() {
            TextMessage msg = new TextMessage("assistant", "Hello!");
            assertEquals("assistant", msg.getRole());
            assertEquals("Hello!", msg.getContent());
        }

        @Test
        void toDictBasicFormat() {
            TextMessage msg = new TextMessage("user", "Hello");
            Map<String, Object> dict = msg.toDict();

            assertEquals("user", dict.get("role"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) dict.get("parts");
            assertNotNull(parts);
            assertEquals(1, parts.size());
            assertEquals("text", parts.get(0).get("type"));
            assertEquals("Hello", parts.get(0).get("text"));
        }

        @Test
        void toDictMatchesPythonFormat() {
            // Python: {"role": "user", "parts": [{"type": "text", "text": "Hello"}]}
            TextMessage msg = new TextMessage("user", "Hello");
            Map<String, Object> dict = msg.toDict();

            assertTrue(dict.containsKey("role"));
            assertTrue(dict.containsKey("parts"));
            assertFalse(dict.containsKey("content")); // content is NOT in toDict output
        }

        @Test
        void toDictWithAssistantRole() {
            TextMessage msg = new TextMessage("assistant", "Response");
            Map<String, Object> dict = msg.toDict();
            assertEquals("assistant", dict.get("role"));
        }

        @Test
        void toDictWithSystemRole() {
            TextMessage msg = new TextMessage("system", "System prompt");
            Map<String, Object> dict = msg.toDict();
            assertEquals("system", dict.get("role"));
        }

        @Test
        void toDictIncludesMetaWhenSet() {
            TextMessage msg = new TextMessage("user", "Hello");
            msg.setMeta("{\"key\":\"value\"}");
            Map<String, Object> dict = msg.toDict();
            assertEquals("{\"key\":\"value\"}", dict.get("meta"));
        }

        @Test
        void toDictExcludesMetaWhenNull() {
            TextMessage msg = new TextMessage("user", "Hello");
            Map<String, Object> dict = msg.toDict();
            assertFalse(dict.containsKey("meta"));
        }

        @Test
        void toDictThrowsOnEmptyContent() {
            TextMessage msg = new TextMessage("user", "");
            assertThrows(IllegalArgumentException.class, msg::toDict);
        }

        @Test
        void toDictThrowsOnNullContent() {
            TextMessage msg = new TextMessage();
            msg.setContent(null);
            assertThrows(IllegalArgumentException.class, msg::toDict);
        }

        @Test
        void settersWork() {
            TextMessage msg = new TextMessage();
            msg.setRole("assistant");
            msg.setContent("response");
            msg.setActorId("actor-1");
            msg.setAssistantId("asst-1");
            msg.setMeta("meta");

            assertEquals("assistant", msg.getRole());
            assertEquals("response", msg.getContent());
            assertEquals("actor-1", msg.getActorId());
            assertEquals("asst-1", msg.getAssistantId());
            assertEquals("meta", msg.getMeta());
        }
    }

    // ============================================================
    // ToolCallMessage
    // ============================================================

    @Nested
    class ToolCallMessageTests {

        @Test
        void defaultValues() {
            ToolCallMessage msg = new ToolCallMessage();
            assertEquals("", msg.getId());
            assertEquals("", msg.getName());
            assertEquals("", msg.getArguments());
            assertNull(msg.getMeta());
        }

        @Test
        void threeArgConstructorWithStringArgs() {
            ToolCallMessage msg = new ToolCallMessage("call-1", "get_weather", "{\"city\":\"Beijing\"}");
            assertEquals("call-1", msg.getId());
            assertEquals("get_weather", msg.getName());
            assertEquals("{\"city\":\"Beijing\"}", msg.getArguments());
        }

        @Test
        void threeArgConstructorWithMapArgs() {
            Map<String, Object> args = Map.of("city", "Beijing");
            ToolCallMessage msg = new ToolCallMessage("call-1", "get_weather", args);
            assertEquals("call-1", msg.getId());
            assertEquals("get_weather", msg.getName());
            // Should be JSON-serialized
            assertTrue(msg.getArguments().contains("Beijing"));
            assertTrue(msg.getArguments().contains("city"));
        }

        @Test
        void toDictBasicFormat() {
            ToolCallMessage msg = new ToolCallMessage("call-1", "get_weather", "{}");
            Map<String, Object> dict = msg.toDict();

            assertEquals("tool", dict.get("role"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) dict.get("parts");
            assertNotNull(parts);
            assertEquals(1, parts.size());
            assertEquals("tool_call", parts.get(0).get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> toolCall = (Map<String, Object>) parts.get(0).get("tool_call");
            assertNotNull(toolCall);
            assertEquals("call-1", toolCall.get("id"));
            assertEquals("get_weather", toolCall.get("name"));
            assertEquals("{}", toolCall.get("arguments"));
        }

        @Test
        void toDictMatchesPythonFormat() {
            // Python: {"role": "tool", "parts": [{"type": "tool_call",
            //           "tool_call": {"id": "...", "name": "...", "arguments": "..."}}]}
            ToolCallMessage msg = new ToolCallMessage("c1", "search", "{\"q\":\"test\"}");
            Map<String, Object> dict = msg.toDict();

            assertTrue(dict.containsKey("role"));
            assertEquals("tool", dict.get("role"));
            assertTrue(dict.containsKey("parts"));
            assertFalse(dict.containsKey("id"));      // id is nested in tool_call
            assertFalse(dict.containsKey("name"));    // name is nested in tool_call
        }

        @Test
        void toDictIncludesMetaWhenSet() {
            ToolCallMessage msg = new ToolCallMessage("c1", "tool", "{}");
            msg.setMeta("meta-data");
            Map<String, Object> dict = msg.toDict();
            assertEquals("meta-data", dict.get("meta"));
        }

        @Test
        void toDictExcludesMetaWhenNull() {
            ToolCallMessage msg = new ToolCallMessage("c1", "tool", "{}");
            Map<String, Object> dict = msg.toDict();
            assertFalse(dict.containsKey("meta"));
        }

        @Test
        void settersWork() {
            ToolCallMessage msg = new ToolCallMessage();
            msg.setId("call-2");
            msg.setName("execute");
            msg.setArguments("{\"cmd\":\"ls\"}");
            msg.setMeta("m");

            assertEquals("call-2", msg.getId());
            assertEquals("execute", msg.getName());
            assertEquals("{\"cmd\":\"ls\"}", msg.getArguments());
            assertEquals("m", msg.getMeta());
        }
    }

    // ============================================================
    // ToolResultMessage
    // ============================================================

    @Nested
    class ToolResultMessageTests {

        @Test
        void defaultValues() {
            ToolResultMessage msg = new ToolResultMessage();
            assertEquals("", msg.getToolCallId());
            assertEquals("", msg.getContent());
            assertNull(msg.getAssetRef());
            assertNull(msg.getMeta());
        }

        @Test
        void twoArgConstructor() {
            ToolResultMessage msg = new ToolResultMessage("call-1", "Result: sunny");
            assertEquals("call-1", msg.getToolCallId());
            assertEquals("Result: sunny", msg.getContent());
        }

        @Test
        void toDictBasicFormat() {
            ToolResultMessage msg = new ToolResultMessage("call-1", "sunny");
            Map<String, Object> dict = msg.toDict();

            assertEquals("tool", dict.get("role"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) dict.get("parts");
            assertNotNull(parts);
            assertEquals(1, parts.size());
            assertEquals("tool_result", parts.get(0).get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> toolResult = (Map<String, Object>) parts.get(0).get("tool_result");
            assertNotNull(toolResult);
            assertEquals("call-1", toolResult.get("tool_call_id"));
            assertEquals("sunny", toolResult.get("content"));
        }

        @Test
        void toDictMatchesPythonFormat() {
            // Python: {"role": "tool", "parts": [{"type": "tool_result",
            //           "tool_result": {"tool_call_id": "...", "content": "..."}}]}
            ToolResultMessage msg = new ToolResultMessage("tc-1", "output");
            Map<String, Object> dict = msg.toDict();

            assertEquals("tool", dict.get("role"));
            assertTrue(dict.containsKey("parts"));
            assertFalse(dict.containsKey("tool_call_id")); // nested in tool_result
        }

        @Test
        void toDictWithAssetRef() {
            ToolResultMessage msg = new ToolResultMessage("call-1", "file uploaded");
            Map<String, Object> assetRef = Map.of("asset_id", "a-123", "uri", "s3://bucket/file");
            msg.setAssetRef(assetRef);

            Map<String, Object> dict = msg.toDict();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) dict.get("parts");
            @SuppressWarnings("unchecked")
            Map<String, Object> toolResult = (Map<String, Object>) parts.get(0).get("tool_result");
            @SuppressWarnings("unchecked")
            Map<String, Object> ar = (Map<String, Object>) toolResult.get("asset_ref");
            assertEquals("a-123", ar.get("asset_id"));
            assertEquals("s3://bucket/file", ar.get("uri"));
        }

        @Test
        void toDictWithNullAssetRef() {
            ToolResultMessage msg = new ToolResultMessage("call-1", "text");
            // assetRef defaults to null
            Map<String, Object> dict = msg.toDict();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) dict.get("parts");
            @SuppressWarnings("unchecked")
            Map<String, Object> toolResult = (Map<String, Object>) parts.get(0).get("tool_result");
            assertNull(toolResult.get("asset_ref"));
        }

        @Test
        void toDictIncludesMetaWhenSet() {
            ToolResultMessage msg = new ToolResultMessage("c1", "result");
            msg.setMeta("meta-data");
            Map<String, Object> dict = msg.toDict();
            assertEquals("meta-data", dict.get("meta"));
        }

        @Test
        void toDictExcludesMetaWhenNull() {
            ToolResultMessage msg = new ToolResultMessage("c1", "result");
            Map<String, Object> dict = msg.toDict();
            assertFalse(dict.containsKey("meta"));
        }
    }

    // ============================================================
    // MemorySearchFilter
    // ============================================================

    @Nested
    class MemorySearchFilterTests {

        @Test
        void toDictEmptyByDefault() {
            MemorySearchFilter filter = new MemorySearchFilter();
            Map<String, Object> dict = filter.toDict();
            assertTrue(dict.isEmpty());
        }

        @Test
        void toDictWithQuery() {
            MemorySearchFilter filter = new MemorySearchFilter();
            filter.setQuery("What is the user's name?");
            Map<String, Object> dict = filter.toDict();
            assertEquals("What is the user's name?", dict.get("query"));
        }

        @Test
        void toDictWithAllFields() {
            MemorySearchFilter filter = new MemorySearchFilter();
            filter.setQuery("test");
            filter.setActorId("actor-1");
            filter.setTopK(5);
            filter.setMinScore(0.8);

            Map<String, Object> dict = filter.toDict();
            assertEquals("test", dict.get("query"));
            assertEquals("actor-1", dict.get("actor_id"));
            assertEquals(5, dict.get("top_k"));
            assertEquals(0.8, dict.get("min_score"));
        }

        @Test
        void toDictExcludesNullFields() {
            MemorySearchFilter filter = new MemorySearchFilter();
            filter.setQuery("test");
            Map<String, Object> dict = filter.toDict();
            assertFalse(dict.containsKey("actor_id"));
            assertFalse(dict.containsKey("top_k"));
            assertFalse(dict.containsKey("min_score"));
        }
    }

    // ============================================================
    // MemoryListFilter
    // ============================================================

    @Nested
    class MemoryListFilterTests {

        @Test
        void toDictEmptyByDefault() {
            MemoryListFilter filter = new MemoryListFilter();
            Map<String, Object> dict = filter.toDict();
            assertTrue(dict.isEmpty());
        }

        @Test
        void toDictWithAllFields() {
            MemoryListFilter filter = new MemoryListFilter();
            filter.setStrategyType("semantic");
            filter.setStrategyId("strat-1");
            filter.setActorId("actor-1");
            filter.setAssistantId("asst-1");
            filter.setSessionId("sess-1");
            filter.setStartTime(1000L);
            filter.setEndTime(2000L);
            filter.setSortBy("created_at");
            filter.setSortOrder("desc");

            Map<String, Object> dict = filter.toDict();
            assertEquals("semantic", dict.get("strategy_type"));
            assertEquals("strat-1", dict.get("strategy_id"));
            assertEquals("actor-1", dict.get("actor_id"));
            assertEquals("asst-1", dict.get("assistant_id"));
            assertEquals("sess-1", dict.get("session_id"));
            assertEquals(1000L, dict.get("start_time"));
            assertEquals(2000L, dict.get("end_time"));
            assertEquals("created_at", dict.get("sort_by"));
            assertEquals("desc", dict.get("sort_order"));
        }

        @Test
        void toDictExcludesNullFields() {
            MemoryListFilter filter = new MemoryListFilter();
            filter.setStrategyType("summary");
            Map<String, Object> dict = filter.toDict();
            assertEquals("summary", dict.get("strategy_type"));
            assertFalse(dict.containsKey("actor_id"));
            assertFalse(dict.containsKey("sort_by"));
        }

        @Test
        void settersAndGetters() {
            MemoryListFilter filter = new MemoryListFilter();
            filter.setStrategyType("semantic");
            filter.setStrategyId("s1");
            filter.setActorId("a1");
            filter.setAssistantId("as1");
            filter.setSessionId("se1");
            filter.setStartTime(100L);
            filter.setEndTime(200L);
            filter.setSortBy("updated_at");
            filter.setSortOrder("asc");

            assertEquals("semantic", filter.getStrategyType());
            assertEquals("s1", filter.getStrategyId());
            assertEquals("a1", filter.getActorId());
            assertEquals("as1", filter.getAssistantId());
            assertEquals("se1", filter.getSessionId());
            assertEquals(100L, filter.getStartTime());
            assertEquals(200L, filter.getEndTime());
            assertEquals("updated_at", filter.getSortBy());
            assertEquals("asc", filter.getSortOrder());
        }
    }

    // ============================================================
    // AddMessagesRequest
    // ============================================================

    @Nested
    class AddMessagesRequestTests {

        @Test
        void defaultValues() {
            AddMessagesRequest req = new AddMessagesRequest();
            assertNull(req.getMessages());
            assertNull(req.getTimestamp());
            assertNull(req.getIdempotencyKey());
            assertFalse(req.isIsForceExtract());
        }

        @Test
        void fluentApi() {
            List<Map<String, Object>> messages = List.of(
                    new TextMessage("user", "Hello").toDict()
            );
            AddMessagesRequest req = new AddMessagesRequest()
                    .withMessages(messages)
                    .withTimestamp(12345L)
                    .withIdempotencyKey("key-1")
                    .withIsForceExtract(true);

            assertEquals(1, req.getMessages().size());
            assertEquals(12345L, req.getTimestamp());
            assertEquals("key-1", req.getIdempotencyKey());
            assertTrue(req.isIsForceExtract());
        }

        @Test
        void jsonSerialization() {
            List<Map<String, Object>> messages = List.of(
                    new TextMessage("user", "Hello").toDict()
            );
            AddMessagesRequest req = new AddMessagesRequest()
                    .withMessages(messages)
                    .withIsForceExtract(false);

            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"messages\""));
            assertTrue(json.contains("\"is_force_extract\":false"));
        }

        @Test
        void jsonSerializationNonNullFieldsOnly() {
            AddMessagesRequest req = new AddMessagesRequest()
                    .withMessages(List.of());
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"messages\":[]"));
            assertFalse(json.contains("\"timestamp\""));
            assertFalse(json.contains("\"idempotency_key\""));
        }

        @Test
        void equalityAndHashCode() {
            AddMessagesRequest a = new AddMessagesRequest().withIdempotencyKey("k1");
            AddMessagesRequest b = new AddMessagesRequest().withIdempotencyKey("k1");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void toStringRedactsMessageAndIdempotencyValues() {
            AddMessagesRequest req = new AddMessagesRequest()
                    .withMessages(List.of(Map.of("content", "unit-sensitive-marker")))
                    .withIdempotencyKey("unit-sensitive-marker");
            assertFalse(req.toString().contains("unit-sensitive-marker"));
            assertTrue(req.toString().contains("count=1"));
            assertTrue(req.toString().contains("[REDACTED]"));
        }
    }

    // ============================================================
    // CreateSpaceRequest
    // ============================================================

    @Nested
    class CreateSpaceRequestTests {

        @Test
        void defaultValues() {
            CreateSpaceRequest req = new CreateSpaceRequest();
            assertNull(req.getName());
            assertNull(req.getDescription());
            assertNull(req.getApiKeyId());
            assertEquals(0, req.getMessageTtlHours()); // int default is 0, not 168
        }

        @Test
        void fluentApi() {
            CreateSpaceRequest req = new CreateSpaceRequest()
                    .withName("my-space")
                    .withDescription("Test space")
                    .withMessageTtlHours(168)
                    .withPublicAccessEnable(true)
                    .withApiKeyId("key-1");

            assertEquals("my-space", req.getName());
            assertEquals("Test space", req.getDescription());
            assertEquals(168, req.getMessageTtlHours());
            assertTrue(req.getPublicAccessEnable());
            assertEquals("key-1", req.getApiKeyId());
        }

        @Test
        void jsonSerialization() {
            CreateSpaceRequest req = new CreateSpaceRequest()
                    .withName("test-space")
                    .withMessageTtlHours(72);
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"test-space\""));
            assertTrue(json.contains("\"message_ttl_hours\":72"));
        }

        @Test
        void jsonSerializationExcludesNulls() {
            CreateSpaceRequest req = new CreateSpaceRequest().withName("test");
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"test\""));
            assertFalse(json.contains("\"description\""));
            assertFalse(json.contains("\"api_key_id\""));
        }

        @Test
        void equalityAndHashCode() {
            CreateSpaceRequest a = fullyConfiguredCreateSpaceRequest();
            CreateSpaceRequest b = fullyConfiguredCreateSpaceRequest();
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());

            b.withPublicAccessEnable(false);
            assertNotEquals(a, b);
        }

        private CreateSpaceRequest fullyConfiguredCreateSpaceRequest() {
            return new CreateSpaceRequest()
                    .withName("test")
                    .withDescription("description")
                    .withApiKeyId("key-id")
                    .withTags(List.of(Map.of("key", "environment", "value", "test")))
                    .withPublicAccessEnable(true)
                    .withPrivateVpcId("vpc-id")
                    .withPrivateSubnetId("subnet-id")
                    .withNetworkAccess(Map.of("public_access_enable", true))
                    .withMemoryExtractIdleSeconds(30)
                    .withMemoryExtractMaxTokens(1000)
                    .withMemoryExtractMaxMessages(20)
                    .withMemoryStrategiesBuiltin(List.of("semantic"))
                    .withMemoryStrategiesCustomized(List.of(Map.of("name", "custom")))
                    .withMessageTtlHours(168);
        }

        @Test
        void toStringContainsName() {
            CreateSpaceRequest req = new CreateSpaceRequest().withName("my-space");
            assertTrue(req.toString().contains("my-space"));
        }

        @Test
        void memoryStrategiesFields() {
            CreateSpaceRequest req = new CreateSpaceRequest()
                    .withMemoryStrategiesBuiltin(List.of("semantic", "summary"))
                    .withMemoryStrategiesCustomized(List.of(Map.of("type", "custom")));

            assertEquals(2, req.getMemoryStrategiesBuiltin().size());
            assertEquals(1, req.getMemoryStrategiesCustomized().size());
        }

        @Test
        void networkAccessField() {
            Map<String, Object> networkAccess = Map.of("public_access_enable", true);
            CreateSpaceRequest req = new CreateSpaceRequest()
                    .withNetworkAccess(networkAccess);
            assertEquals(true, req.getNetworkAccess().get("public_access_enable"));
        }

        @Test
        void memoryExtractFields() {
            CreateSpaceRequest req = new CreateSpaceRequest()
                    .withMemoryExtractIdleSeconds(300)
                    .withMemoryExtractMaxTokens(10000)
                    .withMemoryExtractMaxMessages(100);

            assertEquals(300, req.getMemoryExtractIdleSeconds());
            assertEquals(10000, req.getMemoryExtractMaxTokens());
            assertEquals(100, req.getMemoryExtractMaxMessages());
        }
    }

    // ============================================================
    // Response models deserialization
    // ============================================================

    @Nested
    class ResponseModelTests {

        @Test
        void sessionInfoDeserialization() throws Exception {
            String json = "{\"id\":\"sess-123\",\"space_id\":\"sp-456\","
                    + "\"actor_id\":\"actor-1\",\"assistant_id\":\"asst-1\","
                    + "\"meta\":{\"key\":\"value\"},"
                    + "\"created_at\":\"2025-01-01T00:00:00Z\","
                    + "\"updated_at\":\"2025-01-02T00:00:00Z\"}";
            SessionInfo info = JsonUtils.MAPPER.readValue(json, SessionInfo.class);
            assertEquals("sess-123", info.getId());
            assertEquals("sp-456", info.getSpaceId());
            assertEquals("actor-1", info.getActorId());
            assertEquals("asst-1", info.getAssistantId());
            assertEquals("value", info.getMeta().get("key"));
            assertEquals("2025-01-01T00:00:00Z", info.getCreatedAt());
        }

        @Test
        void sessionInfoIgnoresUnknown() throws Exception {
            String json = "{\"id\":\"s1\",\"extra\":\"ignored\"}";
            SessionInfo info = JsonUtils.MAPPER.readValue(json, SessionInfo.class);
            assertEquals("s1", info.getId());
        }

        @Test
        void spaceInfoDeserialization() throws Exception {
            String json = "{\"id\":\"sp-123\",\"name\":\"my-space\","
                    + "\"description\":\"Test\",\"message_ttl_hours\":72,"
                    + "\"status\":\"active\",\"created_at\":\"2025-01-01\","
                    + "\"memory_extract_enabled\":true,"
                    + "\"api_key\":\"key-123\",\"api_key_id\":\"kid-1\","
                    + "\"public_domain\":\"https://example.com\"}";
            SpaceInfo info = JsonUtils.MAPPER.readValue(json, SpaceInfo.class);
            assertEquals("sp-123", info.getId());
            assertEquals("my-space", info.getName());
            assertEquals("Test", info.getDescription());
            assertEquals(72, info.getMessageTtlHours());
            assertEquals("active", info.getStatus());
            assertTrue(info.isMemoryExtractEnabled());
            assertEquals("key-123", info.getApiKey());
            assertEquals("kid-1", info.getApiKeyId());
            assertEquals("https://example.com", info.getPublicDomain());
        }

        @Test
        void spaceInfoDeserializesAdvancedConfiguration() throws Exception {
            String json = "{\"id\":\"sp-123\",\"memory_extract_idle_seconds\":30,"
                    + "\"memory_extract_max_tokens\":1000,\"memory_extract_max_messages\":20,"
                    + "\"memory_strategies_builtin\":[{\"name\":\"semantic\"}],"
                    + "\"memory_strategies_customized\":[{\"name\":\"custom\"}],"
                    + "\"private_access\":{\"domain\":\"private.example\",\"ip\":\"192.0.2.1\"},"
                    + "\"private_domain\":\"private.example\",\"private_ip\":\"192.0.2.1\"}";
            SpaceInfo info = JsonUtils.MAPPER.readValue(json, SpaceInfo.class);

            assertEquals(30, info.getMemoryExtractIdleSeconds());
            assertEquals(1000, info.getMemoryExtractMaxTokens());
            assertEquals(20, info.getMemoryExtractMaxMessages());
            assertEquals("semantic", info.getMemoryStrategiesBuiltin().get(0).get("name"));
            assertEquals("custom", info.getMemoryStrategiesCustomized().get(0).get("name"));
            assertEquals("private.example", info.getPrivateAccess().get("domain"));
            assertEquals("private.example", info.getPrivateDomain());
            assertEquals("192.0.2.1", info.getPrivateIp());
        }

        @Test
        void spaceInfoDefaultMessageTtl() throws Exception {
            String json = "{\"id\":\"sp-1\"}";
            SpaceInfo info = JsonUtils.MAPPER.readValue(json, SpaceInfo.class);
            assertEquals(168, info.getMessageTtlHours());
        }

        @Test
        void memoryInfoDeserialization() throws Exception {
            String json = "{\"id\":\"mem-123\",\"space_id\":\"sp-456\","
                    + "\"strategy_id\":\"strat-1\",\"strategy_type\":\"semantic\","
                    + "\"actor_id\":\"actor-1\",\"assistant_id\":\"asst-1\","
                    + "\"session_id\":\"sess-1\",\"content\":\"User prefers dark mode\","
                    + "\"memory_type\":\"memory\",\"isolation_level\":\"actor\","
                    + "\"created_at\":\"2025-01-01\"}";
            MemoryInfo info = JsonUtils.MAPPER.readValue(json, MemoryInfo.class);
            assertEquals("mem-123", info.getId());
            assertEquals("sp-456", info.getSpaceId());
            assertEquals("strat-1", info.getStrategyId());
            assertEquals("semantic", info.getStrategyType());
            assertEquals("actor-1", info.getActorId());
            assertEquals("User prefers dark mode", info.getContent());
            assertEquals("memory", info.getMemoryType());
            assertEquals("actor", info.getIsolationLevel());
        }

        @Test
        void memoryInfoDefaults() throws Exception {
            String json = "{\"id\":\"m1\"}";
            MemoryInfo info = JsonUtils.MAPPER.readValue(json, MemoryInfo.class);
            assertEquals("", info.getContent());
            assertEquals("memory", info.getMemoryType());
            assertEquals("actor", info.getIsolationLevel());
        }

        @Test
        void messageInfoDeserialization() throws Exception {
            String json = "{\"id\":\"msg-123\",\"session_id\":\"sess-456\","
                    + "\"seq\":1,\"actor_id\":\"actor-1\","
                    + "\"role\":\"user\","
                    + "\"parts\":[{\"type\":\"text\",\"text\":\"Hello\"}],"
                    + "\"idempotency_key\":\"key-1\","
                    + "\"message_time\":1704067200000,"
                    + "\"created_at\":\"2025-01-01T00:00:00Z\"}";
            MessageInfo info = JsonUtils.MAPPER.readValue(json, MessageInfo.class);
            assertEquals("msg-123", info.getId());
            assertEquals("sess-456", info.getSessionId());
            assertEquals(1, info.getSeq());
            assertEquals("actor-1", info.getActorId());
            assertEquals("user", info.getRole());
            assertNotNull(info.getParts());
            assertEquals(1, info.getParts().size());
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> firstPart = (java.util.Map<String, Object>) info.getParts().get(0);
            assertEquals("text", firstPart.get("type"));
            assertEquals("key-1", info.getIdempotencyKey());
            assertEquals(1704067200000L, info.getMessageTime());
        }

        @Test
        void messageInfoDefaultRole() throws Exception {
            String json = "{\"id\":\"m1\"}";
            MessageInfo info = JsonUtils.MAPPER.readValue(json, MessageInfo.class);
            assertEquals("user", info.getRole());
        }

        @Test
        void memorySearchResponseDeserialization() throws Exception {
            String json = "{\"results\":[{\"record\":{\"content\":\"memory1\"},\"score\":0.95}],"
                    + "\"total\":1,\"query\":\"What does user prefer?\"}";
            MemorySearchResponse resp = JsonUtils.MAPPER.readValue(json, MemorySearchResponse.class);
            assertEquals(1, resp.getTotal());
            assertEquals("What does user prefer?", resp.getQuery());
            assertNotNull(resp.getResults());
            assertEquals(1, resp.getResults().size());
            assertEquals(0.95, resp.getResults().get(0).get("score"));
        }

        @Test
        void memorySearchResponseDefaults() throws Exception {
            String json = "{}";
            MemorySearchResponse resp = JsonUtils.MAPPER.readValue(json, MemorySearchResponse.class);
            assertEquals(0, resp.getTotal());
            assertNull(resp.getResults());
            assertNull(resp.getQuery());
        }

        @Test
        void spaceListResponseDeserialization() throws Exception {
            // SpaceListResponse uses @JsonProperty("spaces") for items, @JsonProperty("size") for limit
            String json = "{\"spaces\":[{\"id\":\"sp-1\",\"name\":\"space1\"}],"
                    + "\"total\":1,\"size\":20,\"offset\":0}";
            SpaceListResponse resp = JsonUtils.MAPPER.readValue(json, SpaceListResponse.class);
            assertEquals(1, resp.getTotal());
            assertEquals(20, resp.getLimit());
            assertEquals(0, resp.getOffset());
            assertEquals(1, resp.getItems().size());
            assertEquals("space1", resp.getItems().get(0).getName());
        }

        @Test
        void messageListResponseDeserialization() throws Exception {
            String json = "{\"items\":[{\"id\":\"m1\",\"role\":\"user\"}],"
                    + "\"total\":1,\"limit\":10,\"offset\":0}";
            MessageListResponse resp = JsonUtils.MAPPER.readValue(json, MessageListResponse.class);
            assertEquals(1, resp.getTotal());
            assertEquals(10, resp.getLimit());
            assertEquals(0, resp.getOffset());
            assertEquals("user", resp.getItems().get(0).getRole());
        }

        @Test
        void memoryListResponseDeserialization() throws Exception {
            String json = "{\"items\":[{\"id\":\"mem-1\",\"content\":\"test\"}],"
                    + "\"total\":1,\"limit\":10,\"offset\":0}";
            MemoryListResponse resp = JsonUtils.MAPPER.readValue(json, MemoryListResponse.class);
            assertEquals(1, resp.getTotal());
            assertEquals("test", resp.getItems().get(0).getContent());
        }

        @Test
        void messageBatchResponseDeserialization() throws Exception {
            // MessageBatchResponse uses @JsonProperty("messages") for items
            String json = "{\"messages\":[{\"id\":\"m1\"},{\"id\":\"m2\"}],\"count\":2}";
            MessageBatchResponse resp = JsonUtils.MAPPER.readValue(json, MessageBatchResponse.class);
            assertNotNull(resp.getItems());
            assertEquals(2, resp.getItems().size());
            assertEquals(2, resp.getCount());
        }

        @Test
        void apiKeyInfoDeserialization() throws Exception {
            String json = "{\"id\":\"key-1\",\"api_key\":\"ak-12345\"}";
            ApiKeyInfo info = JsonUtils.MAPPER.readValue(json, ApiKeyInfo.class);
            assertEquals("key-1", info.getId());
            assertEquals("ak-12345", info.getApiKey());
        }
    }

    // ============================================================
    // CreateMemorySessionRequest
    // ============================================================

    @Nested
    class CreateMemorySessionRequestTests {

        @Test
        void jsonSerialization() throws Exception {
            CreateMemorySessionRequest req = new CreateMemorySessionRequest()
                    .withId("sess-1")
                    .withActorId("actor-1")
                    .withAssistantId("asst-1")
                    .withMeta(Map.of("key", "val"));
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"id\":\"sess-1\""));
            assertTrue(json.contains("\"actor_id\":\"actor-1\""));
            assertTrue(json.contains("\"assistant_id\":\"asst-1\""));
            assertTrue(json.contains("\"key\":\"val\""));
        }

        @Test
        void deserialization() throws Exception {
            String json = "{\"id\":\"sess-1\",\"actor_id\":\"actor-1\","
                    + "\"assistant_id\":\"asst-1\",\"meta\":{\"key\":\"val\"}}";
            CreateMemorySessionRequest req = JsonUtils.MAPPER.readValue(json, CreateMemorySessionRequest.class);
            assertEquals("sess-1", req.getId());
            assertEquals("actor-1", req.getActorId());
            assertEquals("asst-1", req.getAssistantId());
            assertNotNull(req.getMeta());
            assertEquals("val", req.getMeta().get("key"));
        }

        @Test
        void equalityIncludesMetadata() {
            CreateMemorySessionRequest first = new CreateMemorySessionRequest()
                    .withId("session-id")
                    .withMeta(Map.of("tenant", "one"));
            CreateMemorySessionRequest second = new CreateMemorySessionRequest()
                    .withId("session-id")
                    .withMeta(Map.of("tenant", "one"));

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            assertNotEquals(first, second.withMeta(Map.of("tenant", "two")));
        }
    }

    // ============================================================
    // UpdateSpaceRequest
    // ============================================================

    @Nested
    class UpdateSpaceRequestTests {

        @Test
        void jsonSerialization() {
            UpdateSpaceRequest req = new UpdateSpaceRequest();
            req.setName("updated-space");
            req.setDescription("Updated");
            String json = JsonUtils.toJson(req);
            assertTrue(json.contains("\"name\":\"updated-space\""));
            assertTrue(json.contains("\"description\":\"Updated\""));
        }

        @Test
        void equalityIncludesAdvancedConfiguration() {
            UpdateSpaceRequest first = fullyConfiguredUpdateSpaceRequest();
            UpdateSpaceRequest second = fullyConfiguredUpdateSpaceRequest();

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            assertNotEquals(first, second.withMemoryExtractEnabled(false));
        }

        private UpdateSpaceRequest fullyConfiguredUpdateSpaceRequest() {
            return new UpdateSpaceRequest()
                    .withName("updated-space")
                    .withDescription("Updated")
                    .withMessageTtlHours(336)
                    .withMemoryExtractEnabled(true)
                    .withMemoryExtractIdleSeconds(30)
                    .withMemoryExtractMaxTokens(1000)
                    .withMemoryExtractMaxMessages(20)
                    .withTags(List.of(Map.of("key", "environment", "value", "test")))
                    .withMemoryStrategiesBuiltin(List.of("semantic"));
        }
    }
}
