package com.huaweicloud.agentarts.sdk.integration.agentscope.state;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.agentarts.sdk.core.APIException;
import com.huaweicloud.agentarts.sdk.memory.MemoryClient;
import com.huaweicloud.agentarts.sdk.memory.model.*;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope {@link AgentStateStore} 实现，通过 AgentArts Memory 数据面 API 持久化状态。
 *
 * <p>映射策略：每个 (userId, sessionId, key) 三元组对应一个 Memory Session，
 * State 对象序列化为 JSON 后作为 TextMessage 存入。每次 save 追加新消息，
 * 读取时取最新的消息。</p>
 *
 * <p>消息内容格式：</p>
 * <ul>
 *   <li>单 State: {@code __S__:{className}:{json}}</li>
 *   <li>列表 State: 每个元素一条 {@code __L__:{className}:{json}}</li>
 * </ul>
 */
public class MemoryAgentStateStore implements AgentStateStore {

    private static final String DEFAULT_USER_ID = "agentscope";

    private static final String SINGLE_PREFIX = "__S__:";
    private static final String LIST_PREFIX = "__L__:";
    private static final String LIST_BATCH = "__LB__:";
    private static final String DELETE_PREFIX = "__D__:";
    private static final String INDEX_PREFIX = "__I__:";
    private static final String INDEX_USER = "__agentscope_index__";
    private static final String INDEX_SESSION = "__agentscope_index__";
    private static final String INDEX_KEY = "__agentscope_index_v1__";
    private static final int MESSAGE_PAGE_SIZE = 100;

    private final MemoryClient memoryClient;
    private final String spaceId;

    /** ObjectMapper 配置：忽略未知属性，与 agentscope 的 JacksonJsonCodec 行为一致。 */
    private final ObjectMapper mapper;

    /** 本地缓存: logicalKey → Memory Session ID，避免重复创建 Session。 */
    private final Map<String, String> sessionCache = new ConcurrentHashMap<>();

    /** 本地缓存: logicalKey → 已知的 session 存在状态（用于 exists/listSessionIds）。 */
    public MemoryAgentStateStore(MemoryClient memoryClient, String spaceId) {
        this.memoryClient = Objects.requireNonNull(memoryClient, "memoryClient");
        if (spaceId == null || spaceId.isBlank()) {
            throw new IllegalArgumentException("spaceId must not be blank");
        }
        this.spaceId = spaceId;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 便捷构造函数。
     */
    public MemoryAgentStateStore(String regionName, String apiKey, String spaceId) {
        this(new MemoryClient(regionName, apiKey), spaceId);
    }

    // ========================
    // 向后兼容的多用户访问方法
    // ========================

    public void save(String userId, String sessionId, String key, State value) {
        Objects.requireNonNull(value, "value");
        String memSessionId = getOrCreateMemorySession(userId, sessionId, key);

        try {
            String json = mapper.writeValueAsString(value);
            String className = value.getClass().getName();
            String content = SINGLE_PREFIX + className + ":" + json;
            TextMessage msg = new TextMessage("system", content);
            memoryClient.addMessages(spaceId, memSessionId, List.of(msg), null, null, false);
            appendIndex("upsert", userId, sessionId, key);
        } catch (Exception e) {
            throw new AgentStateStoreException("Failed to save AgentScope state", e);
        }
    }

    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        Objects.requireNonNull(values, "values");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("values must not contain null elements");
        }
        String memSessionId = getOrCreateMemorySession(userId, sessionId, key);

