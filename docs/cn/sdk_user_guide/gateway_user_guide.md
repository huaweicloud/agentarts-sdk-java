# Gateway SDK 使用指南

AgentArts Gateway SDK 提供 MCP 网关和目标的管理能力，始终使用 AK/SK 签名认证。

> 生产行为：客户端保留服务端 HTTP 状态和错误体，不把 401/403/5xx 转成空结果；创建默认 IAM agency 时仅将“已存在”的 409 视为幂等成功。

## 概述

Gateway 是 Model Context Protocol 的服务端网关，用于管理 MCP 工具的路由和调用。SDK 提供完整的网关和目标 CRUD 操作。

## 身份验证

Gateway 始终使用 AK/SK 签名认证：

```bash
export HUAWEICLOUD_SDK_AK="your-access-key"
export HUAWEICLOUD_SDK_SK="your-secret-key"
```

```powershell
$env:HUAWEICLOUD_SDK_AK = "your-access-key"
$env:HUAWEICLOUD_SDK_SK = "your-secret-key"
```

## 快速入门

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.huaweicloud.agentarts.sdk.gateway.GatewayClient;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import java.util.Map;

try (GatewayClient client = new GatewayClient()) {
    // 创建网关
    RequestResult gw = client.createGateway("my-gateway", "测试网关");
    JsonNode gwData = (JsonNode) gw.getData();
    String gatewayId = gwData.has("id") ? gwData.get("id").asText()
            : gwData.get("gateway_id").asText();

    // 创建目标（targetConfiguration 必填）
    RequestResult target = client.createGatewayTarget(
            gatewayId, "my-target", "测试目标",
            Map.of("mcp_server", Map.of(
                    "endpoint", "https://example.com/mcp",
                    "server_type", "sse")),
            null);

    // 列出网关
    RequestResult list = client.listGateways();
}
```

## API 参考

### 构造函数

```java
// 默认 SSL 验证
GatewayClient client = new GatewayClient();

// 禁用 SSL 验证
GatewayClient client = new GatewayClient(false);
```

### 网关管理

#### createGateway — 创建网关

```java
RequestResult result = client.createGateway(
    "my-gateway",     // name: 网关名称
    "描述文本",        // description: 可选描述
    "mcp",            // protocolType: 协议类型（默认 "mcp"）
    "iam",            // authorizerType: 认证类型（默认 "iam"）
    null              // agencyName: IAM 代理名称（可选）
);
```

**返回**：`RequestResult`（success, data, error, statusCode）

#### updateGateway — 更新网关

```java
RequestResult result = client.updateGateway("gateway-id", "新描述");
```

#### deleteGateway — 删除网关

```java
RequestResult result = client.deleteGateway("gateway-id");
```

#### getGateway — 获取网关

```java
RequestResult result = client.getGateway("gateway-id");
if (result.isSuccess()) {
    JsonNode data = result.getDataAsJson();
}
```

#### listGateways — 列出网关

```java
RequestResult result = client.listGateways(
    null,  // name: 按名称过滤（可选）
    10,    // limit: 分页大小
    0      // offset: 偏移量
);
```

### 目标管理

#### createGatewayTarget — 创建目标

```java
RequestResult result = client.createGatewayTarget(
    "gateway-id",          // gatewayId
    "my-target",           // name
    "目标描述",             // description
    null,                  // targetConfiguration（可选）
    null                   // credentialProviderConfiguration（可选）
);
```

#### updateGatewayTarget — 更新目标

```java
RequestResult result = client.updateGatewayTarget(
    "gateway-id", "target-id",
    "new-name", "new-description",
    null, null
);
```

#### deleteGatewayTarget — 删除目标

```java
RequestResult result = client.deleteGatewayTarget("gateway-id", "target-id");
```

#### getGatewayTarget — 获取目标

```java
RequestResult result = client.getGatewayTarget("gateway-id", "target-id");
```

#### listGatewayTargets — 列出目标

```java
RequestResult result = client.listGatewayTargets("gateway-id", 10, 0);
// gatewayId, limit, offset
```

## RequestResult 返回类型

所有 Gateway API 方法返回 `RequestResult`：

| 属性 | 类型 | 说明 |
|------|------|------|
| `isSuccess()` | boolean | 请求是否成功（2xx 状态码） |
| `getStatusCode()` | int | HTTP 状态码 |
| `getData()` | Object | JSON 响应为 `JsonNode`，文本为 `String`，二进制为 `byte[]` |
| `getError()` | String | 错误信息（失败时） |
| `getHeaders()` | Map | 响应头 |

## 错误处理

```java
RequestResult result = client.createGateway("my-gw", "desc");
if (!result.isSuccess()) {
    System.err.println("Error " + result.getStatusCode() + ": " + result.getError());
} else {
    JsonNode data = result.getDataAsJson();
    String gatewayId = data.path("id").asText();
}
```

调用方应把非 2xx 当作失败，并记录状态码、request ID 和脱敏后的错误摘要。不要记录签名头、AK/SK、credential provider 返回值或完整响应头。

## IAM agency 与分页

未显式传入 `agencyName` 时，SDK 会确保默认 agency 和系统策略存在：创建成功后读取嵌套的 agency ID；遇到 409 时按名称分页查找并复用；策略已绑定的 409 同样按幂等成功处理。401、403、429 和 5xx 会继续向上抛出。

IAM marker 分页会检测重复 marker，避免后端异常导致无限循环。业务列表 API 仍由调用方根据 `limit`/`offset` 分页，不应假定单页包含全部网关或目标。

## 最佳实践

1. **使用 try-with-resources** 确保客户端资源正确释放
2. **检查 `isSuccess()`** 在访问 `getData()` 之前
3. **处理 403 错误** — 可能表示租户未开通 Gateway 服务
4. **复用客户端** — 每个进程复用少量客户端，并在关闭时调用 `close()`
5. **设置唯一名称和清理策略** — 自动化创建的网关/目标应携带运行标识，并在失败路径清理
