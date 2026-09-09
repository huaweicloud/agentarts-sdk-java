package com.huaweicloud.agentarts.sdk.memory;

import com.huaweicloud.agentarts.sdk.memory.model.MessageBatchResponse;
import com.huaweicloud.agentarts.sdk.memory.model.SessionInfo;
import com.huaweicloud.agentarts.sdk.memory.model.SpaceInfo;
import com.huaweicloud.agentarts.sdk.memory.model.TextMessage;

import java.util.List;
import java.util.Map;

/**
 * Standalone end-to-end check for add_messages against the real Memory data plane.
 *
 * <p>Proves: when you pass {@link TextMessage} objects, the SDK converts them to
 * the OpenAPI "parts" format and the backend accepts them. Also reproduces the
 * client-side "Unsupported message type: java.util.LinkedHashMap" error that
 * occurs when raw Maps are passed instead of typed message objects.
 *
 * <p>Requires env vars: HUAWEICLOUD_SDK_AK/SK/REGION (see agentarts-e2e.env).
 */
public class MemoryAddMessagesE2E {

    public static void main(String[] args) {
        String ak = System.getenv("HUAWEICLOUD_SDK_AK");
        String sk = System.getenv("HUAWEICLOUD_SDK_SK");
        String region = System.getenv().getOrDefault("HUAWEICLOUD_SDK_REGION", "cn-southwest-2");
        if (ak == null || ak.isBlank() || sk == null || sk.isBlank()) {
            System.err.println("FAIL: set HUAWEICLOUD_SDK_AK and HUAWEICLOUD_SDK_SK.");
            System.exit(2);
        }

        MemoryClient controlClient = new MemoryClient(region, null);
        SpaceInfo space = null;
        try {
            // 1. Create a space + capture the data-plane api key.
            space = controlClient.createSpace(
                    "e2e-addmsg-" + System.currentTimeMillis(),
                    168, "e2e: add_messages verification",
                    null, null, null, null, true, null, null, List.of("semantic"), null);
            if (space == null) { System.err.println("FAIL: createSpace returned null"); System.exit(1); }
            String apiKey = space.getApiKey() != null ? space.getApiKey().toString() : null;
            System.out.println("createSpace OK -> space=" + space.getId() + " apiKey=" + (apiKey != null ? "(set)" : "null"));

            // 2. Data-plane client + session.
            MemoryClient dataClient = new MemoryClient(region, apiKey);
            SessionInfo session = dataClient.createMemorySession(space.getId(), null, "e2e-actor", null);
            System.out.println("createMemorySession OK -> session=" + session.getId());

            // 3. addMessages with TextMessage (the CORRECT usage) — should succeed.
            try {
                MessageBatchResponse resp = dataClient.addMessages(
                        space.getId(), session.getId(),
                        List.of(new TextMessage("user", "hello from e2e")),
                        null, null, true);
                System.out.println("addMessages(TextMessage) OK -> items=" + (resp == null ? null : resp.getItems().size()));
                System.out.println("PASS: TextMessage path works; SDK sent the 'parts' format the backend expects.");
            } catch (Exception e) {
                System.out.println("addMessages(TextMessage) FAILED -> " + e);
            }

            // 4. Reproduce the user's error: pass a raw Map (LinkedHashMap).
            try {
                dataClient.addMessages(
                        space.getId(), session.getId(),
                        List.of(Map.of("role", "user", "content", "hello")),
                        null, null, true);
                System.out.println("UNEXPECTED: raw-Map addMessages did not throw.");
            } catch (IllegalArgumentException e) {
                System.out.println("REPRODUCED (client-side) -> " + e.getMessage());
                System.out.println("  => This is NOT a backend 500; the SDK rejects raw Maps in buildAddMessagesRequest.");
                System.out.println("  => Fix: pass new TextMessage(role, content) instead of a Map.");
            }
        } catch (Exception e) {
            System.err.println("FAIL: " + e);
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (space != null && space.getId() != null) {
                try { controlClient.deleteSpace(space.getId()); System.out.println("cleanup: deleted space " + space.getId()); }
                catch (Exception e) { System.err.println("cleanup failed: " + e.getMessage()); }
            }
        }
    }
}
