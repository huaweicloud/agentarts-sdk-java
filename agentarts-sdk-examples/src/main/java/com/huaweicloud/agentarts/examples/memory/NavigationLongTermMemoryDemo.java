package com.huaweicloud.agentarts.examples.memory;

import com.huaweicloud.agentarts.sdk.integration.agentscope.memory.AgentArtsLongTermMemory;
import com.huaweicloud.agentarts.sdk.integration.agentscope.memory.InMemoryLongTermMemory;
import com.huaweicloud.agentarts.sdk.memory.MemoryClient;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.extensions.model.openai.OpenAIChatModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 导航场景 Demo —— agentscope 原生 {@code LongTermMemory} 接入 AgentArts 云上 Memory。
 *
 * <p>核心适配器是 {@link AgentArtsLongTermMemory}（implements agentscope
 * {@code io.agentscope.core.memory.LongTermMemory}）：
 * {@code record(List<Msg>)} → 云上 {@code addMessages}（触发自动抽取），
 * {@code retrieve(Msg)} → 云上 {@code searchMemories}（按 actor 跨会话召回）。</p>
 *
 * <h3>双路径，确保始终可执行</h3>
 * <ul>
 *   <li><b>离线路径（默认，无 LLM / 无云凭据即可跑）</b>：直接驱动 LongTermMemory 的
 *       record/retrieve，展现"写入对话 → 抽取 → 跨会话召回 → 注入"链路。</li>
 *   <li><b>LLM 路径（设 OPENAI_API_KEY 启用）</b>：用真实 ReActAgent +
 *       {@code .longTermMemory(...).longTermMemoryMode(STATIC_CONTROL)}，
 *       由 agentscope 原生 hook 自动 record/retrieve 并注入 system prompt。</li>
 * </ul>
 *
 * <h3>运行</h3>
 * <pre>
 * # 离线（开箱即跑）
 * mvn compile exec:java -pl agentarts-sdk-examples \
 *   -Dexec.mainClass="com.huaweicloud.agentarts.examples.memory.NavigationLongTermMemoryDemo"
 *
 * # 上云（真连 AgentArts Memory，离线路径自动切到云上 LTM）
 * export AGENTARTS_MEMORY_API_KEY=your-api-key
 * export AGENTARTS_MEMORY_SPACE_ID=your-space-id
 *
 * # LLM 路径（真实 ReActAgent + 原生长期记忆 hook）
 * export OPENAI_API_KEY=sk-xxx
 * export AGENTARTS_MEMORY_API_KEY=your-api-key
 * export AGENTARTS_MEMORY_SPACE_ID=your-space-id
 * </pre>
 */
public class NavigationLongTermMemoryDemo {

    private static final String ACTOR_ID = "user-zhangsan";

    public static void main(String[] args) {
        boolean useLlm = System.getenv("OPENAI_API_KEY") != null
                && !System.getenv("OPENAI_API_KEY").isEmpty();
        boolean hasCloud = env("AGENTARTS_MEMORY_API_KEY") != null
                && env("AGENTARTS_MEMORY_SPACE_ID") != null;

        System.out.println("=".repeat(64));
        System.out.println("agentscope LongTermMemory × AgentArts Memory · 导航场景 Demo");
        System.out.println("=".repeat(64));
        System.out.println("长期记忆后端: " + (hasCloud
                ? "云上 AgentArtsMemory（AgentArtsLongTermMemory）"
                : "离线 InMemoryLongTermMemory（设 AGENTARTS_MEMORY_API_KEY + SPACE_ID 上云）"));
        System.out.println("Agent 模式: " + (useLlm
                ? "真实 ReActAgent + STATIC_CONTROL（需 LLM，已检测到 OPENAI_API_KEY）"
                : "脚本驱动 record/retrieve（设 OPENAI_API_KEY 启用真实 Agent）"));
        System.out.println("Actor: " + ACTOR_ID);
        System.out.println("=".repeat(64));

        if (useLlm) {
            runWithRealAgent(hasCloud);
        } else {
            runScripted(hasCloud);
        }
    }

    // ========================================================================
    // 离线路径：直接驱动 LongTermMemory.record / retrieve（无需 LLM）
    // ========================================================================

