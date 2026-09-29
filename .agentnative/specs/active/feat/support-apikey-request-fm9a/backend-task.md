---
spec_id: "7379276505"
title: "veadk-java Viking 数据面支持 API Key 鉴权"
status: "draft"
template_id: "backend-task-v1"
schema_version: 1
created_at: "2026-09-29"
updated_at: "2026-09-29"
---

# Tasks: veadk-java Viking 数据面支持 API Key 鉴权

> 执行依据为同目录 `prd-spec.md` 与 `backend-design.md`。只做列出的最小变更；不升级依赖、不改服务端协议、不增加数据库或 Feature Gate。每组任务完成时同步运行对应定向测试，不将真实凭据写入代码、日志或测试输出。

## 1. 配置解析与兼容契约

- [ ] 1.1 在 `EnvUtil` 增加 `DATABASE_VIKING_API_KEY`、`DATABASE_VIKINGMEM_API_KEY` 的领域隔离读取，以及 `trim` 后空白/大小写不敏感 `none`、`null` 归一为未配置的函数。
- [ ] 1.2 增加“有效显式 API Key > 有效领域环境变量”的解析函数，以及仅供 Viking 多凭据流程使用的 optional AK/SK getter；保留现有 `getAccessKey()`、`getSecretKey()` 必填语义。
- [ ] 1.3 扩展 `VikingKnowledgebaseConfig` 保存解析后的 API Key 与完整 AK/SK 判定；保留四参数构造器和无参 `fromEnv()`，增加带 API Key 的兼容重载。
- [ ] 1.4 扩展 `EnvUtilTest`，参数化验证显式/环境优先级、`null`/空白/`none`/`NULL` 清洗、Knowledge/Memory 隔离和旧 AK/SK getter 行为。

## 2. 实现共享 API Key HTTP transport

- [ ] 2.1 新增 `VikingApiKeyHttpClient`，使用 JDK 17 `HttpClient` 向当前北京 endpoint 发送 JSON POST，设置 `Accept`、`Content-Type`、`Authorization: Bearer <apiKey>`，连接和请求超时均为 5 秒。
- [ ] 2.2 将共享 client 声明为 `public final` 以供两个 sibling wrapper 调用，仅公开构造器、`post(String path, String jsonBody)` 和不可变最小响应；可替换 transport 保持 package-private 供同包测试捕获请求。一次调用只 `send` 一次，不实现重试或 AK/SK fallback。
- [ ] 2.3 对中断恢复线程标志并抛出安全 `IOException`；禁止在 helper 的日志、异常、`toString` 中包含 key、完整 header、请求体或完整响应体。
- [ ] 2.4 新增 `VikingApiKeyHttpClientTest`，断言 endpoint/path/header/body/5 秒超时/状态透传/单次发送/中断恢复，所有凭据使用唯一假值。

## 3. 在现有 Viking wrapper 中按操作路由鉴权

- [ ] 3.1 为 `VikingKnowledgebaseWrapper` 增加 `(accessKey, secretKey, apiKey)` 公开构造器和可注入共享 client 的 package-private 测试构造器；旧双参数构造器保留并委托 `apiKey=null`。仅 `searchKnowledge` 在 API Key 有效时走 Bearer transport，collection 管理与 `addDoc` 保持 AK/SK `json(...)`。
- [ ] 3.2 为 `VikingMemoryWrapper` 增加 `(accessKey, secretKey, apiKey)` 公开构造器和可注入共享 client 的 package-private 测试构造器；旧双参数构造器保留并委托 `apiKey=null`。仅 `addSession`、`searchMemory` 在 API Key 有效时走 Bearer transport，collection 管理保持 AK/SK `json(...)`。
- [ ] 3.3 在两个 wrapper 复用当前请求 body 和响应映射；先判断 HTTP 2xx，再将顶层数值 `code != 0` 或非空对象 `ResponseMetadata.Error` 判为业务错误，并识别畸形 JSON，保持 Knowledge 失败/IO 为空列表、Memory 添加非成功为 `false`、Memory 查询非成功为空列表以及现有异常传播边界。
- [ ] 3.4 失败日志只记录操作、collection、`authMode=API_KEY`、status 和安全 request ID；不记录 API Key、AK/SK、Authorization、请求体或完整响应。401/403/404/5xx 和网络失败均不得调用另一鉴权 client。
- [ ] 3.5 扩展两个 wrapper 测试，以同一 fixture 对照 API Key/AK-SK 映射，并验证每个数据面操作的路径、选路、失败语义与调用次数。

## 4. 接入 KnowledgeBase 公开入口与管理边界

