# agentarts CLI 指引

> `agentarts` CLI 已统一由 **[Python SDK](https://github.com/huaweicloud/agentarts-sdk-python)** 提供与维护。
> 本 Java 仓库不再自带 CLI(不再有 picocli 命令、不再需构建 CLI jar)。

## 安装

CLI 自带 Python 解释器,使用时无需安装 Python。对 Java agent,CLI 会调用你本机的 JDK + Maven。

**Linux / macOS 一键安装:**

```bash
curl -fsSL https://raw.githubusercontent.com/huaweicloud/agentarts-sdk-python/main/install.sh | sh
```

**其他方式**(Windows、手动下载、`pip install agentarts-sdk`):见
[Python SDK README 的安装章节](https://github.com/huaweicloud/agentarts-sdk-python#installation)。

## 命令

CLI 覆盖全生命周期,同时支持 Python 与 Java agent。对 Java agent,它识别配置里的
`language: java17` 并调用 Maven/JDK:

| 命令 | 说明 |
| --- | --- |
| `agentarts init -t java-basic` / `-t java-agentscope` | 生成 Java 项目(pom.xml + Agent.java + temurin Dockerfile) |
| `agentarts dev` | 编译(`mvn compile`)→ 起子 JVM → `/ping` 就绪 → 源码轮询热重载 |
| `agentarts config` | 读写 `.agentarts_config.yaml`(已支持 `pom.xml` 与 Java 字段推导) |
| `agentarts deploy` | `mvn package` 产 fat jar → docker build/push → 创建云端 runtime |
| `agentarts invoke` / `destroy` / `runtime` / `gateway` / `memory` | 纯 HTTP/文件操作,与语言无关 |

完整命令、子命令、选项与示例,以
[Python SDK 仓库的 README 与文档](https://github.com/huaweicloud/agentarts-sdk-python#readme)
为准。

## Java agent 的前置工具链

CLI 二进制只内嵌 Python 解释器,**带不了 JVM**。要跑 `agentarts dev` / `agentarts deploy`
针对 Java agent,本机需另备:

- **JDK 17+**
- **Maven 3.9+**

Python agent 则无需额外安装(解释器已随 CLI 附带)。

## 已有自己的 Java 项目?

不想用 `init` 脚手架,只想生成配置文件:

```bash
agentarts config -n my-agent -e com.example.MyAgent --dependency-file pom.xml \
  --region cn-southwest-2 --swr-org <o> --swr-repo <r>
```

会生成 `.agentarts_config.yaml`(`language: java17` + temurin 基础镜像)与 Dockerfile,不动你的代码。
