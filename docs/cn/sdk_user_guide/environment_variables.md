# 环境变量配置指南

本文档详细说明 AgentArts Java SDK 支持的所有环境变量，包括用途、默认值和优先级规则。

> 生产安全：SDK 只从进程环境读取这些值，不会自动加载 `.env`，也不会把环境变量当作 JVM `-D` 系统属性。密钥应由 Secret Manager、容器 Secret 或 CI secret store 注入；不要提交 `.env`，不要把密钥放在命令行参数中。

## 华为云认证

### AK/SK 认证（控制面必需）

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `HUAWEICLOUD_SDK_AK` | 华为云 Access Key ID | `AK_xxxxxxxxxxxxxxxx` |
| `HUAWEICLOUD_SDK_SK` | 华为云 Secret Access Key | `SK_xxxxxxxxxxxxxxxx` |

```bash
# Linux / macOS
export HUAWEICLOUD_SDK_AK="your-access-key"
export HUAWEICLOUD_SDK_SK="your-secret-key"
```

```powershell
# Windows PowerShell
$env:HUAWEICLOUD_SDK_AK = "your-access-key"
$env:HUAWEICLOUD_SDK_SK = "your-secret-key"
```

### STS 安全令牌（可选）

用于临时凭证场景，与 AK/SK 配合使用：

| 变量名 | 说明 |
|--------|------|
| `HUAWEICLOUD_SDK_SECURITY_TOKEN` | STS 安全令牌 |

```bash
export HUAWEICLOUD_SDK_SECURITY_TOKEN="your-security-token"
```

```powershell
$env:HUAWEICLOUD_SDK_SECURITY_TOKEN = "your-security-token"
```

## 区域配置

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `HUAWEICLOUD_SDK_REGION` | 华为云区域 | `cn-southwest-2` |
| `HUAWEICLOUD_REGION` | 备用区域变量 | — |
| `OS_REGION_NAME` | OpenStack 兼容区域 | — |

**优先级链**：`HUAWEICLOUD_SDK_REGION` → `HUAWEICLOUD_REGION` → `OS_REGION_NAME` → `cn-southwest-2`

```bash
export HUAWEICLOUD_SDK_REGION="cn-southwest-2"
```

```powershell
$env:HUAWEICLOUD_SDK_REGION = "cn-southwest-2"
```

Java SDK 中通过 `Constants.getRegion()` 获取区域，自动按上述优先级解析。

## 项目 ID

| 变量名 | 说明 |
|--------|------|
| `HUAWEICLOUD_SDK_PROJECT_ID` | 华为云项目 ID |

```bash
export HUAWEICLOUD_SDK_PROJECT_ID="your-project-id"
```

```powershell
$env:HUAWEICLOUD_SDK_PROJECT_ID = "your-project-id"
```

## IDP 配置

| 变量名 | 说明 |
|--------|------|
| `HUAWEICLOUD_SDK_IDP_ID` | 身份提供商 ID |
| `HUAWEICLOUD_SDK_ID_TOKEN_FILE` | ID Token 文件路径 |

## AgentArts 服务端点

### 控制面端点

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `AGENTARTS_CONTROL_ENDPOINT` | 控制面端点 URL | `https://agentarts.{region}.myhuaweicloud.com` |

```bash
export AGENTARTS_CONTROL_ENDPOINT="https://agentarts.cn-southwest-2.myhuaweicloud.com"
```

```powershell
$env:AGENTARTS_CONTROL_ENDPOINT = "https://agentarts.cn-southwest-2.myhuaweicloud.com"
```

### 数据面端点

| 变量名 | 说明 |
|--------|------|
| `AGENTARTS_RUNTIME_DATA_ENDPOINT` | 运行时数据面端点 |
| `AGENTARTS_MEMORY_DATA_ENDPOINT` | Memory 数据面端点 |
| `AGENTARTS_CODEINTERPRETER_DATA_ENDPOINT` | 代码解释器数据面端点 |

数据面凭证分别使用 `HUAWEICLOUD_SDK_MEMORY_API_KEY` 和 `HUAWEICLOUD_SDK_CODE_INTERPRETER_API_KEY`。它们与控制面 AK/SK 用途不同，不应互相替代。

```bash
export AGENTARTS_RUNTIME_DATA_ENDPOINT="https://your-runtime-endpoint.com"
export AGENTARTS_MEMORY_DATA_ENDPOINT="https://memory.cn-southwest-2.huaweicloud-agentarts.com"
export AGENTARTS_CODEINTERPRETER_DATA_ENDPOINT="https://your-ci-endpoint.com"
```