- [ ] 4.1 在 `KnowledgeBase.Builder` 增加 `apiKey(String)`，Viking backend 构建时传入配置；增加 `KnowledgeBase.viking(String, String)` 便捷重载。OpenSearch 与 `backendInstance` 路径不消费该值。
- [ ] 4.2 扩展 `VikingKnowledgebaseBackend`：完整 AK/SK 时保持 collection 检查/创建，API Key-only 时跳过；数据面查询使用 config 固定的鉴权模式。
- [ ] 4.3 API Key-only 调用 `addDoc` 时，在任何网络调用前以安全错误指出需要 AK/SK；双凭据时 `addDoc` 与管理面仍走 AK/SK。
- [ ] 4.4 为 deprecated `VikingKnowledgebaseService` 增加 `(appName, apiKey)` 重载，保留单参数构造器、`topK=5` 和 `SearchKnowledgebaseResponse` 类型。
- [ ] 4.5 扩展 backend、`KnowledgeBase` 和 deprecated service 测试，覆盖显式/环境 key、空查询、API Key-only、双凭据、管理/`addDoc` 边界和旧入口编译行为。

## 5. 接入 Viking Memory 公开入口与管理边界

- [ ] 5.1 为 `VikingMemoryService` 增加 `(appName, apiKey)` 构造器，单参数构造器委托 `apiKey=null`；按 Memory 专属变量解析，保存构造期固定的鉴权配置。
- [ ] 5.2 完整 AK/SK 时保持 collection 检查/创建，API Key-only 时跳过；双凭据时管理面走 AK/SK，添加与查询走 API Key。
- [ ] 5.3 保持既有 user message 筛选、空会话零请求、metadata、event types、`topK=5`、`MemoryEntry`/`SearchMemoryResponse` 和 RxJava 成功/失败语义。
- [ ] 5.4 扩展 `VikingMemoryServiceTest`，覆盖显式优先、环境回退、领域隔离、API Key-only、双凭据、AK/SK-only、缺少凭据、添加/查询和空会话。

## 6. 安全与失败回归

- [ ] 6.1 使用唯一假 API Key、AK、SK 分别触发非成功响应、IOException、InterruptedException 和缺失配置，断言异常与可捕获日志不含任何假凭据或完整 Authorization。
- [ ] 6.2 对 Knowledge 查询、Memory 添加、Memory 查询的鉴权失败分别断言 API Key transport 恰好调用一次，AK/SK 数据面调用为 0；对 API Key-only 构造断言 collection 管理调用为 0。
- [ ] 6.3 运行现有 AK/SK 测试并补齐旧构造器、同步/异步入口、Google ADK service 返回类型和空结果语义回归。

## 7. 文档、格式、构建与覆盖率门禁

- [ ] 7.1 更新 `README.md` 与 `README_zh.md`：说明两个 API Key 环境变量、显式 Java 入口、空值规则、配置优先级、数据面范围、双凭据分工，以及 API Key-only 必须使用已有 collection；示例仅使用占位符。
- [ ] 7.2 运行 `./mvnw -pl core spotless:check`，修正后重跑至退出码为 0；确认没有业务范围外格式化。
- [ ] 7.3 运行定向测试：`./mvnw -pl core -Dtest=EnvUtilTest,VikingApiKeyHttpClientTest,VikingKnowledgebaseWrapperTest,VikingKnowledgebaseBackendTest,KnowledgeBaseTest,VikingKnowledgebaseServiceTest,VikingMemoryWrapperTest,VikingMemoryServiceTest test`。
- [ ] 7.4 运行 `./mvnw -pl core test` 和 `./mvnw -pl core -am -DskipTests package`，记录命令、退出码和关键日志；本设计无生成文件，若实际出现生成差异则停止并查明来源。
- [ ] 7.5 从 `core/target/site/jacoco/jacoco.xml` 结合 `git diff --unified=0 <before>...HEAD -- core/src/main/java` 统计本次增量可执行行覆盖率，记录基线 SHA、覆盖分子/分母、百分比和未覆盖行；达到至少 90%，并检查配置/路由/异常分支覆盖，否则补测后重跑。
- [ ] 7.6 审查 `git status --porcelain` 和完整 diff，逐项映射到 REQ/Test；只显式 stage 本任务文件，提交并 push 当前分支，验证远端 ref 指向 after commit。

## 8. 交付证据清单

- [ ] 8.1 记录所有实际变更、环境变量与 Java 公开签名，并确认没有依赖、Proto、数据库、Feature Gate、生成物或非 Viking 行为变更。
- [ ] 8.2 汇总 T-001～T-015 的通过证据，特别列出 API Key-only、双凭据、不 fallback、Secret 不泄露和 AK/SK 回归结论。
- [ ] 8.3 记录格式化、定向测试、全量 core 测试、构建和 JaCoCo 增量覆盖率的命令、退出码、日志/报告位置。
- [ ] 8.4 记录提交前 dirty 文件归属、显式 stage 文件、before/after commit、diff stat、remote、branch、push 结果、远端 ref 校验和提交后干净工作区。
