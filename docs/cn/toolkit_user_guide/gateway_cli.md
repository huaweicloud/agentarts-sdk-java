# agentarts gateway / gateway — MCP 网关管理

`gateway` 是与 Python CLI 对齐的名称，`gateway` 继续作为兼容名称。简短子命令与原 Java 长名称指向同一实现，例如 `gateway create` 等价于 `gateway create-gateway`。

| 简短名称 | 兼容名称 |
|---|---|
| `create` / `update` / `delete` / `get` / `list` | `*-gateway` / `list-gateways` |
| `create-target` / `update-target` / `delete-target` / `get-target` / `list-targets` | `*-gateway-target` / `list-gateway-targets` |

> 下方多行示例使用 bash 的反斜杠 `\` 续行。Windows PowerShell 下续行符改为反引号 `` ` ``，或写成一行。每处多行示例后均附 PowerShell 版本。
>
> **含 JSON 的命令**（如 `--target-configuration`）在 PS 5.1 下须用 `--%` + `\"` 且单行，见 [cli_overview.md §4.1](cli_overview.md#41-windows-powershell-引号注意事项重要)。

## 前置条件

设置 AK/SK 环境变量：

```bash
export HUAWEICLOUD_SDK_AK="your-access-key"
export HUAWEICLOUD_SDK_SK="your-secret-key"
```

```powershell
$env:HUAWEICLOUD_SDK_AK = "your-access-key"
$env:HUAWEICLOUD_SDK_SK = "your-secret-key"
```

## 网关命令

### create-gateway — 创建网关

```bash
# 基本创建
agentarts gateway create-gateway --name my-gateway --description "测试网关"

# 完整配置
agentarts gateway create-gateway \
  --name my-gateway \
  --description "生产网关" \
  --protocol-type mcp \
  --authorizer-type iam \
  --agency-name AgentArtsCoreGateway
```

```powershell
# 完整配置
agentarts gateway create-gateway `
  --name my-gateway `
  --description "生产网关" `
  --protocol-type mcp `
  --authorizer-type iam `
  --agency-name AgentArtsCoreGateway
```

### update-gateway — 更新网关

`gatewayId` 为位置参数（不是 `--gateway-id` 选项）：

```bash
agentarts gateway update-gateway gw-123 --description "更新后的描述"
```

### delete-gateway — 删除网关

```bash
agentarts gateway delete-gateway gw-123
```

### get-gateway — 获取网关

```bash
agentarts gateway get-gateway gw-123
```

### list-gateways — 列出网关

```bash
# 列出全部
agentarts gateway list-gateways

# 按名称过滤
agentarts gateway list-gateways --name my-gateway

# 分页
agentarts gateway list-gateways --limit 10 --offset 0
```

## 目标命令

### create-gateway-target — 创建目标

`gatewayId` 为位置参数；`--target-configuration`（JSON）为必填：

```bash
agentarts gateway create-gateway-target gw-123 \
  --name my-target \
  --description "测试目标" \
  --target-configuration '{"mcp_server":{"endpoint":"https://example.com/mcp","server_type":"sse"}}'
```

```powershell
# PS 5.1 含 JSON 需用 --% + \" 且单行（见 cli_overview.md §4.1）
agentarts --% gateway create-gateway-target gw-123 --name my-target --description "测试目标" --target-configuration "{\"mcp_server\":{\"endpoint\":\"https://example.com/mcp\",\"server_type\":\"sse\"}}"
```

### update-gateway-target — 更新目标

`gatewayId` 与 `targetId` 均为位置参数（前两个位置参数）：

```bash
agentarts gateway update-gateway-target gw-123 tgt-456 \
  --name new-name \
  --description "新描述"
```

```powershell
agentarts gateway update-gateway-target gw-123 tgt-456 `
  --name new-name `
  --description "新描述"
```

### delete-gateway-target — 删除目标

```bash
agentarts gateway delete-gateway-target gw-123 tgt-456
```

### get-gateway-target — 获取目标

```bash
agentarts gateway get-gateway-target gw-123 tgt-456
```

### list-gateway-targets — 列出目标

`gatewayId` 为位置参数：

```bash
# 列出网关下所有目标
agentarts gateway list-gateway-targets gw-123

# 分页
agentarts gateway list-gateway-targets gw-123 --limit 10 --offset 0
```

## 最佳实践

1. **先创建网关，再创建目标** — 目标必须关联到一个网关
2. **使用有意义的名称** — 便于后续查找和管理
3. **定期清理不用的网关和目标** — 避免资源浪费
4. **自动化删除显式使用 `--force`** — 交互终端默认确认；无 TTY 且未传 `--force` 时命令失败，不会误删
5. **IAM agency 创建是幂等的** — 已存在的 agency/策略绑定会复用；403、5xx 和非冲突错误会原样失败

## 常见问题

### 403 错误

如果收到 `Operation not supported. The tenant has not enabled the service.` 错误，表示租户尚未开通 Gateway 服务。请在华为云控制台开通后再试。