```powershell
$env:AGENTARTS_RUNTIME_DATA_ENDPOINT = "https://your-runtime-endpoint.com"
$env:AGENTARTS_MEMORY_DATA_ENDPOINT = "https://memory.cn-southwest-2.huaweicloud-agentarts.com"
$env:AGENTARTS_CODEINTERPRETER_DATA_ENDPOINT = "https://your-ci-endpoint.com"
```

## 华为云服务端点

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `HUAWEICLOUD_SDK_IAM_ENDPOINT` | IAM 服务端点 | `https://iam.{region}.myhuaweicloud.com` |
| `HUAWEICLOUD_SDK_SWR_ENDPOINT` | SWR 服务端点 | `https://swr-api.{region}.myhuaweicloud.com` |
| `HUAWEICLOUD_SDK_SWR_LOGIN_SERVER` | SWR Docker 登录服务器（部分区域格式不同，可覆盖） | `swr.{region}.myhuaweicloud.com` |
| `HUAWEICLOUD_SDK_AGENTIDENTITY_ENDPOINT` | 代理身份服务端点 | `https://agent-identity.{region}.myhuaweicloud.com` |

## 运行时配置

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `AGENTARTS_LOG_LEVEL` | 日志级别（DEBUG/INFO/WARNING/ERROR） | `INFO` |
| `AGENTARTS_BIND_IP` | 运行时绑定 IP | `0.0.0.0` |

## 生产注入方式

- 本地开发：只在当前终端进程设置环境变量，退出终端后失效。
- Kubernetes：使用 Secret 并以 `env.valueFrom.secretKeyRef` 注入。
- CI/CD：使用平台的 masked/protected secret，并关闭命令回显。
- 长期运行：优先使用可轮换的临时凭证；发生疑似泄漏时立即轮换并审计访问日志。

如果团队使用 `.env` 工具，应由应用或启动器显式加载，且文件必须加入 `.gitignore`、限制文件权限并避免进入镜像层；SDK 本身不会读取该文件。

## Java SDK 常量对照表

| 环境变量 | Java 常量 | 访问方法 |
|----------|-----------|----------|
| `HUAWEICLOUD_SDK_AK` | `Constants.ENV_HUAWEICLOUD_SDK_AK` | `Constants.getAk()` |
| `HUAWEICLOUD_SDK_SK` | `Constants.ENV_HUAWEICLOUD_SDK_SK` | `Constants.getSk()` |
| `HUAWEICLOUD_SDK_REGION` | `Constants.ENV_HUAWEICLOUD_SDK_REGION` | `Constants.getRegion()` |
| `HUAWEICLOUD_SDK_SECURITY_TOKEN` | `Constants.ENV_HUAWEICLOUD_SDK_SECURITY_TOKEN` | `Constants.getSecurityToken()` |
| `AGENTARTS_CONTROL_ENDPOINT` | `Constants.ENV_AGENTARTS_CONTROL_ENDPOINT` | `Constants.getControlPlaneEndpoint()` |
| `AGENTARTS_RUNTIME_DATA_ENDPOINT` | `Constants.ENV_AGENTARTS_RUNTIME_DATA_ENDPOINT` | `Constants.getRuntimeDataPlaneEndpoint()` |
| `AGENTARTS_MEMORY_DATA_ENDPOINT` | `Constants.ENV_AGENTARTS_MEMORY_DATA_ENDPOINT` | `Constants.getMemoryEndpoint("data")` |
| `AGENTARTS_CODEINTERPRETER_DATA_ENDPOINT` | `Constants.ENV_AGENTARTS_CODEINTERPRETER_DATA_ENDPOINT` | `Constants.getCodeInterpreterDataPlaneEndpoint()` |
| `HUAWEICLOUD_SDK_SWR_LOGIN_SERVER` | `Constants.ENV_HUAWEICLOUD_SDK_SWR_LOGIN_SERVER` | `Constants.getSwrLoginServer(region)` |

## 常见问题

### 环境变量不生效？

确保在启动 Java 应用之前已导出环境变量：

```bash
export HUAWEICLOUD_SDK_AK="your-ak"
export HUAWEICLOUD_SDK_SK="your-sk"
java -jar your-app.jar
```

```powershell
$env:HUAWEICLOUD_SDK_AK = "your-ak"
$env:HUAWEICLOUD_SDK_SK = "your-sk"
java -jar your-app.jar
```

不要改用 JVM `-D` 参数：SDK 不从系统属性读取凭证，而且命令行可能被进程列表和构建日志采集。

### 区域选择

AgentArts 默认使用 `cn-southwest-2` 区域。如需使用其他区域，请设置 `HUAWEICLOUD_SDK_REGION`。
