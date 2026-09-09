package com.huaweicloud.agentarts.sdk.memory;

import com.huaweicloud.agentarts.sdk.memory.model.SpaceInfo;

import java.util.List;
import java.util.Map;

/**
 * Standalone end-to-end check for the create-space flow against the real
 * AgentArts Memory control plane.
 *
 * <p>Verifies the fix for the deserialization failure on
 * {@code SpaceInfo.memory_strategies_builtin} (server returns a list of
 * objects, not strings).
 *
 * <p>Requires env vars: HUAWEICLOUD_SDK_AK, HUAWEICLOUD_SDK_SK,
 * HUAWEICLOUD_SDK_REGION. Creates a space (with a builtin strategy to
 * populate the field under test), reads it back, then deletes it.
 *
 * <p>Run with: {@code mvn -pl agentarts-sdk-memory -am test-compile
 * exec:java -Dexec.classpathScope=test
 * -Dexec.mainClass=com.huaweicloud.agentarts.sdk.memory.MemoryCreateSpaceE2E}
 */
public class MemoryCreateSpaceE2E {

    public static void main(String[] args) {
        String ak = System.getenv("HUAWEICLOUD_SDK_AK");
        String sk = System.getenv("HUAWEICLOUD_SDK_SK");
        String region = System.getenv().getOrDefault("HUAWEICLOUD_SDK_REGION", "cn-southwest-2");
        if (ak == null || ak.isBlank() || sk == null || sk.isBlank()) {
            System.err.println("FAIL: set HUAWEICLOUD_SDK_AK and HUAWEICLOUD_SDK_SK (and optionally HUAWEICLOUD_SDK_REGION).");
            System.exit(2);
        }

        String name = "e2e-builtin-fix-" + System.currentTimeMillis();
        System.out.println("region = " + region);
        System.out.println("Creating space: " + name);

        MemoryClient client = new MemoryClient(region, null);
        SpaceInfo created = null;
        try {
            created = client.createSpace(
                    name,
                    168,
                    "e2e: verify memory_strategies_builtin deserialization fix",
                    null,               // tags
                    null,               // memoryExtractIdleSeconds
                    null,               // memoryExtractMaxTokens
                    null,               // memoryExtractMaxMessages
                    true,               // publicAccessEnable
                    null, null,         // private vpc/subnet
                    List.of("semantic"), // memoryStrategiesBuiltin — triggers the field under test
                    null);              // memoryStrategiesCustomized

            if (created == null) {
                System.err.println("FAIL: createSpace returned null");
                System.exit(1);
            }
            System.out.println("createSpace OK -> id=" + created.getId()
                    + " name=" + created.getName()
                    + " status=" + created.getStatus());

            // The field under test — this is what used to throw.
            List<Map<String, Object>> builtin = created.getMemoryStrategiesBuiltin();
            System.out.println("memory_strategies_builtin (deserialized) = " + builtin);
            if (builtin != null) {
                System.out.println("  element class = " + (builtin.isEmpty() ? "empty" : builtin.get(0).getClass().getName()));
            }

            // Read it back — exercises the same path on getSpace.
            SpaceInfo fetched = client.getSpace(created.getId());
            System.out.println("getSpace OK -> id=" + (fetched == null ? null : fetched.getId())
                    + " builtin=" + (fetched == null ? null : fetched.getMemoryStrategiesBuiltin()));

            System.out.println("PASS: create/get space succeeded; memory_strategies_builtin deserialized without error.");
        } catch (Exception e) {
            System.err.println("FAIL: " + e);
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (created != null && created.getId() != null) {
                try {
                    client.deleteSpace(created.getId());
                    System.out.println("cleanup: deleted space " + created.getId());
                } catch (Exception e) {
                    System.err.println("cleanup: failed to delete space " + created.getId() + " — " + e.getMessage());
                }
            }
        }
    }
}
