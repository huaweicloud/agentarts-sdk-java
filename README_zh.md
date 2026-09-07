# 华为云 AgentArts Java SDK

[English](README.md) | **[中文](README_zh.md)**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17%2B-brightgreen.svg)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-orange.svg)](https://maven.apache.org/)

借助华为云能力构建、部署和管理 AI Agent。

## 概述

AgentArts Java SDK 是一套用于在华为云上开发、部署和管理 AI Agent 的工具包。你只需实现 Agent 逻辑,SDK 提供 HTTP 运行时、云上认证、内置工具(记忆、代码解释器、网关)以及覆盖全生命周期的 CLI。它与 [agentscope-java](https://github.com/agentscope-ai/agentscope-java) 原生集成,并提供可选的 Spring Boot starter。

> **`agentarts` CLI 由 [Python SDK](https://github.com/huaweicloud/agentarts-sdk-python) 提供。**
> 本 Java 仓库不再自带 CLI。安装一次 CLI(独立二进制或 `pip`),即可驱动全生命周期 —— 包括 Java agent —— 通过 `init`、`dev`、`deploy`、`invoke`、`destroy`。见下方[安装 CLI](#安装-cli)。

### 核心特性

- **把任意 Agent 包装成 HTTP 服务** —— 通过 `AgentArtsRuntimeApp` 暴露 `POST /invocations`(JSON + SSE 流式)、`GET /ping`(健康检查)、`WS /ws`(WebSocket)
- **一条命令覆盖全生命周期** —— 通过 `agentarts` CLI(从 Python SDK 安装)完成 `init`/`dev`/`deploy`/`invoke`/`destroy`
- **内置云上工具** —— Memory(对话记忆)、Code Interpreter(沙箱执行)、Gateway(网关与 target CRUD)
- **云上身份** —— 工作负载身份、AK/SK 签名、API Key 与 STS 凭证提供者
- **AgentScope 集成** —— 请求级上下文、按会话限界执行、`@Tool` 适配器、消息转换
- **Spring Boot Starter** —— `AgentArtsRuntimeApp` 自动配置 + Actuator 健康指标
- **响应式** —— Vert.x + Project Reactor 流式,带背压与取消

## 模块结构

```
agentarts-sdk-java/
├── agentarts-sdk-bom/                    # 依赖版本管理(BOM)
├── agentarts-sdk-core/                   # 常量、V11 签名、认证注解、配置
├── agentarts-sdk-service/                # 基础 HTTP 客户端、IAM/SWR/runtime 服务客户端
├── agentarts-sdk-runtime/                # Vert.x 运行时服务:/invocations、/ping、/ws、SSE
├── agentarts-sdk-identity/               # 身份客户端、凭证提供者、令牌轮询
├── agentarts-sdk-memory/                 # Memory 客户端(控制面 + 数据面)、MemorySession
├── agentarts-sdk-tools/                  # 代码解释器客户端、CodeSession
├── agentarts-sdk-gateway/                # 网关客户端、网关/target CRUD 模型
├── agentarts-sdk-integration-agentscope/ # AgentScope 桥接、会话、工具
├── agentarts-spring-boot-starter/        # Spring Boot 自动配置 + 健康指标
├── agentarts-sdk-all/                    # 聚合模块(全部模块)
├── agentarts-sdk-examples/               # 可运行示例项目
└── agentarts-sdk-tests/                  # 跨模块与可选云上 E2E 测试
```

## 安装 CLI

`agentarts` CLI 是 Agent 全生命周期的统一入口,维护在 [Python SDK 仓库](https://github.com/huaweicloud/agentarts-sdk-python)。它自带 Python 解释器,**使用时无需安装 Python**。对 Java agent,它会调用你本机的 JDK + Maven。

**Linux / macOS 一键安装:**

```bash
curl -fsSL https://raw.githubusercontent.com/huaweicloud/agentarts-sdk-python/main/install.sh | sh
```

**其他方式**(Windows、手动下载、或 `pip install agentarts-sdk`):见 [Python SDK README](https://github.com/huaweicloud/agentarts-sdk-python#installation)。

## 把你的 Agent 包装成 HTTP 服务

`AgentArtsRuntimeApp` 把你的 Agent 逻辑包装成标准 HTTP 服务。你只实现处理函数,SDK 负责 HTTP、请求解析与响应格式化。

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.huaweicloud.agentarts</groupId>
    <artifactId>agentarts-sdk-runtime</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. 实现 Agent

```java
import com.huaweicloud.agentarts.sdk.core.PingStatus;
import com.huaweicloud.agentarts.sdk.runtime.AgentArtsRuntimeApp;

import java.util.Map;

public class MyAgent {

    private final AgentArtsRuntimeApp app = new AgentArtsRuntimeApp();

    public Map<String, Object> handle(Map<String, Object> payload) {
        String message = (String) payload.get("message");
        // 你的 Agent 逻辑
        return Map.of("result", "Hello from Java agent: " + message);
    }

    public PingStatus healthCheck() {
        return PingStatus.HEALTHY;
    }

    public static void main(String[] args) {
        MyAgent agent = new MyAgent();
        // 编程式注册。(`agentarts dev` 可自动装配 @Entrypoint / @Ping 注解)
        agent.app.setEntrypoint(agent::handle);
        agent.app.setPingHandler(agent::healthCheck);
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String host = System.getenv().getOrDefault("HOST", "0.0.0.0");
        agent.app.runUntilShutdown(port, host);
    }
}
```

### 3. 运行与测试

```bash
# 用 CLI(推荐)—— 编译项目并运行 agent
agentarts dev

# 或直接运行
mvn compile exec:java -Dexec.mainClass="MyAgent"
```

```bash
# 健康检查
curl http://localhost:8080/ping
# {"status":"Healthy","time_of_last_update":1719667200}

# 调用
curl -X POST http://localhost:8080/invocations \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello!"}'
# {"result":"Hello from Java agent: Hello!"}
```

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.9+
- `agentarts` CLI(见[安装 CLI](#安装-cli))
- 华为云账号及 AK/SK 凭证

```bash
export HUAWEICLOUD_SDK_AK="your-access-key"
export HUAWEICLOUD_SDK_SK="your-secret-key"
export HUAWEICLOUD_SDK_REGION="cn-southwest-2"
```

> 在 [华为云控制台](https://console.huaweicloud.com/) → 我的凭证 → 访问密钥 获取 AK/SK。

### 1. 初始化项目

```bash
# Java 项目(Maven + JDK17 + agentarts-sdk-runtime)
agentarts init -n my-agent -t java-basic -r cn-southwest-2
# 基于 AgentScope 的 Java agent:
# agentarts init -n my-agent -t java-agentscope -r cn-southwest-2
```

这会生成 `my-agent/` 目录,含 `pom.xml`、脚手架 `Agent.java`、`.agentarts_config.yaml`(`language: java17`)。

### 2. 本地开发

```bash
agentarts dev -p 8080
# CLI 会执行 `mvn compile`,启动子 JVM,并监听源码变更做热重载。
# 服务运行于 http://127.0.0.1:8080
#   POST /invocations  — 调用 agent
#   GET  /ping         — 健康检查
```

### 3. 配置

```bash
agentarts config set base.region cn-north-4
agentarts config set-env LOG_LEVEL INFO
agentarts config list
```

> 已经有自己的 Java 项目?跳过 `init`,直接运行
> `agentarts config -n my-agent -e com.example.MyAgent --dependency-file pom.xml`,
> 只生成 `.agentarts_config.yaml` + Dockerfile。

### 4. 部署与调用

```bash
agentarts deploy -a my-agent -m cloud -t v1.0
agentarts invoke '{"message": "Hello!"}' -a my-agent -m local -p 8080
agentarts destroy -a my-agent -y
```

## CLI 命令参考

`agentarts` CLI(来自 Python SDK)同时支持 Python 与 Java agent。对 Java agent,它识别 `language: java17` 并调用 Maven/JDK。

| 命令 | 说明 |
| --- | --- |
| `agentarts init` | 初始化新项目(Java 用 `-t java-basic` / `-t java-agentscope`) |
| `agentarts dev` | 启动本地开发服务(编译 + 运行你的 agent,热重载) |
| `agentarts config` | 配置 SDK(别名:`configure`) |
| `agentarts deploy` | 部署 agent 到华为云(别名:`launch`) |
| `agentarts invoke` | 调用 agent |
| `agentarts destroy` | 删除已部署的 agent |
| `agentarts runtime` | 管理运行时资源(6 个子命令) |
| `agentarts gateway` | 管理网关与 target(别名:`mcp-gateway`) |
| `agentarts memory` | 管理 Memory Space 与会话(6 个子命令) |

完整 CLI 文档见 [Python SDK 仓库](https://github.com/huaweicloud/agentarts-sdk-python#readme)。

## AgentScope 集成

基于 [agentscope-java](https://github.com/agentscope-ai/agentscope-java) 构建的 agent,使用集成模块 —— 它把 AgentArts HTTP 运行时与 AgentScope 的 `ReActAgent`、请求级上下文、按会话限界执行、`@Tool` 适配器桥接起来。

```xml
<dependency>
    <groupId>com.huaweicloud.agentarts</groupId>
    <artifactId>agentarts-sdk-integration-agentscope</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.session.InMemorySession;
import io.agentscope.core.session.Session;
import io.agentscope.core.tool.Toolkit;
import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeRuntimeHost;
import com.huaweicloud.agentarts.sdk.integration.agentscope.runtime.AgentscopeSessionExecutor;
import com.huaweicloud.agentarts.sdk.runtime.AgentArtsRuntimeApp;

OpenAIChatModel model = OpenAIChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName(System.getenv().getOrDefault("OPENAI_MODEL_NAME", "gpt-4o"))
    .stream(true)
    .build();
Toolkit toolkit = new Toolkit();
Session session = new InMemorySession();
AgentscopeSessionExecutor executor = new AgentscopeSessionExecutor(session);

AgentArtsRuntimeApp app = new AgentArtsRuntimeApp();
new AgentscopeRuntimeHost(app, (payload, ctx) -> {
    String message = (String) payload.getOrDefault("message", "");
    ReActAgent agent = ReActAgent.builder()
        .name("my-agent")
        .sysPrompt("You are a helpful assistant.")
        .model(model)
        .toolkit(toolkit.copy())
        .toolExecutionContext(ctx.toToolExecutionContext())
        .maxIters(8)
        .build();
    Msg reply = executor.call(agent, message, ctx);
    return java.util.Map.of("reply", reply.getTextContent());
});
app.registerManagedResource(session::close);
app.runUntilShutdown(8080);
```

完整可运行示例(交互式流式、多轮记忆、`@Tool` 注解工具、服务模式)见 [`AgentScopeIntegrationExample.java`](agentarts-sdk-examples/src/main/java/com/huaweicloud/agentarts/examples/AgentScopeIntegrationExample.java)。

## Spring Boot Starter

Spring Boot 3.x 应用可使用可选 starter:

```xml
<dependency>
    <groupId>com.huaweicloud.agentarts</groupId>
    <artifactId>agentarts-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
# application.yml
agentarts:
  region: cn-southwest-2
  runtime:
    port: 8080
    max-concurrency: 15
    auto-start: true
  memory:
    api-key: ${HUAWEICLOUD_SDK_MEMORY_API_KEY}
    space-id: ${AGENTARTS_MEMORY_SPACE_ID}
```

starter 自动配置 `AgentArtsRuntimeApp`,并在 `/actuator/health` 提供 Actuator 健康指标。

## 配置

### 环境变量

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `HUAWEICLOUD_SDK_AK` | 访问密钥 ID | — |
| `HUAWEICLOUD_SDK_SK` | 访问密钥(Secret) | — |
| `HUAWEICLOUD_SDK_REGION` | 华为云区域 | `cn-southwest-2` |
| `HUAWEICLOUD_SDK_MEMORY_API_KEY` | Memory 数据面 API Key | — |
| `HUAWEICLOUD_SDK_CODE_INTERPRETER_API_KEY` | 代码解释器数据面 API Key | — |
| `AGENTARTS_CONTROL_ENDPOINT` | 自定义控制面端点 | 自动 |
| `AGENTARTS_RUNTIME_DATA_ENDPOINT` | 自定义数据面端点 | — |

完整优先级链与 Java 常量映射见[环境变量指南](docs/cn/sdk_user_guide/environment_variables.md)。

### `.agentarts_config.yaml`

```yaml
default_agent: my-agent
agents:
  my-agent:
    base:
      name: my-agent
      entrypoint: com.example.MyAgent
      region: cn-southwest-2
      language: java17
      base_image: eclipse-temurin:17-jre
      dependency_file: pom.xml
    swr_config:
      organization: my-org
      repository: my-agent
    runtime:
      invoke_config:
        protocol: HTTP
        port: 8080
```

## 内置云上工具

| 工具 | 模块 | 作用 |
| --- | --- | --- |
| **Memory** | `agentarts-sdk-memory` | 对话记忆:space/session/message CRUD、带过滤检索、`MemorySession` 封装(控制面用 AK/SK,数据面用 API Key) |
| **Code Interpreter** | `agentarts-sdk-tools` | 沙箱执行:运行代码/命令、上传下载文件、安装包 —— `CodeSession` 自动关闭 |
| **Gateway** | `agentarts-sdk-gateway` | 网关与 target CRUD,AK/SK 签名请求 |
| **Identity** | `agentarts-sdk-identity` | 工作负载身份、访问令牌(forUserId/forJwt)、OAuth2/API Key/STS 提供者 |

API 细节与示例见 [SDK 用户指南](docs/cn/sdk_user_guide)。

## 示例

`agentarts-sdk-examples` 模块含可运行项目:

```bash
# 基础 Vert.x 运行时 agent(同步 + SSE 流式)
mvn compile exec:java -pl agentarts-sdk-examples \
  -Dexec.mainClass="com.huaweicloud.agentarts.examples.BasicRuntimeExample"

# AgentScope 集成 demo(流式、工具、多轮记忆)
export HUAWEICLOUD_SDK_MEMORY_API_KEY=your-api-key
export AGENTARTS_MEMORY_SPACE_ID=your-space-id
mvn compile exec:java -pl agentarts-sdk-examples \
  -Dexec.mainClass="com.huaweicloud.agentarts.examples.AgentScopeIntegrationExample"
```

## 从源码构建

```bash
git clone https://github.com/huaweicloud/agentarts-sdk-java.git
cd agentarts-sdk-java
mvn clean verify
```

云上 E2E 测试是可选的,需要真实凭证;一次绿色的 `mvn verify` 不会触发云上行为。安全分级、环境变量与结果解读见 [E2E 测试指南](docs/cn/e2e_testing_guide.md)。**切勿提交或打印真实凭证** —— 通过环境变量或 CI secret store 注入。

## 文档

- [SDK 用户指南(中文)](docs/cn/sdk_user_guide) —— Runtime、Memory、Code Interpreter、Gateway、Identity
- [E2E 测试指南(中文)](docs/cn/e2e_testing_guide.md) —— 三级安全模型与结果解读
- [CLI(Python SDK)](https://github.com/huaweicloud/agentarts-sdk-python#readme) —— `agentarts` CLI 安装、命令、Java agent 支持

## 许可证

[Apache License 2.0](LICENSE)

## 支持

- **Issues:** [GitHub Issues](https://github.com/huaweicloud/agentarts-sdk-java/issues)
- **文档:** <https://support.huaweicloud.com/productdesc-agentarts/agentarts_03_0002.html>
- **邮箱:** <hwagentartssdk@huawei.com>
