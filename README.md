# Huawei Cloud AgentArts SDK for Java

**[English](README.md)** | [中文](README_zh.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17%2B-brightgreen.svg)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-orange.svg)](https://maven.apache.org/)

Build, deploy and manage AI agents with Huawei Cloud capabilities.

## Overview

The AgentArts Java SDK is a toolkit for developing, deploying, and managing AI
agents on Huawei Cloud. You implement only the agent logic; the SDK provides
the HTTP runtime, cloud authentication, built-in tools (memory, code
interpreter, gateway), and a CLI for the full agent lifecycle. It integrates
natively with [agentscope-java](https://github.com/agentscope-ai/agentscope-java)
and ships an optional Spring Boot starter.

> **The `agentarts` CLI is provided by the [Python SDK](https://github.com/huaweicloud/agentarts-sdk-python).**
> This Java repository no longer ships its own CLI. Install the CLI once (a
> standalone binary or `pip`) and it drives the full lifecycle — including
> Java agents — via `init`, `dev`, `deploy`, `invoke`, `destroy`. See
> [Install the CLI](#install-the-cli) below.

### Key Features

- **Wrap Any Agent as an HTTP Server** — expose `POST /invocations` (JSON + SSE streaming), `GET /ping` (health check), and `WS /ws` (WebSocket) via `AgentArtsRuntimeApp`
- **One-Command Lifecycle** — `init`, `dev`, `deploy`, `invoke`, `destroy` through the `agentarts` CLI (installed from the Python SDK)
- **Built-in Cloud Tools** — Memory (conversation memory), Code Interpreter (sandboxed execution), Gateway (gateway & target CRUD)
- **Cloud Identity** — workload identity, AK/SK signing, API Key and STS credential providers
- **AgentScope Integration** — request-scoped context, bounded per-session execution, `@Tool` adapters, message conversion
- **Spring Boot Starter** — auto-configuration for `AgentArtsRuntimeApp` with Actuator health indicator
- **Reactive** — Vert.x + Project Reactor streaming with backpressure and cancellation

## Module Structure

```
agentarts-sdk-java/
├── agentarts-sdk-bom/                    # Bill of Materials (dependency versions)
├── agentarts-sdk-core/                   # Constants, V11 signer, auth annotations, config
├── agentarts-sdk-service/                # Base HTTP client, IAM/SWR/runtime service clients
├── agentarts-sdk-runtime/                # Vert.x runtime server: /invocations, /ping, /ws, SSE
├── agentarts-sdk-identity/               # Identity client, credential providers, token polling
├── agentarts-sdk-memory/                 # Memory client (control + data plane), MemorySession
├── agentarts-sdk-tools/                  # Code Interpreter client, CodeSession
├── agentarts-sdk-gateway/                # Gateway client, gateway/target CRUD models
├── agentarts-sdk-integration-agentscope/ # AgentScope bridge, session, tools
├── agentarts-spring-boot-starter/        # Spring Boot auto-configuration + health indicator
├── agentarts-sdk-all/                    # Aggregator (all modules)
├── agentarts-sdk-examples/               # Runnable example projects
└── agentarts-sdk-tests/                  # Cross-module and opt-in cloud E2E tests
```

## Install the CLI

The `agentarts` CLI is the single entry point for the agent lifecycle and is
maintained in the [Python SDK repository](https://github.com/huaweicloud/agentarts-sdk-python).
It bundles its own Python interpreter, so **you do not need Python installed**
to use it. For Java agents it shells out to your local JDK + Maven.

**Linux / macOS — one-line install:**

```bash
curl -fsSL https://raw.githubusercontent.com/huaweicloud/agentarts-sdk-python/main/install.sh | sh
```

**Other options** (Windows, manual download, or `pip install agentarts-sdk`):
see the [Python SDK README](https://github.com/huaweicloud/agentarts-sdk-python#installation).

## Wrapping Your Agent as an HTTP Server

`AgentArtsRuntimeApp` wraps your agent logic as a standard HTTP server. You only implement the handler; the SDK handles HTTP, request parsing, and response formatting.

### 1. Add Dependencies

```xml
<dependency>
    <groupId>com.huaweicloud.agentarts</groupId>
    <artifactId>agentarts-sdk-runtime</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Implement Your Agent

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
        // Programmatic registration. (Use `agentarts dev` for annotation-based
        // auto-wiring of @Entrypoint / @Ping.)
        agent.app.setEntrypoint(agent::handle);
        agent.app.setPingHandler(agent::healthCheck);
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String host = System.getenv().getOrDefault("HOST", "0.0.0.0");
        agent.app.runUntilShutdown(port, host);
    }
}
```

### 3. Run and Test

```bash
# Using the CLI (recommended) — compiles your project and runs the agent
agentarts dev

# Or run directly
mvn compile exec:java -Dexec.mainClass="MyAgent"
```

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

## Quick Start

### Prerequisites

- JDK 17+
- Maven 3.9+
- The `agentarts` CLI (see [Install the CLI](#install-the-cli))
- Huawei Cloud account with AK/SK credentials

```bash
export HUAWEICLOUD_SDK_AK="your-access-key"
export HUAWEICLOUD_SDK_SK="your-secret-key"
export HUAWEICLOUD_SDK_REGION="cn-southwest-2"
```

> Get your AK/SK from [Huawei Cloud Console](https://console.huaweicloud.com/) → My Credentials → Access Keys.

### 1. Initialize a Project

```bash
# Java project (Maven + JDK17 + agentarts-sdk-runtime)
agentarts init -n my-agent -t java-basic -r cn-southwest-2
# AgentScope-based Java agent:
# agentarts init -n my-agent -t java-agentscope -r cn-southwest-2
```

This scaffolds `my-agent/` with a `pom.xml`, a scaffolded `Agent.java`, and an `.agentarts_config.yaml` (with `language: java17`).

### 2. Develop Locally

```bash
agentarts dev -p 8080
# The CLI runs `mvn compile`, launches a child JVM, and watches sources for
# hot reload. Server runs at http://127.0.0.1:8080
#   POST /invocations  — invoke the agent
#   GET  /ping         — health check
```

### 3. Configure

```bash
agentarts config set base.region cn-north-4
agentarts config set-env LOG_LEVEL INFO
agentarts config list
```

> Already have your own Java project? Skip `init` and run
> `agentarts config -n my-agent -e com.example.MyAgent --dependency-file pom.xml`
> to generate just the `.agentarts_config.yaml` + Dockerfile.

### 4. Deploy and Invoke

```bash
agentarts deploy -a my-agent -m cloud -t v1.0
agentarts invoke '{"message": "Hello!"}' -a my-agent -m local -p 8080
agentarts destroy -a my-agent -y
```

## CLI Commands Reference

The `agentarts` CLI (from the Python SDK) supports both Python and Java agents.
For Java agents it uses `language: java17` and shells out to Maven/JDK.

| Command | Description |
| --- | --- |
| `agentarts init` | Initialize a new agent project (`-t java-basic` / `-t java-agentscope` for Java) |
| `agentarts dev` | Start the local development server (compiles + runs your agent, hot reload) |
| `agentarts config` | Configure SDK settings (alias: `configure`) |
| `agentarts deploy` | Deploy an agent to Huawei Cloud (alias: `launch`) |
| `agentarts invoke` | Invoke an agent |
| `agentarts destroy` | Remove a deployed agent |
| `agentarts runtime` | Manage runtime resources (6 subcommands) |
| `agentarts gateway` | Manage gateways and targets (alias: `mcp-gateway`) |
| `agentarts memory` | Manage Memory Spaces and sessions (6 subcommands) |

Full CLI documentation lives in the [Python SDK repository](https://github.com/huaweicloud/agentarts-sdk-python#readme).

## AgentScope Integration

For agents built on [agentscope-java](https://github.com/agentscope-ai/agentscope-java), use the integration module — it bridges the AgentArts HTTP runtime with AgentScope's `ReActAgent`, request-scoped context, bounded per-session execution, and `@Tool` adapters.

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

For a complete runnable demo (interactive streaming, multi-turn memory, `@Tool`-annotated tools, server mode), see [`AgentScopeIntegrationExample.java`](agentarts-sdk-examples/src/main/java/com/huaweicloud/agentarts/examples/AgentScopeIntegrationExample.java).

## Spring Boot Starter

For Spring Boot 3.x applications, use the optional starter:

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

The starter auto-configures `AgentArtsRuntimeApp` and contributes an Actuator health indicator at `/actuator/health`.

## Configuration

### Environment Variables

| Variable | Description | Default |
| --- | --- | --- |
| `HUAWEICLOUD_SDK_AK` | Access Key ID | — |
| `HUAWEICLOUD_SDK_SK` | Secret Access Key | — |
| `HUAWEICLOUD_SDK_REGION` | Huawei Cloud region | `cn-southwest-2` |
| `HUAWEICLOUD_SDK_MEMORY_API_KEY` | Memory data-plane API key | — |
| `HUAWEICLOUD_SDK_CODE_INTERPRETER_API_KEY` | Code Interpreter data-plane API key | — |
| `AGENTARTS_CONTROL_ENDPOINT` | Custom control plane endpoint | auto |
| `AGENTARTS_RUNTIME_DATA_ENDPOINT` | Custom data plane endpoint | — |

For the full priority chain and Java constant mappings, see the [Environment Variables Guide](docs/cn/sdk_user_guide/environment_variables.md).

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

## Built-in Cloud Tools

| Tool | Module | What it does |
| --- | --- | --- |
| **Memory** | `agentarts-sdk-memory` | Conversation memory: space/session/message CRUD, search with filters, `MemorySession` wrapper (control plane via AK/SK, data plane via API Key) |
| **Code Interpreter** | `agentarts-sdk-tools` | Sandboxed execution: run code/commands, upload/download files, install packages — `CodeSession` auto-closeable context |
| **Gateway** | `agentarts-sdk-gateway` | Gateway and target CRUD via AK/SK signed requests |
| **Identity** | `agentarts-sdk-identity` | Workload identity, access tokens (forUserId/forJwt), OAuth2/API Key/STS providers |

See the [SDK user guides](docs/cn/sdk_user_guide) for API details and examples.

## Examples

The `agentarts-sdk-examples` module contains runnable projects:

```bash
# Basic Vert.x runtime agent (sync + SSE streaming)
mvn compile exec:java -pl agentarts-sdk-examples \
  -Dexec.mainClass="com.huaweicloud.agentarts.examples.BasicRuntimeExample"

# AgentScope integration demo (streaming, tools, multi-turn memory)
export HUAWEICLOUD_SDK_MEMORY_API_KEY=your-api-key
export AGENTARTS_MEMORY_SPACE_ID=your-space-id
mvn compile exec:java -pl agentarts-sdk-examples \
  -Dexec.mainClass="com.huaweicloud.agentarts.examples.AgentScopeIntegrationExample"
```

## Building from Source

```bash
git clone https://github.com/huaweicloud/agentarts-sdk-java.git
cd agentarts-sdk-java
mvn clean verify
```

Cloud E2E tests are opt-in and require real credentials; a green `mvn verify` run does not exercise cloud behavior. See the [E2E testing guide](docs/cn/e2e_testing_guide.md) for the safety tiers, environment variables, and how to interpret results. **Never commit or print real credentials** — inject them through the environment or a CI secret store.

## Documentation

- [SDK User Guides (中文)](docs/cn/sdk_user_guide) — Runtime, Memory, Code Interpreter, Gateway, Identity
- [E2E Testing Guide (中文)](docs/cn/e2e_testing_guide.md) — three-tier safety model and result interpretation
- [CLI (Python SDK)](https://github.com/huaweicloud/agentarts-sdk-python#readme) — `agentarts` CLI install, commands, Java agent support

## License

[Apache License 2.0](LICENSE)

## Support

- **Issues:** [GitHub Issues](https://github.com/huaweicloud/agentarts-sdk-java/issues)
- **Documentation:** <https://support.huaweicloud.com/productdesc-agentarts/agentarts_03_0002.html>
- **Email:** <hwagentartssdk@huawei.com>
