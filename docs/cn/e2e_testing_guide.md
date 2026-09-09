# 端到端（E2E）测试指南

本文说明 AgentArts Java SDK 的本地、云端和计费测试门禁，以及如何判断鉴权测试是否真正执行。

> 凭证安全：本文只使用占位符。真实 AK、SK、API Key、Bearer Token 仅由受控环境在运行时注入，不写入命令历史、源码、配置、日志、测试报告或 Git。

> 对齐基准：本仓库按本地 `agentarts-sdk-python` commit `d130e21` 审核；除 Python framework 模板外，SDK 与 CLI 能力纳入对齐范围。

## 测试分层

| 层级 | 前置条件 | 覆盖范围 | 是否创建资源 |
|---|---|---|---|
| 本地门禁 | JDK 17+、Maven 3.9+ | 单元、模块集成、HTTP wire、Runtime SSE/WebSocket、CLI | 否 |
| L1 只读云测 | AK/SK | Identity、Memory、Gateway、Runtime、Code Interpreter 的只读探测 | 否 |
| L2 生命周期 | AK/SK + `AGENTARTS_TEST_ALLOW_CREATE=1` | create/get/list/update/delete 与鉴权装饰器 | 是 |
| L3 计费 | L2 + `AGENTARTS_TEST_RUN_BILLABLE=1` + 预置资源 | Runtime session、Code Interpreter session、CLI deploy/invoke | 是，且可能计费 |

L2/L3 资源通过 `E2EResourceRegistry` 逆序清理。测试资源使用带运行 ID 的独立名称；即使断言失败，也会尝试清理。

## 环境变量

| 变量 | 用途 |
|---|---|
| `HUAWEICLOUD_SDK_AK` / `HUAWEICLOUD_SDK_SK` | 云控制面认证 |
| `HUAWEICLOUD_SDK_SECURITY_TOKEN` | 临时凭证的安全令牌（如适用） |
| `HUAWEICLOUD_SDK_REGION` | 测试区域，默认 `cn-southwest-2` |
| `AGENTARTS_TEST_ALLOW_CREATE` | 值为 `1` 时允许生命周期测试 |
| `AGENTARTS_TEST_RUN_BILLABLE` | 值为 `1` 时允许计费测试 |
| `AGENTARTS_TEST_RUN_ID` | 可选的资源名称隔离标识 |
| `HUAWEICLOUD_SDK_MEMORY_API_KEY` | Memory 数据面测试 |
| `HUAWEICLOUD_SDK_CODE_INTERPRETER_API_KEY` | Code Interpreter 数据面测试 |
| `AGENTARTS_TEST_WORKLOAD_IDENTITY_NAME` | L1 预置 Workload Identity |
| `AGENTARTS_TEST_RUNTIME_AGENT_NAME` | L3 预部署 Runtime Agent |
| `AGENTARTS_TEST_CODE_INTERPRETER_NAME` | L3 预置 Code Interpreter |
| `AGENTARTS_TEST_STS_AGENCY_URN` | STS provider/注解测试 |
| `AGENTARTS_TEST_OAUTH2_CLIENT_ID` / `_CLIENT_SECRET` / `_VENDOR` | OAuth2 provider 测试 |

不要使用已废弃或不存在的 `AGENTARTS_TEST_REGION`；区域统一读取 `HUAWEICLOUD_SDK_REGION`。

## 推荐执行方式

### 本地完整门禁

```bash
mvn clean verify
```

根 POM 对测试执行设置了边界：单个测试默认 30 秒、fork 180 秒，CI 工作流整体 15 分钟。云测试工作流使用更宽但仍有限的 120 秒/600 秒/10 分钟边界。不要通过无限增大超时掩盖阻塞、重试或资源泄漏。

### 云端定向验证

以下命令假定凭证已由 CI secret store 或当前进程环境安全注入：

```bash
# L1：只读
mvn test -pl agentarts-sdk-tests -am

# L2：显式允许创建后再运行
export AGENTARTS_TEST_ALLOW_CREATE=1
mvn test -pl agentarts-sdk-tests -am

# 单类诊断，避免重复运行整个套件
mvn test -pl agentarts-sdk-tests -am \
  -Dtest=IdentityReadonlyTest -Dsurefire.failIfNoSpecifiedTests=false
```

PowerShell 中使用当前进程的 `$env:变量名` 注入，任务结束后关闭该终端或显式移除变量。不要使用 `setx` 持久化测试密钥，也不要把密钥写入 `-D...` 参数。

L3 必须在隔离租户中额外设置 `AGENTARTS_TEST_RUN_BILLABLE=1`，并提供预置 Runtime/Code Interpreter 资源；CLI deploy 测试还要求可用的 Docker daemon。默认门禁不会意外触发计费。

## 如何判断鉴权测试有效

绿色构建不等于真实云鉴权通过。必须同时确认：

1. Surefire XML 中目标云测试类的 `tests` 大于 0，而不是在 `@BeforeAll` assumption 后整类跳过。
2. 报告中没有因缺少凭证、预置资源或开关导致的 skip。
3. L1 至少观察到真实服务 2xx 响应；权限不足的 401/403 只能证明请求到达服务，不能证明授权有效。
4. L2/L3 的创建、读取、更新、删除和清理均成功；不能把 setup 失败后的整类跳过算作通过。
5. API Key、STS、OAuth2 和 Bearer Token 分别验证其对应数据面或注解路径，AK/SK 成功不能替代这些认证方式。

本地 wire 测试会验证 Authorization/IAM 签名头是否按契约生成、错误是否 fail-closed、敏感值是否被脱敏；它们不替代真实租户鉴权。

## 覆盖与 Python 对齐

Java 套件覆盖：

- Runtime 控制面、数据面、本地服务、SSE、WebSocket、会话与文件传输；
- Memory 空间、消息、记忆、同步与真正非阻塞的 cold Reactor API，以及 `AsyncMemorySession`；
- Identity Workload Identity、API Key/OAuth2/STS provider 和 `@Require*` 注解；
- Gateway/Target CRUD、IAM agency 创建复用、策略绑定和分页；
- Code Interpreter 控制面与沙箱会话；
- CLI init/config/dev/deploy/destroy/invoke/runtime/memory/gateway 的命令树、别名、help 和本地/云路径；
- AgentScope 1.0 Session 适配、每会话串行化、超时、状态持久化与 Runtime host。

Python 的 LangGraph、LangChain、Google ADK 等 framework 模板不属于 Java 对齐范围。Java CLI 明确支持 `basic` 与 `agentscope`，未知模板会失败，不静默降级。

## 异步与流式测试

`AsyncMemoryClient` 和 `AsyncMemorySession` 返回 cold `Mono`，订阅时才发起非阻塞 HTTP；取消、关闭及重复订阅语义有测试覆盖。Runtime 的 SSE/NDJSON 与二进制下载通过单次消费的流式 `RequestResult` 验证，未消费或中途取消时必须关闭响应。

## 结果记录

每次发布前记录以下证据，不记录任何凭证值：

- commit、JDK/Maven 版本、`mvn clean verify` 总时长；
- tests/failures/errors/skipped 汇总和各模块 JaCoCo 覆盖；
- release profile 构建结果与产物大小；
- gitleaks 历史扫描和工作区扫描结果；
- 实际执行的 L1/L2/L3 层级及未执行原因。

截至 2026-07-17 的本地生产门禁为 0 failures、0 errors；两个计费测试类在未提供预置资源和计费开关时按设计跳过。最终数字以当前提交生成的 Surefire XML 为准。
