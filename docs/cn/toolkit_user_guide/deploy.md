# agentarts deploy — 部署 Agent

`deploy` 命令（别名 `launch`）将 Agent 部署到华为云或本地环境。

> 生产行为：构建、Docker 与云 API 子进程均有超时；任一步失败都会返回非零退出码。SWR 仅把 404 视为“资源不存在”、409 视为并发创建/已存在，401/403/429/5xx 不会被吞掉。

## 用法

```bash
agentarts deploy [选项]
agentarts launch [选项]  # 别名
```

## 参数

| 参数 | 缩写 | 说明 | 默认值 |
|------|------|------|--------|
| `--agent` | `-a` | Agent 名称 | 默认 Agent |
| `--mode` | `-m` | 部署模式：`cloud` 或 `local` | `cloud` |
| `--tag` | `-t` | Docker 镜像标签 | `latest` |
| `--local-port` | `-l` | 本地模式端口映射 | — |
| `--swr-org` | | SWR 组织名（覆盖配置文件） | 配置文件值 |
| `--swr-repo` | | SWR 仓库名（覆盖配置文件） | 配置文件值 |
| `--description` | `-d` | Agent 描述 | — |
| `--skip-build` | | 跳过构建/推送，直接用配置中的镜像 URL | `false` |
| `--skip-ssl-verification` | `-k` | 跳过 SSL 证书验证 | `false` |
| `--platform` | | Docker 构建目标平台（`os/arch` 格式，如 `linux/amd64`、`linux/arm64`） | `linux/arm64` |

## 部署模式

### cloud — 云端部署

```bash
agentarts deploy -a my-agent -m cloud -t v1.0
```

部署流程：

1. Maven 构建项目并执行 Docker build
2. 按配置检查或幂等创建 SWR 命名空间/仓库
3. 使用 stdin 向 Docker login 传递短期 SWR 凭证，随后推送镜像
4. 创建或更新 AgentArts Runtime
5. 原子更新 `.agentarts_config.yaml` 中的 `agent_id`

### local — 本地部署

```bash
agentarts deploy -a my-agent -m local --local-port 8080
```

## 示例

> 多行示例使用 bash 反斜杠 `\` 续行；Windows PowerShell 下改为反引号 `` ` ``，或写成一行。

```bash
# 基本部署（云端）
agentarts deploy

# 指定 Agent 和标签
agentarts deploy --agent my-agent --tag v1.0.0

# 本地部署
agentarts deploy --mode local --local-port 3000

# 指定 SWR
agentarts deploy --swr-org my-org --swr-repo my-repo

# 跳过构建（使用已有镜像）
agentarts deploy --skip-build

# 构建 AMD64 架构镜像
agentarts deploy --platform linux/amd64

# 构建 ARM64 架构镜像（默认）
agentarts deploy --platform linux/arm64

# 完整参数
agentarts deploy -a my-agent -m cloud -t v2.0 --swr-org my-org --swr-repo my-repo \
  -d "Production deployment v2.0"
```

```powershell
# 完整参数
agentarts deploy -a my-agent -m cloud -t v2.0 --swr-org my-org --swr-repo my-repo `
  -d "Production deployment v2.0"
```

## 部署前准备

- JDK 17+、Maven 3.9+；非 `--skip-build` 模式需要正在运行的 Docker daemon。
- 云模式需要最小权限的 IAM/SWR/AgentArts 凭证，且凭证仅由进程环境注入。
- 发布前固定镜像 tag 或 digest；生产部署不建议复用可变的 `latest`。
- `--skip-ssl-verification` 仅用于隔离诊断，不应用于生产。
- `--platform` 参数用于指定 Docker 构建的目标平台架构。支持的值：
  - `linux/arm64`（默认）：ARM 64位架构，适用于 ARM 服务器（如华为鲲鹏）或 Apple Silicon
  - `linux/amd64`：标准 x86_64 架构，适用于大多数云服务器
  - 其他 Docker 支持的平台格式（`os/arch`）
  - 注意：构建非本地架构的镜像需要 Docker Buildx 或多架构构建支持

确保配置文件正确：

```yaml
# .agentarts_config.yaml
agents:
  my-agent:
    base:
      name: my-agent
      entrypoint: com.example.MyAgent
      region: cn-southwest-2
    swr_config:
      organization: my-org
      repository: my-repo
    runtime:
      invoke_config:
        protocol: HTTP
        port: 8080
```

## 常见问题

### 部署失败？

1. 检查 AK/SK 权限
2. 确认 SWR 组织/仓库存在
3. 检查网络连接
4. 根据最先失败的 step 检查 Maven、Docker daemon、SWR 登录或 Runtime 控制面错误；CLI 不会把云端错误当作成功继续执行

### 镜像 URL 优先级

1. 配置文件中的 `runtime.artifact_source.url`（最高，跳过 SWR 推送）
2. `--swr-org` + `--swr-repo` 命令行参数
3. 配置文件中的 `swr_config`

> 若以上均未提供，deploy 将报错（不会自动回退到 Agent 名称）。