        try {
            List<TextMessage> messages = new ArrayList<>();
            String batchId = UUID.randomUUID().toString();
            // Batch marker: indicates start of a new list save (enables full-replacement semantics)
            messages.add(new TextMessage(
                    "system", LIST_BATCH + batchId + ":" + values.size()));
            for (State item : values) {
                String json = mapper.writeValueAsString(item);
                String className = item.getClass().getName();
                messages.add(new TextMessage(
                        "system", LIST_PREFIX + batchId + ":" + className + ":" + json));
            }
            appendMessagesInBatches(memSessionId, messages);
            appendIndex("upsert", userId, sessionId, key);
        } catch (Exception e) {
            throw new AgentStateStoreException("Failed to save AgentScope state list", e);
        }
    }

    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        Objects.requireNonNull(type, "type");
        String logicalKey = buildLogicalKey(userId, sessionId, key);
        String memSessionId = sessionCache.getOrDefault(
                logicalKey, deterministicSessionId(userId, sessionId, key));

        try {
            List<MessageInfo> msgs = memoryClient.getLastKMessages(memSessionId, 1, spaceId);
            if (msgs == null || msgs.isEmpty()) return Optional.empty();

            // 从最后一条消息开始查找 __S__: 前缀的消息
            for (int i = msgs.size() - 1; i >= 0; i--) {
                String text = extractText(msgs.get(i));
                if (text != null && text.startsWith(DELETE_PREFIX)) {
                    return Optional.empty();
                }
                if (text != null && text.startsWith(SINGLE_PREFIX)) {
                    String body = text.substring(SINGLE_PREFIX.length());
                    int colonIdx = body.indexOf(':');
                    if (colonIdx > 0) {
                        String json = body.substring(colonIdx + 1);
                        T result = mapper.readValue(json, type);
                        return Optional.of(result);
                    }
                }
            }
        } catch (APIException e) {
            if (e.getStatusCode() == 404) return Optional.empty();
            throw new AgentStateStoreException("Failed to load AgentScope state", e);
        } catch (Exception e) {
            throw new AgentStateStoreException("Failed to load AgentScope state", e);
        }
        return Optional.empty();
    }

    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> itemType) {
        Objects.requireNonNull(itemType, "itemType");
        String logicalKey = buildLogicalKey(userId, sessionId, key);
        String memSessionId = sessionCache.getOrDefault(
                logicalKey, deterministicSessionId(userId, sessionId, key));

        try {
            // 获取消息总数
            List<MessageInfo> allMsgs = listAllMessages(memSessionId);
            if (allMsgs.isEmpty()) return List.of();

            // 获取全部消息
            String lastText = extractText(allMsgs.get(allMsgs.size() - 1));
            if (lastText != null && lastText.startsWith(DELETE_PREFIX)) return List.of();

            // 从末尾找到最后一个 __LB__: 批次标记，只读取该批次的 __L__: 消息
            int batchStart = -1;
            ListBatch batch = null;
            for (int i = allMsgs.size() - 1; i >= 0; i--) {
                String text = extractText(allMsgs.get(i));
                if (text != null && text.startsWith(LIST_BATCH)) {
                    batchStart = i;
                    batch = parseListBatch(text);
                    break;
                }
            }
            if (batchStart < 0 || batch == null) return List.of();

            // 读取 batchStart 之后的 batchCount 条 __L__: 消息
            List<T> result = new ArrayList<>();
            for (int i = batchStart + 1;
                    i < allMsgs.size() && result.size() < batch.count();
                    i++) {
                String text = extractText(allMsgs.get(i));
                if (text != null && text.startsWith(LIST_PREFIX)) {
                    String body = text.substring(LIST_PREFIX.length());
                    if (batch.id() != null) {
                        String batchPrefix = batch.id() + ":";
                        if (!body.startsWith(batchPrefix)) continue;
                        body = body.substring(batchPrefix.length());
                    }
                    int colonIdx = body.indexOf(':');
                    if (colonIdx > 0) {
                        String json = body.substring(colonIdx + 1);
                        result.add(mapper.readValue(json, itemType));
                    }
                }
            }
            if (result.size() != batch.count()) {
                throw new AgentStateStoreException(
                        "AgentScope state list contains an incomplete message batch");
            }
            return result;
        } catch (APIException e) {
            if (e.getStatusCode() == 404) return List.of();
            throw new AgentStateStoreException("Failed to load AgentScope state list", e);
        } catch (Exception e) {
            throw new AgentStateStoreException("Failed to load AgentScope state list", e);
        }
    }

    public boolean exists(String userId, String sessionId) {
        validateSessionId(sessionId);
        return !readIndex().keys(normalizeUserId(userId), sessionId).isEmpty();
    }

    public void delete(String userId, String sessionId) {
        validateSessionId(sessionId);
        String normalizedUserId = normalizeUserId(userId);
        Set<String> keys = readIndex().keys(normalizedUserId, sessionId);
        for (String key : keys) {
            appendTombstone(userId, sessionId, key);
            sessionCache.remove(buildLogicalKey(userId, sessionId, key));
        }
        appendIndex("delete_session", userId, sessionId, null);
    }

    public void delete(String userId, String sessionId, String key) {
        String logicalKey = buildLogicalKey(userId, sessionId, key);
        appendTombstone(userId, sessionId, key);
        appendIndex("delete_key", userId, sessionId, key);
        sessionCache.remove(logicalKey);
    }

    public Set<String> listSessionIds(String userId) {
        return readIndex().sessionIds(normalizeUserId(userId));
    }

    @Override
    public void close() {
        memoryClient.close();
    }

    // ========================
    // 内部方法
    // ========================

    /**
     * 获取或创建 Memory Session。
     * Memory API 要求 Session ID 为 UUID 格式，因此让服务端自动生成，
     * 本地缓存 logicalKey → server-generated UUID 的映射。
     *
     * @return Memory Session ID（UUID），创建失败时返回 null
     */
    private String getOrCreateMemorySession(String userId, String sessionId, String key) {
        String logicalKey = buildLogicalKey(userId, sessionId, key);
        return sessionCache.computeIfAbsent(logicalKey, k -> {
            String deterministicId = deterministicSessionId(userId, sessionId, key);
            try {
                SessionInfo session = memoryClient.createMemorySession(
                        spaceId, deterministicId, normalizeUserId(userId), null);
                if (session != null && session.getId() != null) {
                    return session.getId();
                }
            } catch (APIException e) {
                if (e.getStatusCode() == 409) {
                    return deterministicId;
                }
                throw new AgentStateStoreException("Failed to create AgentArts Memory session", e);
            } catch (Exception e) {
                throw new AgentStateStoreException("Failed to create AgentArts Memory session", e);
            }
            throw new AgentStateStoreException(
                    "AgentArts Memory did not return a session identifier");
        });
    }

    private void appendTombstone(String userId, String sessionId, String key) {
        String memorySessionId = getOrCreateMemorySession(userId, sessionId, key);
        try {
            memoryClient.addMessages(
                    spaceId,
                    memorySessionId,
                    List.of(new TextMessage("system", DELETE_PREFIX + "deleted")),
                    null,
                    null,
                    false);
        } catch (Exception e) {
            throw new AgentStateStoreException("Failed to delete AgentScope state", e);
        }
    }

    private void appendIndex(
            String operation, String userId, String sessionId, String key) {
        validateSessionId(sessionId);
        String indexSessionId = getOrCreateMemorySession(
                INDEX_USER, INDEX_SESSION, INDEX_KEY);
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("operation", operation);
            entry.put("user_id", normalizeUserId(userId));
            entry.put("session_id", sessionId);
            if (key != null) entry.put("key", key);
            String content = INDEX_PREFIX + mapper.writeValueAsString(entry);
            memoryClient.addMessages(
                    spaceId,
                    indexSessionId,
                    List.of(new TextMessage("system", content)),
                    null,
                    null,
                    false);
        } catch (Exception e) {
            throw new AgentStateStoreException("Failed to update AgentScope state index", e);
        }
    }

    private IndexSnapshot readIndex() {
        String indexSessionId = deterministicSessionId(
                INDEX_USER, INDEX_SESSION, INDEX_KEY);
        try {
            List<MessageInfo> messages = listAllMessages(indexSessionId);
            if (messages.isEmpty()) return new IndexSnapshot();
            IndexSnapshot snapshot = new IndexSnapshot();
            for (MessageInfo message : messages) {
                String text = extractText(message);
                if (text == null || !text.startsWith(INDEX_PREFIX)) continue;
                JsonNode entry = mapper.readTree(text.substring(INDEX_PREFIX.length()));
                String operation = requiredIndexText(entry, "operation");
                String indexedUserId = requiredIndexText(entry, "user_id");
                String indexedSessionId = requiredIndexText(entry, "session_id");
                if ("upsert".equals(operation)) {
                    snapshot.upsert(indexedUserId, indexedSessionId,
                            requiredIndexText(entry, "key"));
                } else if ("delete_key".equals(operation)) {
                    snapshot.deleteKey(indexedUserId, indexedSessionId,
                            requiredIndexText(entry, "key"));
                } else if ("delete_session".equals(operation)) {
                    snapshot.deleteSession(indexedUserId, indexedSessionId);
                } else {
                    throw new AgentStateStoreException(
                            "AgentScope state index contains an unknown operation");
                }
            }
            return snapshot;
        } catch (APIException e) {
            if (e.getStatusCode() == 404) return new IndexSnapshot();
            throw new AgentStateStoreException("Failed to load AgentScope state index", e);
        } catch (AgentStateStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentStateStoreException("Failed to load AgentScope state index", e);
        }
    }

    private static String requiredIndexText(JsonNode entry, String field) {
        JsonNode value = entry.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new AgentStateStoreException(
                    "AgentScope state index is missing field: " + field);
        }
        return value.asText();
    }

    private void appendMessagesInBatches(String memorySessionId, List<TextMessage> messages) {
        for (int start = 0; start < messages.size(); start += MESSAGE_PAGE_SIZE) {
            int end = Math.min(messages.size(), start + MESSAGE_PAGE_SIZE);
            memoryClient.addMessages(
                    spaceId,
                    memorySessionId,
                    messages.subList(start, end),
                    null,
                    null,
                    false);
        }
    }

    private static ListBatch parseListBatch(String text) {
        String body = text.substring(LIST_BATCH.length());
        int separator = body.indexOf(':');
        if (separator < 0) {
            return new ListBatch(null, Integer.parseInt(body));
        }
        String batchId = body.substring(0, separator);
        if (batchId.isBlank()) {
            throw new AgentStateStoreException("AgentScope state list batch ID is empty");
        }
        return new ListBatch(
                batchId,
                Integer.parseInt(body.substring(separator + 1)));
    }

    private List<MessageInfo> listAllMessages(String memorySessionId) {
        List<MessageInfo> result = new ArrayList<>();
        int offset = 0;
        int expectedTotal = Integer.MAX_VALUE;
        while (offset < expectedTotal) {
            MessageListResponse page = memoryClient.listMessages(
                    spaceId, memorySessionId, MESSAGE_PAGE_SIZE, offset);
            if (page == null) {
                throw new AgentStateStoreException(
                        "AgentArts Memory returned an empty message page response");
            }
            if (offset == 0) {
                expectedTotal = Math.max(0, page.getTotal());
                if (expectedTotal == 0) return List.of();
            }
            List<MessageInfo> items = page.getItems();
            if (items == null || items.isEmpty()) {
                throw new AgentStateStoreException(
                        "AgentArts Memory pagination ended before the advertised total");
            }
            int remaining = expectedTotal - result.size();
            result.addAll(items.size() <= remaining ? items : items.subList(0, remaining));
            offset += items.size();
        }
        return result;
    }

    private String deterministicSessionId(String userId, String sessionId, String key) {
        String canonical = lengthPrefixed(spaceId)
                + lengthPrefixed(normalizeUserId(userId))
                + lengthPrefixed(sessionId)
                + lengthPrefixed(key);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            digest[6] = (byte) ((digest[6] & 0x0f) | 0x50);
            digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
            ByteBuffer bytes = ByteBuffer.wrap(digest);
            return new UUID(bytes.getLong(), bytes.getLong()).toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String lengthPrefixed(String value) {
        return value.length() + ":" + value;
    }

    private static void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }

    /**
     * 构建逻辑键: {normalizedUserId}|{sessionId}|{key}
     */
    private String buildLogicalKey(String userId, String sessionId, String key) {
        validateSessionId(sessionId);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encodeKeyPart(encoder, normalizeUserId(userId)) + "."
                + encodeKeyPart(encoder, sessionId) + "."
                + encodeKeyPart(encoder, key);
    }

    private static String encodeKeyPart(Base64.Encoder encoder, String value) {
        return encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeUserId(String userId) {
        return (userId == null) ? "__anon__" : userId;
    }

    /**
     * 从 MessageInfo 中提取文本内容。
     *
     * <p>每个 part 既可能是结构化对象（{@code {"type":"text","text":...}}），
     * 也可能是后端在内容加密不可读时返回的字符串标记（如 {@code "_encrypted"}）。
     * 非 Map 的 part 一律跳过。</p>
     */
    @SuppressWarnings("unchecked")
    private String extractText(MessageInfo msg) {
        if (msg == null || msg.getParts() == null) return null;
        StringBuilder sb = new StringBuilder();
        for (Object part : msg.getParts()) {
            if (!(part instanceof Map)) continue;
            Map<String, Object> p = (Map<String, Object>) part;
            if ("text".equals(p.get("type")) && p.get("text") != null) {
                sb.append(p.get("text"));
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static final class IndexSnapshot {
        private final Map<String, Map<String, Set<String>>> keysByUserAndSession = new HashMap<>();

        private void upsert(String userId, String sessionId, String key) {
            keysByUserAndSession
                    .computeIfAbsent(userId, ignored -> new HashMap<>())
                    .computeIfAbsent(sessionId, ignored -> new HashSet<>())
                    .add(key);
        }

        private void deleteKey(String userId, String sessionId, String key) {
            Map<String, Set<String>> sessions = keysByUserAndSession.get(userId);
            if (sessions == null) return;
            Set<String> keys = sessions.get(sessionId);
            if (keys == null) return;
            keys.remove(key);
            if (keys.isEmpty()) sessions.remove(sessionId);
            if (sessions.isEmpty()) keysByUserAndSession.remove(userId);
        }

        private void deleteSession(String userId, String sessionId) {
            Map<String, Set<String>> sessions = keysByUserAndSession.get(userId);
            if (sessions == null) return;
            sessions.remove(sessionId);
            if (sessions.isEmpty()) keysByUserAndSession.remove(userId);
        }

        private Set<String> keys(String userId, String sessionId) {
            Map<String, Set<String>> sessions = keysByUserAndSession.get(userId);
            if (sessions == null) return Set.of();
            Set<String> keys = sessions.get(sessionId);
            return keys == null ? Set.of() : Set.copyOf(keys);
        }

        private Set<String> sessionIds(String userId) {
            Map<String, Set<String>> sessions = keysByUserAndSession.get(userId);
            return sessions == null ? Set.of() : Set.copyOf(sessions.keySet());
        }
    }

    private record ListBatch(String id, int count) {
        private ListBatch {
            if (count < 0) {
                throw new AgentStateStoreException(
                        "AgentScope state list batch count must not be negative");
            }
        }
    }
}