    private static void runScripted(boolean hasCloud) {
        // ===== 会话 A：多轮，体现短期记忆（同会话上下文）=====
        System.out.println("\n########## 会话 A（多轮）：短期记忆（同会话上下文）##########");
        LongTermMemory ltmA = newLongTermMemory(hasCloud, ACTOR_ID);
        List<String> sessionABuffer = new ArrayList<>(); // 模拟短期对话缓冲（对应 AgentState.context）

        // A 轮1：自报画像 → record 到长期记忆 + 进短期缓冲
        scriptedTurn(ltmA, sessionABuffer,
                "我叫张三，我家在朝阳公园南门，公司在国贸，偏好避开高速公路。",
                "好的，已记下家/公司位置与避开高速的偏好。", true);

        // A 轮2（同会话）：问"我刚才说的公司地址" → 用短期缓冲回答（同会话上下文，无需跨会话召回）
        scriptedTurn(ltmA, sessionABuffer,
                "我刚才说的公司地址是哪？",
                null, true);

        // 云上抽取是异步的：等抽取完成，为会话 B 的长期召回做准备
        if (hasCloud) {
            System.out.println("\n⏳ 等待云上抽取（轮询，最长 60s）...");
            String got = waitForRecall(ltmA,
                    Msg.builder().role(MsgRole.USER).textContent("导航去公司").build(), 60000, 2000);
            System.out.println(got == null || got.isEmpty() ? "   （超时）" : "   抽取就绪 ✓");
        }

        // ===== 会话 B：新会话，体现长期记忆（跨会话召回）=====
        System.out.println("\n########## 会话 B（新会话）：长期记忆（跨会话召回）##########");
        LongTermMemory ltmB = newLongTermMemory(hasCloud, ACTOR_ID); // 新实例、新缓冲
        recallAndAnswer(ltmB, "导航去公司", "国贸");
        recallAndAnswer(ltmB, "回家", "朝阳公园");

        // ===== 对照：另一 actor，无长期记忆 =====
        System.out.println("\n########## 对照：另一用户，无长期记忆 ##########");
        LongTermMemory ltmC = newLongTermMemory(hasCloud, "user-lisi");
        String out = ltmC.retrieve(Msg.builder().role(MsgRole.USER).textContent("导航去公司").build()).block();
        System.out.println("👤 用户(lisi): 导航去公司");
        System.out.println("🧠 retrieve(): " + (out == null || out.isEmpty() ? "（无）" : "\n" + out));
        System.out.println("🤖 助手: 我还没有您的目的地信息，请先告诉我您常去的地方。");
    }

    /** 脚本驱动单轮：进短期缓冲；若问"刚才/前面"则从本会话缓冲答（短期），否则回 ack 并 record（长期）。 */
    private static void scriptedTurn(LongTermMemory ltm, List<String> buffer,
                                     String userMsg, String ackReply, boolean record) {
        System.out.println("\n👤 用户: " + userMsg);
        buffer.add(userMsg);
        if (userMsg.contains("刚才") || userMsg.contains("前面")) {
            // 短期记忆：同会话上下文
            String company = extractFromBuffer(buffer, "公司在");
            if (company != null) {
                System.out.println("🧠 短期记忆(本会话上下文): 本会话历史里有 \"公司在" + company + "\"");
                System.out.println("🤖 助手: 您刚才说的公司地址是" + company
                        + "。（来自本会话上下文，无需跨会话召回）");
            } else {
                System.out.println("🧠 短期记忆(本会话上下文): （本会话未提及）");
                System.out.println("🤖 助手: 本会话里没有提到公司地址。");
            }
        } else if (ackReply != null) {
            System.out.println("🤖 助手: " + ackReply);
        }
        if (record) {
            ltm.record(List.of(Msg.builder().role(MsgRole.USER).textContent(userMsg).build())).block();
            System.out.println("  ↳ record() → 长期记忆（云上触发抽取）");
        }
    }

