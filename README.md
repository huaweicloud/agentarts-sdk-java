# Huawei Cloud AgentArts SDK for Java

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17%2B-brightgreen.svg)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-orange.svg)](https://maven.apache.org/)
[![Tests](https://img.shields.io/badge/tests-mvn%20verify-blue.svg)]()

Build, deploy and manage AI agents with Huawei Cloud capabilities.

## Overview

AgentArts Java SDK is a comprehensive toolkit for developing, deploying, and managing AI agents on Huawei Cloud. It integrates natively with [agentscope-java](https://github.com/agentscope-ai/agentscope-java) (Reactor-based agent framework). See also the [Python SDK](https://github.com/huaweicloud/agentarts-sdk-python).

### Key Features

- **V11-HMAC-SHA256 Signing** — Full Java implementation of Huawei Cloud V11 signer with HKDF key derivation; dual signing mode support (V11 + SDK-HMAC-SHA256); `@RequireAccessToken` / `@RequireApiKey` / `@RequireStsToken` auth annotations with JDK Proxy interceptor
- **Vert.x Runtime Server** — HTTP server exposing `POST /invocations` (JSON + SSE streaming), `GET /ping` (health check with HEALTHY/HEALTHY_BUSY/UNHEALTHY), `WS /ws` (WebSocket); Semaphore-based concurrency control; `RequestContext` + `AgentArtsRuntimeContext` (7-field ThreadLocal with leak prevention)
- **Cloud Identity** — Workload identity CRUD, access token issuance (forUserId/forJwt), three credential provider types (OAuth2/API Key/STS), resource token retrieval, `TokenPoller` for async OAuth2 flows, `.agent_identity.json` local bootstrap
- **Memory Service** — Dual-plane client (AK/SK control plane + API Key data plane); space/session/message/memory CRUD; `MemorySession` convenience wrapper; OpenAPI "parts" format serialization; search with filters (query, topK, minScore)
- **Code Interpreter** — Sandboxed code execution client; `CodeSession` AutoCloseable context manager; execute code/commands, upload/download files, install packages, clear context
- **Gateway** — Gateway and target CRUD management via AK/SK signed requests (10 API methods)
- **Runtime Client** — Dual-plane client for agent lifecycle management: control plane (AK/SK), data plane invoke, raw NDJSON command streaming, file transfer and session lifecycle; `LocalRuntimeClient` for local development
- **agentscope-java Integration** — stable AgentScope 1.0 API: `AgentscopeRuntimeHost`, request-scoped `AgentscopeRequestContext`, bounded `AgentscopeSessionExecutor`, `MemoryAgentStateStore` implementing `Session`, AgentTool adapters and message conversion
- **CLI Toolkit** — Picocli-based CLI with 9 top-level commands and 30 grouped subcommands; Python-compatible `launch`, `configure`, `gateway` and concise gateway aliases remain available alongside the original Java names
- **Spring Boot Starter** — AutoConfiguration for `AgentArtsRuntimeApp`, `@ConfigurationProperties` binding (`agentarts.*`), Actuator HealthIndicator (UP healthy/busy, UNKNOWN not-started)
- **Bounded Test Suite** — clean local verification executes unit, socket, protocol, CLI and cross-module tests under 30-second per-test, 180-second fork and 15-minute CI limits; cloud tests use an explicit three-tier safety model
- **Reactor API Integration** — the HTTP transport streams SSE/NDJSON with demand control and cancellation; Memory exposes cold, non-blocking Reactor APIs plus synchronous compatibility wrappers

## Module Structure

```
agentarts-sdk-java/
├── agentarts-sdk-bom/                   # Bill of Materials (dependency versions)
├── agentarts-sdk-core/                  # Constants, V11Signer, annotations, config, exceptions
├── agentarts-sdk-service/               # BaseHttpClient, IdentityServiceClient, IAMServiceClient, SWRServiceClient, RuntimeClient
├── agentarts-sdk-runtime/               # Vert.x HttpServer: /invocations, /ping, /ws, SSE, concurrency control
├── agentarts-sdk-identity/              # IdentityClient, auth annotations, TokenPoller, .agent_identity.json
├── agentarts-sdk-memory/                # MemoryClient (dual-plane: control + data), MemorySession
├── agentarts-sdk-tools/                 # CodeInterpreterClient, CodeSession
├── agentarts-sdk-gateway/            # GatewayClient, Gateway/Target/ToolCall models
├── agentarts-sdk-integration-agentscope/# AgentScope 1.0 bridge, Session, bounded execution, tools
├── agentarts-toolkit-cli/               # Picocli CLI + standalone executable JAR
├── agentarts-spring-boot-starter/       # Spring Boot AutoConfiguration + Properties + HealthIndicator
├── agentarts-sdk-all/                   # Aggregator (all modules)
├── agentarts-sdk-examples/              # Example projects (basic-runtime, agentscope-integration)
└── agentarts-sdk-tests/                 # Cross-module and opt-in cloud E2E tests
```

## Documentation

### SDK 用户指南（中文）

| 文档 | 说明 |
|---|---|
| [环境变量配置](docs/cn/sdk_user_guide/environment_variables.md) | 所有环境变量、优先级链、Java 常量对照表 |
| [运行时 SDK](docs/cn/sdk_user_guide/runtime_user_guide.md) | AgentArtsRuntimeApp 服务端 + RuntimeClient 双平面客户端 + LocalRuntimeClient 本地开发 |
| [Memory SDK](docs/cn/sdk_user_guide/memory_user_guide.md) | MemoryClient 双平面架构、MemorySession、完整 API（25+ 方法） |
| [Code Interpreter SDK](docs/cn/sdk_user_guide/tools_user_guide.md) | CodeInterpreterClient、CodeSession、17 种操作方法 |
| [Gateway SDK](docs/cn/sdk_user_guide/gateway_user_guide.md) | GatewayClient 网关/目标 CRUD、RequestResult |
| [Agent Identity SDK](docs/cn/sdk_user_guide/agent_identity_guide.md) | IdentityClient、注解模式、OAuth2/API Key/STS 三种凭证 |

### 测试指南（中文）

| 文档 | 说明 |
|---|---|
| [E2E 测试指南](docs/cn/e2e_testing_guide.md) | 三层安全模型、运行方法、与 Python SDK 的用例逐条对比、覆盖范围、凭证安全约定 |

### CLI 工具指南（中文）

| 文档 | 命令 |
|---|---|
| [**CLI 安装与前置步骤**](docs/cn/toolkit_user_guide/cli_overview.md) | 构建 jar、`bin/agentarts` 启动器、PATH 配置——使用 CLI 前必读 |
| [init](docs/cn/toolkit_user_guide/init.md) | 初始化项目 |
| [config](docs/cn/toolkit_user_guide/config.md) | 配置管理（8 个子命令） |
| [dev](docs/cn/toolkit_user_guide/dev.md) | 本地开发服务器 |
| [deploy](docs/cn/toolkit_user_guide/deploy.md) | 云端/本地部署 |
| [invoke](docs/cn/toolkit_user_guide/invoke.md) | 调用 Agent |
| [destroy](docs/cn/toolkit_user_guide/destroy.md) | 销毁 Agent |
| [runtime](docs/cn/toolkit_user_guide/runtime_cli.md) | 运行时管理（6 个子命令） |
| [gateway / gateway](docs/cn/toolkit_user_guide/gateway_cli.md) | MCP 网关管理（10 个子命令及兼容别名） |

## Quick Start

### Prerequisites

- JDK 17+
- Maven 3.9+
- Huawei Cloud account with AK/SK credentials

### 1. Add Dependencies

```xml
<dependency>
    <groupId>com.huaweicloud.agentarts</groupId>
    <artifactId>agentarts-sdk-runtime</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Create Your Agent

```java
import com.huaweicloud.agentarts.sdk.core.PingStatus;
import com.huaweicloud.agentarts.sdk.runtime.AgentArtsRuntimeApp;

import java.util.Map;

public class MyAgent {

    private final AgentArtsRuntimeApp app = new AgentArtsRuntimeApp();

    public Map<String, Object> handle(Map<String, Object> payload) {
        String message = (String) payload.get("message");
        // Your agent logic here
        return Map.of("result", "Hello from Java agent: " + message);
    }

    public PingStatus healthCheck() {
        return PingStatus.HEALTHY;
    }

    public static void main(String[] args) {
        MyAgent agent = new MyAgent();
        // Programmatic registration. (app.run() does not scan @Entrypoint/@Ping
        // annotations — use `agentarts dev` for annotation-based auto-wiring.)
        agent.app.setEntrypoint(agent::handle);
        agent.app.setPingHandler(agent::healthCheck);
        agent.app.runUntilShutdown(8080);
    }
}
```

### 3. Run Locally

```bash
# Using CLI (when toolkit-cli is built)
agentarts dev

# Or run directly (from your own project where the runtime dependency was added
# in step 1; exec:java resolves via Maven's default org.codehaus.mojo plugin-group)
mvn compile exec:java -Dexec.mainClass="MyAgent"
```

### 4. Test

```bash
# Health check
curl http://localhost:8080/ping
# {"status":"Healthy","time_of_last_update":1719667200}

# Invoke
curl -X POST http://localhost:8080/invocations \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello!"}'
# {"result":"Hello from Java agent: Hello!"}
```

## agentscope-java Integration

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
Session session = new InMemorySession(); // or MemoryAgentStateStore
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

> `AgentscopeIntegrationSnippetTest` verifies construction of `AgentArtsRuntimeApp`, `AgentscopeRuntimeHost`, `ReActAgent` and `OpenAIChatModel`; the `AgentscopeSessionExecutor.call` wiring shown above is exercised by the runnable demo below, not by the snippet test. For that complete demo (interactive streaming, multi-turn memory, `@Tool`-annotated tools, server mode), see [`AgentScopeIntegrationExample.java`](agentarts-sdk-examples/src/main/java/com/huaweicloud/agentarts/examples/AgentScopeIntegrationExample.java).

## Spring Boot Starter

For Spring Boot 3.x applications, use the optional starter for auto-configuration:

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

The starter auto-configures `AgentArtsRuntimeApp` and provides an Actuator health indicator at `/actuator/health`. Note: `agentarts.runtime.auto-start` is honored via a `@ConditionalOnProperty` gate (it triggers the runtime starter bean) rather than being bound into `AgentArtsProperties`; the bound runtime fields are `port` and `max-concurrency`.

## Examples

The `agentarts-sdk-examples` module contains runnable example applications:

### Basic Runtime Example

A standalone agent using the Vert.x runtime with sync and SSE streaming support:

```bash
mvn compile exec:java -pl agentarts-sdk-examples \
  -Dexec.mainClass="com.huaweicloud.agentarts.examples.BasicRuntimeExample"

# Test:
curl http://localhost:8080/ping
curl -X POST http://localhost:8080/invocations \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello!"}'
```

### agentscope Integration Example

The complete runnable demo — [`AgentScopeIntegrationExample.java`](agentarts-sdk-examples/src/main/java/com/huaweicloud/agentarts/examples/AgentScopeIntegrationExample.java) — builds a `ReActAgent` with an OpenAI-compatible model, `@Tool`-annotated tools, optional `MemoryAgentStateStore` persistence, bounded per-session execution, and HTTP Runtime hosting via `AgentscopeRuntimeHost`:

```bash
export HUAWEICLOUD_SDK_MEMORY_API_KEY=your-api-key
export AGENTARTS_MEMORY_SPACE_ID=your-space-id

mvn compile exec:java -pl agentarts-sdk-examples \
  -Dexec.mainClass="com.huaweicloud.agentarts.examples.AgentScopeIntegrationExample"
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|---|---|---|
| `HUAWEICLOUD_SDK_AK` | Access Key ID | — |
| `HUAWEICLOUD_SDK_SK` | Secret Access Key | — |
| `HUAWEICLOUD_SDK_REGION` | Huawei Cloud region | `cn-southwest-2` |
| `HUAWEICLOUD_SDK_MEMORY_API_KEY` | Memory data-plane API key | — |
| `HUAWEICLOUD_SDK_CODE_INTERPRETER_API_KEY` | Code Interpreter data-plane API key | — |
| `AGENTARTS_CONTROL_ENDPOINT` | Custom control plane endpoint | auto |
| `AGENTARTS_RUNTIME_DATA_ENDPOINT` | Custom data plane endpoint | — |

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
    swr_config:
      organization: my-org
      repository: my-agent
    runtime:
      invoke_config:
        protocol: HTTP
        port: 8080
```

## CLI Toolkit

The `agentarts` CLI provides commands for the full agent lifecycle:

```bash
# Initialize a new project
agentarts init -n my-agent -t basic -r cn-southwest-2

# Local development server
agentarts dev -p 8080

# Configure agent settings
agentarts config -n my-agent -e com.example.MyAgent -r cn-southwest-2
agentarts config set base.region cn-north-4
agentarts config set-env LOG_LEVEL INFO
agentarts config list

# Deploy to Huawei Cloud
agentarts deploy -a my-agent -m cloud -t v1.0

# Invoke agent
agentarts invoke '{"message": "Hello!"}' -a my-agent -m local -p 8080

# Destroy agent
agentarts destroy -a my-agent -y
```

See also the [Python SDK](https://github.com/huaweicloud/agentarts-sdk-python) for the equivalent CLI toolkit.

## Building from Source

```bash
git clone https://github.com/huaweicloud/agentarts-sdk-java.git
cd agentarts-sdk-java
mvn clean verify
```

Release artifacts (sources, Javadoc and CycloneDX XML/JSON SBOM) are built with:

```bash
mvn -Prelease -DskipTests -Dgpg.skip=true package
```

Use organization-managed signing keys for a real release; `gpg.skip` is only for local artifact verification. API compatibility checking is available through the `api-compatibility` profile after a published baseline version is configured.

### Run Tests

```bash
# All local tests and coverage/build gates
mvn clean verify

# Unit tests only (core modules)
mvn test -pl agentarts-sdk-core,agentarts-sdk-service,agentarts-sdk-runtime

# E2E tests (read-only tier; AK/SK must already be injected by a secret store)
mvn test -pl agentarts-sdk-tests

# E2E tests (full lifecycle, creates real cloud resources, auto-cleaned)
export AGENTARTS_TEST_ALLOW_CREATE=1
mvn test -pl agentarts-sdk-tests

# E2E tests (billable tier — real money; CI/Runtime session need pre-provisioned resources)
export AGENTARTS_TEST_RUN_BILLABLE=1
mvn test -pl agentarts-sdk-tests -Dtest='CodeInterpreterSessionTest,RuntimeSessionLifecycleTest'

# Docker deploy (billable; self-deploys an agent via Docker+SWR, LIFO-cleans runtime/image/SWR)
export AGENTARTS_TEST_RUN_BILLABLE=1
mvn test -pl agentarts-sdk-tests -Dtest=CliDeployedRuntimeE2ETest
```

> See the [E2E 测试指南](docs/cn/e2e_testing_guide.md) for the full three-tier safety model, environment variables, and result interpretation. **Never commit, print or place real credentials in command history** — inject them through the environment or CI secret store, and rotate any credential that may have been exposed.

## Testing

The test suite spans three layers. The latest clean local verification executed 807 tests with 0 failures and 0 errors in 203.8 seconds; two billable suites were skipped because their explicit prerequisites were absent. Counts are evidence for that run only, not a cloud-authentication attestation.

| Layer | Description |
|---|---|
| Unit + local integration | V11 signing, HTTP client, Runtime server/client, Identity, Memory, Tools, Gateway, agentscope, Spring Boot, cross-module — no cloud credentials needed |
| Cloud E2E | Read-only probes plus opt-in lifecycle and billable suites; actual executed counts must be taken from the current Surefire reports |
| CLI scaffolding | Template rendering, picocli command-tree/option parsing, init/config structure (no Docker/cloud) |

Cloud E2E covers Identity, Memory, Gateway, Code Interpreter, Runtime, auth decorators, read-only probes, and CLI workflows. A successful default `mvn test` does not prove cloud behavior: classes guarded by `@BeforeAll` assumptions can be reported with `Tests run: 0`. Execution requirements and result interpretation are documented in the [E2E 测试指南](docs/cn/e2e_testing_guide.md).

### E2E Three-Tier Safety Model

| Tier | Switch | What runs | Cloud writes? |
|---|---|---|---|
| **Default (read-only)** | — | list/get + local RuntimeApp | none |
| **Lifecycle** | `AGENTARTS_TEST_ALLOW_CREATE=1` | create→get→update→delete | yes, LIFO teardown-guaranteed |
| **Billable** | `AGENTARTS_TEST_RUN_BILLABLE=1` | code-interpreter sandbox, runtime invoke | real money |

Tests whose prerequisites are not met use assumptions. Always inspect Surefire's executed and skipped counts; a green build alone is not an E2E attestation.

## Dependency Versions

Key dependencies aligned with [agentscope-java](https://github.com/agentscope-ai/agentscope-java) BOM:

| Dependency | Version | Notes |
|---|---|---|
| Project Reactor | `2025.0.2` | Aligned with agentscope |
| Jackson | `2.21.1` | Aligned with agentscope |
| Vert.x | `4.5.26` | Self-managed patched release |
| Huawei Cloud SDK v3 | `3.1.207` | core, agentidentity, iam, swr |
| agentscope-core | `1.0.12` | stable API, provided |
| MCP SDK | `0.17.0` | aligned with AgentScope 1.0.12, provided |
| Spring Boot | `3.5.16` | Optional starter |
| JUnit | `5.12.2` | Testing |

## API Compatibility

The Java SDK is aligned with the pinned Python SDK for core, service, Runtime, Identity, Memory, Tools, Gateway and CLI behavior. Python framework integrations and framework-specific templates are intentionally excluded; Java provides a production AgentScope integration instead. Language-idiomatic type shapes may differ, while wire behavior, defaults, error propagation and lifecycle semantics are covered by compatibility tests.

| Aspect | Coverage |
|---|---|
| V11 Signing | Cross-language golden vector tests (exact signature match) |
| HTTP Endpoints | Status codes (200/400/500/503), SSE format, error JSON structure |
| Header Constants | 4 standard AgentArts runtime headers |
| Context Fields | 7 thread-local context fields (session, request, user, token, OAuth2) |
| Memory API | 15 methods + MemorySession wrapper + 15 model classes |
| Tools API | 17 methods + CodeSession context manager |
| Gateway API | 10 methods (gateway CRUD + target CRUD) |
| Runtime Client API | 17 methods: agent CRUD (7) + endpoint CRUD (4) + data plane (6: invoke, exec, upload, download, sessions) |
| Identity API | Workload identity CRUD + credential providers + access tokens |
| CLI Commands | 9 top-level + 8 config + 6 runtime + 10 gateway + 6 memory subcommands |
| agentscope Integration | AgentScope `Session`, bounded per-session executor, request context bridge, AgentTool adapters, MessageConverter |
| Spring Boot Starter | AutoConfiguration, ConfigurationProperties, HealthIndicator |
| Verification | 807 tests in the latest clean local run; cloud lifecycle/billable execution remains explicitly opt-in |

## Python SDK Reference

Compatibility is tracked against the [Python AgentArts SDK](https://github.com/huaweicloud/agentarts-sdk-python). Claims of parity must be backed by behavioral tests; method-count or test-count parity alone is not sufficient.

| Item | Value |
|---|---|
| Python SDK Repository | https://github.com/huaweicloud/agentarts-sdk-python |
| Pinned Commit | `d130e21` (local reference checkout) |
| Exclusions | Python framework integrations and framework-specific templates |
| Last Synced | 2026-07-17 |

To check for Python SDK changes since last sync:

```bash
cd agentarts-sdk-python
git fetch origin
git log d130e21..HEAD --oneline
```

## License

[Apache License 2.0](LICENSE)
