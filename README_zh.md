# veadk-java

一款集成火山引擎强大能力的开源智能体（Agent）开发工具包。

## 快速开始

### 环境要求
- JDK `17` 或以上版本

```xml
<dependency>
    <groupId>com.volcengine.veadk</groupId>
    <artifactId>veadk-java</artifactId>
    <version>0.0.1</version>
</dependency>
```
```java
public class QuickstartAgentExample {
    public static void main(String[] args) {
        // Use an Ark model (replace with a model name available in your Ark Console)
        BaseAgent agent = LlmAgent.builder()
            .name("quickstart-agent")
            .instruction("You are a helpful assistant.")
            .model(new ArkLlm("doubao-seed-1-8-preview-251115"))
            .build();

        Runner runner = new Runner(agent);

        Session session = runner.sessionService()
            .createSession(runner.appName(), "userId", null, "sessionId")
            .blockingGet();

        // Build a simple conversation
        Content userMsg = Content.fromParts(Part.fromText("hello!"));
        RunConfig runConfig = RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.NONE).build();
        Flowable<Event> events = runner.runAsync(session.userId(), session.id(), userMsg, runConfig);

        // Print the final reply
        events.blockingForEach(e -> {
            if (e.finalResponse()) System.out.println(e.stringifyContent());
        });
    }
}
```

### 必需环境变量
示例工程在运行前需要配置以下环境变量（缺失时会抛出明确错误）：

  - `MODEL_AGENT_API_KEY`：火山方舟服务的 API Key（`ArkLlm` 使用）
 
示例设置（macOS / Linux）：

```bash
export MODEL_AGENT_API_KEY="<your-ark-api-key>"
```

## 运行项目示例

### 构建项目
在仓库根目录执行：`./mvnw clean -DskipTests package`

构建完成后，`example/target` 会生成示例所需的编译产物。

### 运行示例（CLI）
示例入口：`com.volcengine.veadk.example.AgentCliRunner`。

运行方式（无需修改 POM，直接通过 Maven Exec 插件坐标）：

```bash
./mvnw -pl example -q compile exec:java -Dexec.mainClass=com.volcengine.veadk.example.AgentCliRunner
```

交互说明：
- 启动后按提示输入对话内容，与 `ArkAgent` 进行交互。
- 输入 `quit` 退出。

### 运行示例（Web UI）
启动命令：

```bash
./mvnw -pl example -q compile exec:java \
    -Dexec.mainClass="com.google.adk.web.AdkWebServer" \
    -Dexec.args="--adk.agents.source-dir=example/target --server.port=8000"  
```

- 访问地址：`http://localhost:8000`

### 在 IDE 中运行
- 使用 IntelliJ IDEA 或 Eclipse 导入 Maven 多模块工程。
- 直接运行 `AgentCliRunner` 或 `AdkWeb` 的 `main` 方法即可。
- 确保在 IDE 的运行配置中注入必需环境变量（或使用 shell 启动 IDE）。
- 如果需要使用web search、viking memory、viking knowledgebase，需要配置环境变化：
  - VOLCENGINE_ACCESS_KEY：火山引擎AccessKey
  - VOLCENGINE_SECRET_KEY：火山引擎SecretKey
- 如果需要使用 Mem0 Memory，可以直接配置 Mem0 API Key：
  - `DATABASE_MEM0_API_KEY`：Mem0 API Key
  - `DATABASE_MEM0_BASE_URL`：Mem0 服务地址，例如 `https://api.mem0.ai`
- 也可以配置火山 AK/SK 和 Mem0 项目信息，由 VeADK 自动解析 Mem0 API Key：
  - `VOLCENGINE_ACCESS_KEY`：火山引擎 AccessKey
  - `VOLCENGINE_SECRET_KEY`：火山引擎 SecretKey
  - `REGION`：火山引擎 Region，默认 `cn-beijing`
  - `DATABASE_MEM0_PROJECT_ID`：Mem0 记忆项目 ID
  - `DATABASE_MEM0_API_KEY_ID`：可选，Mem0 API Key ID
- 如果需要使用TLS Trace，除了AK/SK，还需要配置TLS Topic
  - OBSERVABILITY_OPENTELEMETRY_TLS_SERVICE_NAME：TLS服务trace日志主题的id

运行 Mem0 记忆示例：

```bash
./mvnw -q install -DskipTests
./mvnw -pl example -q compile exec:java -Dexec.mainClass=com.volcengine.veadk.example.Mem0MemoryAgent
```

## 相关项目
- Python 版本与文档参考：[veadk-python](https://github.com/volcengine/veadk-python)。

## 常见问题
- 启动时报错 `Missing required configuration: <ENV_NAME>`：表示必需环境变量未设置，请根据提示进行补全。
- 无法访问内存/知识库/搜索服务：请检查 AK/SK 与网络连通性。
- 端口占用：如 `8000` 端口被占用，可通过`--server.port`或者`AdkWeb.main`中调整启动参数。

## 许可证
本项目使用 Apache License 2.0，详见 `LICENSE` 文件。