    /** 从缓冲里找 marker（如"公司在"）后的地点，取到首个标点。 */
    private static String extractFromBuffer(List<String> buffer, String marker) {
        for (String msg : buffer) {
            int idx = msg.indexOf(marker);
            if (idx < 0) continue;
            String rest = msg.substring(idx + marker.length());
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (c == '，' || c == ',' || c == '。' || c == '.'
                        || c == '；' || c == ';') {
                    return rest.substring(0, i).trim();
                }
            }
            return rest.trim();
        }
        return null;
    }

    private static void recallAndAnswer(LongTermMemory ltm, String query, String expectKey) {
        System.out.println("\n👤 用户: " + query);
        Msg queryMsg = Msg.builder().role(MsgRole.USER).textContent(query).build();
        String recalled = ltm.retrieve(queryMsg).block();
        int n = (recalled == null || recalled.isEmpty()) ? 0 : recalled.split("\n").length;
        System.out.println("  🧠 长期记忆 retrieve → " + (n == 0 ? "无相关记忆" : "召回 " + n + " 条"));
        // 脚本演示：召回结果如何影响导航决策
        boolean hasInfo = recalled != null && recalled.contains(expectKey);
        boolean avoidHighway = recalled != null && recalled.contains("避开");
        if (!hasInfo) {
            System.out.println("🤖 助手: 我还没有您的目的地信息，请先告诉我您常去的地方。");
            return;
        }
        String dest = query.contains("公司") ? "公司" : "家";
        String verb = query.contains("回家") ? "为您导航回家" : "正在为您导航前往" + dest;
        String reply = verb + "（" + expectKey + "）。"
                + (avoidHighway ? "已按您的偏好避开高速公路，改走地面道路，" : "")
                + "预计到达。";
        System.out.println("🤖 助手: " + reply);
    }

    // ========================================================================
    // LLM 路径：真实 ReActAgent + 原生 LongTermMemory（STATIC_CONTROL）
    // ========================================================================

    private static void runWithRealAgent(boolean hasCloud) {
        // 模型：OpenAIChatModel 支持 baseUrl 对接 OpenAI / 华为云 MaaS / DeepSeek 等
        OpenAIChatModel.Builder modelBuilder = OpenAIChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(envOrDefault("OPENAI_MODEL_NAME", "gpt-4o"))
                .stream(true);
        String baseUrl = System.getenv("OPENAI_BASE_URL");
        if (baseUrl != null && !baseUrl.isEmpty()) {
            modelBuilder.baseUrl(baseUrl);
        }
        OpenAIChatModel model = modelBuilder.build();

        // 统一构造：云上则共享一个 MemoryClient，离线则用 agentscope 自带 InMemory 实现
        MemoryClient cloudClient = hasCloud ? buildMemoryClient(true) : null;
        String spaceId = spaceId(hasCloud);

        LongTermMemory raw = hasCloud
                ? new AgentArtsLongTermMemory(cloudClient, spaceId, ACTOR_ID)
                : new InMemoryLongTermMemory(ACTOR_ID);
        // 套日志装饰器：让 agentscope Hook 对 record/retrieve 的调用在运行时可见
        LongTermMemory ltm = new LoggingLongTermMemory(raw, hasCloud ? "AgentArtsLTM" : "InMemoryLTM");

        io.agentscope.core.state.AgentStateStore stateStore = new LoggingAgentStateStore(
                hasCloud
                        ? new com.huaweicloud.agentarts.sdk.integration.agentscope.state
                                .MemoryAgentStateStore(cloudClient, spaceId)
                        : new io.agentscope.core.state.InMemoryAgentStateStore());

        io.agentscope.core.ReActAgent agent = io.agentscope.core.ReActAgent.builder()
                .name("nav-agent")
                .sysPrompt("你是一个导航助手。会根据已召回的长期记忆（家/公司位置、偏好）给出路线。")
                .model(model)
                .toolkit(new io.agentscope.core.tool.Toolkit())
                .stateStore(stateStore)
                // ★ 长期记忆：接入 AgentArts。STATIC_CONTROL 模式下，agentscope 原生 hook
                //   自动 record(每轮对话→云上抽取) + retrieve(按 actor 召回→appendSystemContent 注入)
                .longTermMemory(ltm)
                .longTermMemoryMode(io.agentscope.core.memory.LongTermMemoryMode.STATIC_CONTROL)
                .maxIters(5)
                .build();

        io.agentscope.core.agent.RuntimeContext ctxA = io.agentscope.core.agent.RuntimeContext.builder()
                .userId(ACTOR_ID).sessionId("nav-session-a").build();

        try {
            // ===== 会话 A（多轮）：短期记忆（同会话上下文）=====
            System.out.println("\n########## 会话 A（多轮）：短期记忆（同会话上下文）##########");

            // A 轮1：自报画像（会被 record 到云上并抽取）
            System.out.println("\n👤 用户: 我家在朝阳公园南门，公司在国贸，偏好避开高速公路。");
            Msg r1 = agent.call("我家在朝阳公园南门，公司在国贸，偏好避开高速公路。", ctxA).block();
            System.out.println("🤖 助手: " + (r1 != null ? r1.getTextContent() : "(无回复)"));

            // A 轮2（同会话、同 ctx）：问"刚才说的公司地址" → 此时云上抽取可能尚未完成、
            // Hook 的 retrieve 可能为空，但 Agent 的同会话上下文(AgentState.context 短期记忆)里有第1轮内容，
            // 模型据此回答——体现短期记忆（无需跨会话召回）。
            System.out.println("\n👤 用户: 我刚才说的公司地址是哪？");
            Msg r2 = agent.call("我刚才说的公司地址是哪？", ctxA).block();
            System.out.println("🤖 助手: " + (r2 != null ? r2.getTextContent() : "(无回复)"));

            // 云上抽取是异步的：等抽取完成，为会话 B 的长期召回做准备
            System.out.println("\n⏳ 等待云上抽取（轮询，最长 60s）...");
            String got = waitForRecall(raw,
                    Msg.builder().role(MsgRole.USER).textContent("导航去公司").build(),
                    60000, 2000);
            System.out.println(got == null || got.isEmpty()
                    ? "   （超时仍未召回，会话 B retrieve 可能依赖 Hook 实时结果）"
                    : "   抽取已就绪 ✓");

            // ===== 会话 B（新会话）：长期记忆（跨会话召回）=====
            System.out.println("\n########## 会话 B（新会话）：长期记忆（跨会话召回）##########");
            io.agentscope.core.agent.RuntimeContext ctxB = io.agentscope.core.agent.RuntimeContext.builder()
                    .userId(ACTOR_ID).sessionId("nav-session-b").build();
            System.out.println("\n👤 用户: 导航去公司");
            Msg r3 = agent.call("导航去公司", ctxB).block();
            System.out.println("🤖 助手: " + (r3 != null ? r3.getTextContent() : "(无回复)"));
        } finally {
            agent.close();
            if (cloudClient != null) {
                cloudClient.close();
            }
        }
    }

    // ========================================================================
    // 构造工具
    // ========================================================================

    /**
     * 轮询 retrieve 直到非空或超时。demo 胶水代码，处理云上异步抽取的空窗
     * （record 写消息后，云上后台抽取 Memory 有秒~分钟级延迟，立即 retrieve 可能为空）。
     * 离线实现同步返回，首次即命中。属于"使用方"逻辑，不进适配层。
     *
     * @return 召回字符串；超时仍空则返回 ""
     */
    static String waitForRecall(LongTermMemory ltm, Msg query, long timeoutMs, long intervalMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int attempt = 0;
        while (System.currentTimeMillis() < deadline) {
            attempt++;
            String out = ltm.retrieve(query).block();
            if (out != null && !out.isEmpty()) {
                System.out.println("   第 " + attempt + " 次轮询命中 ✓");
                return out;
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return "";
            }
        }
        System.out.println("   超时（" + attempt + " 次轮询）仍未召回");
        return "";
    }

    private static LongTermMemory newLongTermMemory(boolean hasCloud, String actorId) {
        if (hasCloud) {
            return new AgentArtsLongTermMemory(buildMemoryClient(true), spaceId(true), actorId);
        }
        return new InMemoryLongTermMemory(actorId);
    }

    private static MemoryClient buildMemoryClient(boolean hasCloud) {
        if (!hasCloud) {
            return new MemoryClient("cn-southwest-2", "offline-placeholder");
        }
        String region = envOrDefault("HUAWEICLOUD_SDK_REGION", "cn-southwest-2");
        return new MemoryClient(region, System.getenv("AGENTARTS_MEMORY_API_KEY"));
    }

    private static String spaceId(boolean hasCloud) {
        return hasCloud ? System.getenv("AGENTARTS_MEMORY_SPACE_ID") : "offline-space";
    }

    private static String env(String k) {
        String v = System.getenv(k);
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static String envOrDefault(String k, String d) {
        String v = System.getenv(k);
        return (v == null || v.isEmpty()) ? d : v;
    }
}
