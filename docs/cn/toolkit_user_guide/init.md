# agentarts init — 初始化项目

`init` 命令用于创建新的 AgentArts Agent 项目骨架。

## 用法

```bash
agentarts init [选项]
```

## 参数

| 参数 | 缩写 | 说明 | 默认值 |
|------|------|------|--------|
| `--name` | `-n` | 项目名称 | 省略时交互式提示输入（默认 `myagent-随机5位`） |
| `--template` | `-t` | 项目模板 | `basic` |
| `--path` | | 项目路径 | 当前目录 |
| `--region` | `-r` | 华为云区域 | 省略时交互式提示输入（默认 `cn-southwest-2`） |
| `--swr-org` | | SWR 组织名 | `agentarts-{区域缩写}-{AK哈希8位}-org`（无 AK 时用随机字符） |
| `--swr-repo` | | SWR 仓库名 | `agent_<项目名>` |

## 模板类型

| 模板 | 说明 |
|------|------|
| `basic` | 基础 Java 处理器 |
| `agentscope` | agentscope-java ReActAgent 集成 |

> `langgraph`、`langchain`、`google-adk` 属于 Python framework 模板，不在 Java SDK 的对齐范围。未知模板会在写文件前失败。Dockerfile 在所有 Java 模板下都会生成，无需单独的 `docker` 模板。

## 示例

```bash
# 交互式初始化：提示输入项目名，其余参数取默认值
agentarts init
# Project name: my-agent

# 快速创建（跳过交互）
agentarts init -n my-agent -t basic -r cn-southwest-2

# 指定完整参数
agentarts init --name my-agent --template agentscope --region cn-southwest-2 --swr-org my-org --swr-repo my-repo
```

> `agentarts init` 不带 `--name` / `--region` 时进入交互模式：在终端依次提示输入项目名和区域，均提供默认值（项目名默认 `myagent-随机5位`，区域默认 `cn-southwest-2`），直接回车或按 Ctrl-D 即采用默认值。非交互环境（管道输入、CI、IDE 测试桩等无 TTY）下不提示：缺 `--name` 直接报 `--name is required` 并以非零退出码结束，缺 `--region` 静默使用默认区域 `cn-southwest-2`。

生成过程先写入同级临时目录，全部成功后再移动到目标位置；目标目录已存在时拒绝覆盖，失败会清理临时内容，避免留下半生成项目。

## 生成的项目结构

```
my-agent/
├── pom.xml                          # Maven 构建文件
├── Dockerfile                       # 容器镜像构建文件
├── src/main/java/com/example/
│   └── Agent.java                   # Agent 入口类（public class Agent，与文件名一致）
└── .agentarts_config.yaml           # AgentArts 配置文件
```

## 后续步骤

`init` 成功后控制台会打印项目结构与下一步指引，与 Python SDK 的 Next Steps 对齐（已适配 Java 工程）：

```text
Next Steps - How to Deploy Agent

Step 1: Navigate to project directory
    cd my-agent

Step 2: Build the project
    mvn compile

Step 3: Edit src/main/java/com/example/Agent.java to implement your agent logic

Step 4: Edit .agentarts_config.yaml and run config command (optional)
    Modify configuration as needed, then:
    agentarts config

Step 5: Deploy to Huawei Cloud
    agentarts deploy
```
