---
spec_id: "7379276505"
title: "veadk-java Viking 数据面支持 API Key 鉴权"
status: "draft"
template_id: "backend-design-v1"
schema_version: 1
linked_spec: "prd-spec.md"
baseline_design: null
depends_on_designs: []
supersedes_designs: []
created_at: "2026-09-29"
updated_at: "2026-09-29"
---

# Design: veadk-java Viking 数据面支持 API Key 鉴权

> 本设计基于同目录 `prd-spec.md`、veadk-java 当前实现和 veadk-python `31d2c67be6fb9bd7f3139de42d2615c2c4a73e6f` 的已验证行为。Spec 与设计冲突时以 Spec 为准。

---

# 1. 总体方案

在 SDK 实例构造阶段分别解析 KnowledgeBase 与 Memory 的 API Key，并将鉴权选择固定到实例：有效显式参数优先于对应环境变量；没有有效 API Key 时使用完整 AK/SK。现有 `BaseServiceImpl` 客户端继续承载 collection 管理、KnowledgeBase `addDoc` 和无 API Key 时的数据面请求；新增一个基于 JDK 17 `HttpClient` 的小型 Bearer transport，仅承载 KnowledgeBase 查询、Memory 添加和 Memory 查询。双凭据场景下，初始化管理面使用 AK/SK、数据面使用 API Key；缺少完整 AK/SK 的 API Key-only 场景跳过 collection 检查和创建。

```text
显式 apiKey ─有效─┐
                 ├─> 固定 dataPlaneAuth=API_KEY ─> JDK HttpClient + Bearer
环境 apiKey ─有效─┘                                  │
                                                    ├─ Knowledge search
无有效 apiKey + 完整 AK/SK ─> dataPlaneAuth=AK_SK ──┤─ Memory add/search
                                                    │
完整 AK/SK ─> collection 管理 / Knowledge addDoc ──> volc-sdk-java 签名客户端
无完整 AK/SK + 有效 API Key ─> 跳过初始化管理面
```

**涉及的代码层**（不涉及的标“无”）：

| 层 | 目录 | 改动范围 | 关联 REQ / NFR |
| --- | --- | --- | --- |
| 公开 SDK 入口 | `core/src/main/java/com/volcengine/veadk/knowledgebase/`、`core/src/main/java/com/volcengine/veadk/memory/viking/` | 增加可选显式 API Key 入口，保留现有构造器、builder 和返回类型 | REQ-001、REQ-002、REQ-003、REQ-005、NFR-001 |
| 配置 | `core/src/main/java/com/volcengine/veadk/utils/`、`knowledgebase/backends/viking/` | 领域隔离的环境变量、空值清洗、优先级与完整 AK/SK 判定 | REQ-003、REQ-004、REQ-006、NFR-002、NFR-005 |
| Client / Integration | `core/src/main/java/com/volcengine/veadk/integration/` | 保留 AK/SK wrapper，新增共享 Bearer POST transport，并在 wrapper 中按操作路由 | REQ-001、REQ-002、REQ-004、REQ-006、NFR-003、NFR-004、NFR-006 |
| Tests | `core/src/test/java/com/volcengine/veadk/` | 配置矩阵、公开入口、请求契约、管理边界、失败不降级和凭据保护 | REQ-001～REQ-006、NFR-001～NFR-006 |
| Documentation | `README.md`、`README_zh.md` | 环境变量、显式入口、优先级、范围和 API Key-only 前置条件 | REQ-003、REQ-004、REQ-006 |
| Proto / 服务端 OpenAPI | 无 | 不改变 Viking 服务端协议 | NG-001、NG-005 |
| DB Schema / 持久化 | 无 | 不新增表、字段、索引、DML、迁移或回填 | Spec §6.2 |
| 异步任务 / Workflow | 无 | reactive 入口复用同一 backend/wrapper，不新增任务 | REQ-001、REQ-002、REQ-005 |

**ADR**：

- ADR-001：API Key 数据面采用独立 JDK `HttpClient` transport，而不是复用 `BaseServiceImpl.json(...)`。仓库依赖的 `volc-sdk-java:1.0.250` 在 `BaseServiceImpl.makeRequest(...)` 中无条件执行 AK/SK 签名，`setApiKey` 也不是 Viking Bearer 通用能力；复用它会造成错误签名或同时携带两种鉴权。JDK 17 已是项目基线，仓内 `Mem0RuntimeClient` 已有可测试 transport 先例，因此无需新增依赖。
- ADR-002：只抽取共享的低层 `VikingApiKeyHttpClient`，响应映射与失败语义留在两个现有 wrapper。该类放在两个 wrapper 的公共父包 `com.volcengine.veadk.integration.viking`，声明为 `public final`，但只公开 wrapper 所需的构造器、`post(String path, String jsonBody)` 与只读 response；可替换 transport 保持 package-private，仅供同包测试。两类响应结构不同；这一边界既避免复制 HTTP/Bearer/超时逻辑，也避免引入统一领域客户端或改写现有 AK/SK 路径。
- ADR-003：鉴权在构造阶段解析并固定，不在请求失败后动态切换。这样可保证显式参数优先、并发调用无共享状态竞争，且 Memory 写入不会因 fallback 被重复提交。

# 2. 文件清单

| # | 文件路径 | 新增/修改 | 职责 | 关联 spec |
| --- | --- | --- | --- | --- |
| F-01 | `core/src/main/java/com/volcengine/veadk/utils/EnvUtil.java` | 修改 | 增加两个 API Key 环境变量读取、有效值清洗、显式优先解析和可选 AK/SK 读取；保留现有必填 getter | REQ-003、REQ-004、REQ-005 |
| F-02 | `core/src/main/java/com/volcengine/veadk/integration/viking/VikingApiKeyHttpClient.java` | 新增 | 固定 endpoint、5 秒连接/请求超时、JSON POST、Bearer header、单次发送与可注入 transport | REQ-001、REQ-002、REQ-006、NFR-003、NFR-004、NFR-006 |
| F-03 | `core/src/main/java/com/volcengine/veadk/integration/vikingknowledgebase/VikingKnowledgebaseWrapper.java` | 修改 | Knowledge 查询按 API Key/AK-SK 路由；管理操作与 `addDoc` 只走 AK/SK | REQ-001、REQ-004、REQ-006 |
| F-04 | `core/src/main/java/com/volcengine/veadk/knowledgebase/backends/viking/VikingKnowledgebaseConfig.java` | 修改 | 保存解析后的 `apiKey`，提供完整 AK/SK 判定，兼容现有四参数构造器 | REQ-003、REQ-004、REQ-005 |
| F-05 | `core/src/main/java/com/volcengine/veadk/knowledgebase/backends/viking/VikingKnowledgebaseBackend.java` | 修改 | 按凭据完整性决定是否管理预检查；API Key-only 的 `addDoc` 在本地明确失败 | REQ-001、REQ-004、REQ-005 |
| F-06 | `core/src/main/java/com/volcengine/veadk/knowledgebase/KnowledgeBase.java` | 修改 | `Builder.apiKey(String)` 与 `viking(String, String)` 显式入口，共享原有同步/异步路径 | REQ-001、REQ-003、REQ-005 |
| F-07 | `core/src/main/java/com/volcengine/veadk/knowledgebase/viking/VikingKnowledgebaseService.java` | 修改 | deprecated 兼容类增加 API Key 构造重载，保留原构造器 | REQ-001、REQ-003、REQ-005 |
| F-08 | `core/src/main/java/com/volcengine/veadk/integration/vikingmemory/VikingMemoryWrapper.java` | 修改 | Memory 添加/查询按 API Key/AK-SK 路由；collection 管理只走 AK/SK | REQ-002、REQ-004、REQ-006 |
| F-09 | `core/src/main/java/com/volcengine/veadk/memory/viking/VikingMemoryService.java` | 修改 | 增加 API Key 构造重载、解析优先级并跳过 API Key-only 管理预检查 | REQ-002、REQ-003、REQ-004、REQ-005 |
| F-10 | `core/src/test/java/com/volcengine/veadk/utils/EnvUtilTest.java` | 修改 | API Key 环境读取、清洗、显式优先和领域隔离 | REQ-003、REQ-005、NFR-005 |
| F-11 | `core/src/test/java/com/volcengine/veadk/integration/viking/VikingApiKeyHttpClientTest.java` | 新增 | 捕获并断言 URI、header、body、超时、状态码和单次发送 | REQ-001、REQ-002、REQ-006、NFR-002～NFR-004 |
| F-12 | `core/src/test/java/com/volcengine/veadk/integration/vikingknowledgebase/VikingKnowledgebaseWrapperTest.java` | 修改 | API Key 查询映射、AK/SK fallback、非成功与 IO 语义、不降级 | REQ-001、REQ-004、REQ-006 |
| F-13 | `core/src/test/java/com/volcengine/veadk/knowledgebase/backends/viking/VikingKnowledgebaseBackendTest.java` | 修改 | API Key-only 跳过管理、双凭据管理、空查询、`addDoc` 边界 | REQ-001、REQ-004、REQ-005 |
| F-14 | `core/src/test/java/com/volcengine/veadk/knowledgebase/KnowledgeBaseTest.java` | 修改 | builder/shortcut 显式入口、环境回退及已有 backend 行为 | REQ-001、REQ-003、REQ-005 |
| F-15 | `core/src/test/java/com/volcengine/veadk/knowledgebase/viking/VikingKnowledgebaseServiceTest.java` | 修改 | deprecated 新旧构造入口兼容 | REQ-001、REQ-005 |
| F-16 | `core/src/test/java/com/volcengine/veadk/integration/vikingmemory/VikingMemoryWrapperTest.java` | 修改 | API Key 添加/查询、返回映射、非成功与 IO 语义、不降级 | REQ-002、REQ-004、REQ-006 |
| F-17 | `core/src/test/java/com/volcengine/veadk/memory/viking/VikingMemoryServiceTest.java` | 修改 | 显式/环境/API Key-only/双凭据、空会话与既有返回语义 | REQ-002～REQ-005 |
| F-18 | `README.md` | 修改 | 英文配置和使用边界 | Spec §7、REQ-006 |
| F-19 | `README_zh.md` | 修改 | 中文配置和使用边界 | Spec §7、REQ-006 |

不修改 `pom.xml`、生成物或业务范围外文件；如果实现中发现必须新增依赖或改变服务端字段，应停止并回到设计节点处理范围变化。

# 3. 数据模型

无数据库或 SDK 持久化模型变更。API Key 只保存在 Java 对象私有字段中，不进入序列化对象、`toString`、业务返回或持久化层。

## 3.1 内存配置字段

| Java 字段 | 来源 | 有效值规则 | 作用域 | 备注 |
| --- | --- | --- | --- | --- |
| `VikingKnowledgebaseConfig.apiKey` | `KnowledgeBase.Builder.apiKey(...)` / `VikingKnowledgebaseService` 重载 / `DATABASE_VIKING_API_KEY` | `trim` 后非空且不等于大小写不敏感的 `none`、`null` | Knowledge 查询 | 不用于 Memory、collection 管理或 `addDoc` |
| `VikingMemoryService.apiKey` 或等价私有配置 | 构造器显式参数 / `DATABASE_VIKINGMEM_API_KEY` | 同上 | Memory 添加、查询 | 不用于 Knowledge 或 collection 管理 |
| `accessKey` + `secretKey` | 现有构造参数 / `VOLCENGINE_ACCESS_KEY`、`VOLCENGINE_SECRET_KEY` | 两者均非空白才构成完整管理凭据 | 现有 AK/SK 路径 | 任何 API Key 有效时允许两者缺失；管理调用前仍须成对存在 |

解析伪代码：

```java
String apiKey = normalize(explicitApiKey);
if (apiKey == null) {
    apiKey = normalize(System.getenv(domainApiKeyEnv));
}
String ak = optionalEnv("VOLCENGINE_ACCESS_KEY");
String sk = optionalEnv("VOLCENGINE_SECRET_KEY");
boolean hasAkSk = isNotBlank(ak) && isNotBlank(sk);
if (apiKey == null && !hasAkSk) {
    throw missingCredentialError(domainApiKeyEnv, "VOLCENGINE_ACCESS_KEY",
            "VOLCENGINE_SECRET_KEY");
}
```

`EnvUtil.getAccessKey()` 与 `getSecretKey()` 的既有抛错行为保持不变，新增 optional getter 只供 Viking 多凭据解析，避免影响其他组件。错误只列配置名与操作，不拼接配置值。

## 3.2 数据库变更

无。不存在 DDL、DML、兼容顺序、数据恢复、回填或 GORM/代码生成，因此不调用 `db-migration`。

# 4. API 实现

不修改服务端 OpenAPI、Proto、RPC 或 TOP 网关。本节的 API 指 Java SDK 公开入口与既有 Viking HTTP 数据面。

## 4.1 Java SDK 公开入口

| 类 | 新增入口 | 兼容要求 |
| --- | --- | --- |
| `KnowledgeBase.Builder` | `apiKey(String apiKey)` | 仅在 `backend("viking")` 时消费；OpenSearch 构建不受该字段影响 |
| `KnowledgeBase` | `viking(String appName, String apiKey)` | 现有 `viking(String)` 原样保留并委托原 builder 语义 |
| `VikingKnowledgebaseConfig` | `VikingKnowledgebaseConfig(String accessKey, String secretKey, String apiKey, boolean rerank, int chunkDiffusionCount)`；现有四参数构造器委托 `apiKey=null` | 原构造器源码/二进制签名保留；`fromEnv()` 保留，并新增 `fromEnv(String explicitApiKey)` |
| `VikingKnowledgebaseService` | `VikingKnowledgebaseService(String appName, String apiKey)` | deprecated 类的现有单参数构造器保留，`topK=5` 不变 |
| `VikingMemoryService` | `VikingMemoryService(String appName, String apiKey)` | 现有单参数构造器保留；`topK=5`、event 筛选、reactive 返回类型不变 |

公开参数仅传原始字符串给统一解析函数；不得在参数校验、异常或日志中回显。空白、`none`、`null` 显式参数不覆盖有效环境变量。

## 4.2 HTTP 数据面契约

| 操作 | Path | API Key 模式 | AK/SK 模式 | 成功解析 / 失败语义 |
| --- | --- | --- | --- | --- |
| Knowledge 查询 | `/api/knowledge/collection/search_knowledge` | `Authorization: Bearer <apiKey>` | 现有 `json("SearchKnowledge", ...)` | 复用现有 `result_list` 映射；非成功或 IO 返回空列表 |
| Memory 添加 | `/api/memory/session/add` | `Authorization: Bearer <apiKey>` | 现有 `json("AddSession", ...)` | 复用现有 `session_id` 判断；非成功返回 `false`，IO/中断沿现有异常边界传播 |
| Memory 查询 | `/api/memory/search` | `Authorization: Bearer <apiKey>` | 现有 `json("SearchMemory", ...)` | 复用现有 `result_list` 映射；非成功返回空列表，IO/中断沿现有异常边界传播 |

共同要求：

- endpoint 固定为当前 `https://api-knowledgebase.mlp.cn-beijing.volces.com`，不新增地域或 BytePlus 分支。
- `Accept` 与 `Content-Type` 均为 `application/json`；连接超时和单请求超时均为 5 秒，与当前 wrapper 的 5000 ms 配置对齐。
- 每次调用只执行一次 `HttpClient.send`，不内建重试，不在 401/403/其他失败时调用 AK/SK 路径。
- `InterruptedException` 必须恢复线程中断标记，并转换为不含请求 header、body 或 key 的 `IOException`。
- helper 返回不可变的 `statusCode` 与 `body` 给 wrapper 解析，不记录 request、header 或完整 response。wrapper 先判 HTTP 2xx，再按已知的两种 Viking envelope 判定业务成功：若顶层存在数值 `code`，只有 `0` 才成功；若 `ResponseMetadata.Error` 是非空对象则失败。无上述错误标记的既有成功 fixture 继续按当前 `data` 解析。HTTP 非 2xx、HTTP 2xx 但业务报错、JSON 无法解析均进入该入口既有失败语义，且不 fallback。失败日志只包含操作、collection、`authMode=API_KEY`、HTTP status、可安全提取的业务错误码和 request ID，不得记录请求体、完整响应体或异常中可能包含的 Authorization。

## 4.3 KnowledgeBase 实现详情

**调用链**：`KnowledgeBase` 同步/异步/ADK service → `VikingKnowledgebaseBackend.search` → `VikingKnowledgebaseWrapper.searchKnowledge` → API Key client 或现有 AK/SK `json`。

关键实现要点：

- `VikingKnowledgebaseConfig` 完成一次优先级解析；wrapper 只根据最终 `apiKey != null` 选路，不在每次请求重新读取环境变量。wrapper 新增三参数公开构造器 `(accessKey, secretKey, apiKey)`，旧双参数构造器委托 `apiKey=null`；package-private 测试构造器可注入 `VikingApiKeyHttpClient`，不得将 transport 扩大为 SDK 公共配置。
- `VikingKnowledgebaseBackend` 保存 `hasCompleteAkSk`。构造时仍执行 collection 名校验；仅 `hasCompleteAkSk=true` 时调用 `isCollectionExists/createCollection`。
- API Key 与完整 AK/SK 同时存在时，上述管理预检查照旧，查询始终走 API Key。管理失败仍沿现有初始化边界处理，不归因于 API Key。
- API Key-only 时 `addDoc` 在调用 SDK 前抛出不含凭据的 `IllegalStateException`，指出该操作需要 `VOLCENGINE_ACCESS_KEY` 与 `VOLCENGINE_SECRET_KEY`；不得把 API Key 传给 `AddDoc`。
- 空白查询继续由 backend 直接返回 `List.of()`，API Key client 调用次数为 0；同步、`searchAsync` 与 `searchKnowledgebase` 均复用该行为。

## 4.4 Viking Memory 实现详情

**调用链**：`VikingMemoryService.addSessionToMemory/searchMemory` → `VikingMemoryWrapper.addSession/searchMemory` → API Key client 或现有 AK/SK `json`。

关键实现要点：

- 单参数构造器委托双参数构造器，显式参数为 `null`；双参数构造器按 Memory 专属环境变量解析，绝不读取 `DATABASE_VIKING_API_KEY`。`VikingMemoryWrapper` 新增三参数公开构造器 `(accessKey, secretKey, apiKey)`，旧双参数构造器委托 `apiKey=null`；package-private 测试构造器注入共享 HTTP client。
- 仅完整 AK/SK 才执行 `isCollectionExists/createCollection`；API Key-only 初始化只做 appName 和 event type 解析，不发送管理请求。
- 双凭据时 collection 管理用 AK/SK，`addSession` 与 `searchMemory` 用 API Key。
- 无符合既有规则的用户消息时继续正常完成且不发送 API Key 请求；`Metadata`、event type、`SearchMemoryResponse` 和 `MemoryEntry` 映射不变。
- `addSession` 返回 `false` 的现有外层完成语义不扩大；transport 抛出的异常继续由 `addSessionToMemory` 包装，查询异常继续由 `Single` 传播。

# 5. 服务编排

### REQ-003 / REQ-004：构造与鉴权路由

1. 校验 collection/appName，读取对应 API Key 的显式参数和环境变量并清洗。
2. 可选读取 AK/SK，计算是否为完整凭据对；无 API Key 且无完整 AK/SK 时在网络调用前失败。
3. 构造现有 AK/SK wrapper；API Key 有效时同时构造只持有该 key 的 Bearer client。
4. 完整 AK/SK 存在时执行既有 collection 检查/创建；否则跳过。
5. 数据面根据构造时固定的 API Key 是否存在选择一个 client；不做运行时 fallback。
6. 管理面和 KnowledgeBase `addDoc` 仅允许完整 AK/SK。

**边界处理**：

- 外部依赖超时：连接与请求各 5 秒；不重试，按现有各入口失败语义处理。
- 并发：配置和 client 字段构造后不可变；JDK `HttpClient` 可并发复用；不使用全局可变鉴权 header。
- 重复写入：Memory 添加不自动重试、不切换鉴权，单次调用最多一次远端写请求。
- 缺少凭据：错误仅说明领域、操作和缺失的环境变量名，不包含任何输入值。

# 6. 状态机

无业务状态机变更。实例的鉴权模式在构造后固定为以下二选一，生命周期内不发生状态转换：

| 实例模式 | 前置条件 | 数据面 | 管理面 |
| --- | --- | --- | --- |
| `API_KEY` | 对应领域 API Key 有效 | Bearer client | 完整 AK/SK 存在时可用，否则跳过/拒绝 |
| `AK_SK` | API Key 无效或缺失，AK/SK 完整 | 现有签名 client | 现有签名 client |

# 7. 异步任务

无新增异步任务、队列或调度。`KnowledgeBase.searchAsync/searchKnowledgebase`、`VikingMemoryService` 的 RxJava `Single/Completable` 继续包裹相同同步 wrapper；不新建线程池，JDK client 使用默认同步 `send`。

# 8. 外部依赖

| 系统 / 库 | 用途 | 客户端文件 | 超时 / 重试 | 变更 |
| --- | --- | --- | --- | --- |
| Viking KnowledgeBase / Memory | collection 管理与数据面 | 两个现有 wrapper、F-02 | API Key 连接/请求 5 秒；0 次重试 | 仅新增三个 Bearer 数据面路由 |
| `volc-sdk-java:1.0.250` | AK/SK 签名 | 两个现有 wrapper 的 `BaseServiceImpl` | 保持现状 | 不升级、不改依赖 |
| JDK 17 `java.net.http.HttpClient` | API Key Bearer 请求 | F-02 | 同上 | JDK 内置，无 Maven 依赖 |

不新增 Maven 依赖，不调用远程配置、密钥服务或 IAM fallback。

# 9. 配置与 Feature Gate

| 配置键 / 显式入口 | 默认值 | 用途 | 优先级 / 边界 |
| --- | --- | --- | --- |
| `KnowledgeBase.Builder.apiKey(String)` / Knowledge 构造重载 | `null` | Knowledge 查询 | 有效值优先于 `DATABASE_VIKING_API_KEY` |
| `DATABASE_VIKING_API_KEY` | 未配置 | Knowledge 查询 | 仅在显式值无效时使用；不用于 Memory/管理/`addDoc` |
| `VikingMemoryService(String, String)` 第二参数 | `null` | Memory 添加、查询 | 有效值优先于 `DATABASE_VIKINGMEM_API_KEY` |
| `DATABASE_VIKINGMEM_API_KEY` | 未配置 | Memory 添加、查询 | 仅在显式值无效时使用；不用于 Knowledge/管理 |
| `VOLCENGINE_ACCESS_KEY` + `VOLCENGINE_SECRET_KEY` | 未配置 | AK/SK 数据面、collection 管理、Knowledge `addDoc` | 只有完整一对才可用；有效 API Key 的数据面优先级更高 |

不新增 Feature Gate：这是向后兼容的 SDK 配置能力，启用条件就是调用方提供有效 API Key。API Key 不得出现在配置快照、`toString`、Metrics label 或日志字段。

# 10. 上线策略

| 步骤 | 动作 | 备注 |
| --- | --- | --- |
| 1 | 合入配置、transport、wrapper、service 与测试 | 无 DB、Proto、依赖或生成步骤 |
| 2 | 更新中英文 README 并发布兼容版本 | 明确仅数据面支持、API Key-only 需已有 collection |
| 3 | 使用假 API Key 在测试环境验证 Knowledge 查询、Memory 添加/查询以及双凭据路由 | 不在日志/报告保存真实凭据 |
| 4 | 常规 Maven artifact 发布 | 不需要 Feature Gate、灰度配置或服务端部署 |

**回滚预案**：回滚到变更前 artifact 即可；无数据或协议迁移。只配置 API Key 的应用在回滚后不可用，调用方必须切回完整 AK/SK；SDK 不在单次失败中自动降级。

# 11. 风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 错误复用 `BaseServiceImpl` 导致 Bearer 请求仍被 AK/SK 签名 | API Key 请求鉴权失败或职责混淆 | 独立 JDK transport；测试断言 API Key 路径不调用 `json(...)` |
| API Key-only 仍触发 collection 管理 | 构造即失败，违背数据面 key 权限边界 | 以完整 AK/SK 判定保护预检查；构造测试断言管理调用次数为 0 |
| 双凭据路由错误 | 管理面误用 API Key或数据面未按优先级用显式 key | 捕获请求 header，并分别验证管理 `json` 与数据面 transport 的调用 |
| 鉴权失败后 fallback | Memory 重复写或权限被意外放大 | 每次数据面只选择一个 client；失败测试断言各 transport 调用次数 |
| 日志或异常泄露 Secret | 凭据泄露 | transport 不记录 header/body；日志使用固定模板和最小上下文；唯一假 Secret 覆盖失败与中断路径 |
| API Key HTTP 响应与现有 SDK `RawResponse` 差异 | 返回结构或空结果语义回归 | wrapper 复用现有 JSON 映射逻辑，建立同一 fixture 的 AK/SK 与 API Key 对照测试 |
| 新公开重载破坏兼容 | 已有应用编译或行为变化 | 不删除/改签名；旧入口编译测试；不改变返回类型和默认 `topK` |
| HTTP 阻塞调用占用 RxJava 调用线程 | 高并发时延迟积累 | 与当前同步 wrapper 行为一致，5 秒请求上限；本期不引入异步 client 或线程池 |

# 12. 测试要点

## 12.1 场景矩阵

| Test ID | 场景 | 方法与关键断言 | 关联 REQ / NFR |
| --- | --- | --- | --- |
| T-001 | Knowledge 显式 API Key 优先 | 同时设置两个不同假 key；捕获 `Authorization` 为显式 `Bearer`，`json` 调用 0 次 | REQ-001.1、REQ-003.3、NFR-003 |
| T-002 | Knowledge 环境回退与空值清洗 | 参数化 `null`、空白、`none`、`NULL`；有效环境 key 被选中 | REQ-003.2、REQ-003.4、REQ-003.5 |
| T-003 | Knowledge API Key 查询兼容 | 同一响应 fixture 对 API Key/AK-SK 得到相同 content、metadata、空结果；空 query 为 0 请求 | REQ-001.2～REQ-001.4、REQ-005.3 |
| T-004 | Knowledge API Key 失败 | 401/403/404/500、HTTP 200 + 非零业务 `code`、畸形 JSON 与 IOException 返回既有空列表；每次仅一次 Bearer 请求，AK/SK 数据面调用 0 次 | REQ-001.5、REQ-004.3、NFR-004 |
| T-005 | Knowledge 管理边界 | API Key-only 构造不检查/创建，`addDoc` 本地拒绝；双凭据构造管理走 AK/SK、查询走 API Key | REQ-004.1～REQ-004.4 |
| T-006 | Knowledge 公开入口兼容 | 旧 builder、`viking(String)`、deprecated 单参数构造仍编译运行；新入口传入 API Key | REQ-005.1～REQ-005.3、NFR-001 |
| T-007 | Memory 显式/环境优先级与领域隔离 | 参数化显式/Memory env；只设置 Knowledge env 时 Memory 仍走 AK/SK | REQ-003.1～REQ-003.7 |
| T-008 | Memory API Key 添加 | 捕获 `/api/memory/session/add`、Bearer 和既有 payload；覆盖 HTTP 非 2xx、HTTP 200 + `ResponseMetadata.Error`、畸形 JSON与 IO，保持成功/失败/异常语义且一次调用 | REQ-002.1、REQ-002.3、REQ-002.6、NFR-004 |
| T-009 | Memory API Key 查询 | 捕获 `/api/memory/search`、Bearer、filter/event types；覆盖 HTTP 与业务 envelope 错误，映射与空结果保持且失败不降级 | REQ-002.2、REQ-002.3、REQ-002.5、REQ-002.6 |
| T-010 | Memory 管理与空会话 | API Key-only 构造管理调用 0 次；双凭据管理用 AK/SK；无有效用户消息时数据面调用 0 次 | REQ-002.4、REQ-004.1、REQ-004.2 |
| T-011 | 缺失凭据 | 无有效 API Key 且 AK/SK 缺失或不完整，在网络调用前报错，只含配置名 | REQ-003.6、REQ-004.5、REQ-006.2 |
| T-012 | Secret 不泄露 | 使用唯一假 key 触发非成功、IO 和中断；断言异常/可捕获日志不含 key、AK、SK、完整 Authorization 或请求体 | REQ-006.1～REQ-006.4、NFR-002 |
| T-013 | HTTP transport 契约 | fake transport 断言 endpoint/path、JSON headers、Bearer、5 秒超时、状态透传、线程中断恢复 | REQ-001、REQ-002、NFR-004、NFR-006 |
| T-014 | AK/SK 全回归 | 无 API Key 时运行原 wrapper/service 测试，管理、查询、添加、同步/异步返回不变 | REQ-005、NFR-001 |
| T-015 | README 安全与边界 | 示例仅使用占位符；明确优先级、两环境变量、数据/管理面和已有 collection 前提 | Spec §7、REQ-006.1 |

## 12.2 验证命令与覆盖率

实现阶段按顺序执行并保存命令、退出码和关键日志：

```bash
./mvnw -pl core spotless:check
./mvnw -pl core -Dtest=EnvUtilTest,VikingApiKeyHttpClientTest,VikingKnowledgebaseWrapperTest,VikingKnowledgebaseBackendTest,KnowledgeBaseTest,VikingKnowledgebaseServiceTest,VikingMemoryWrapperTest,VikingMemoryServiceTest test
./mvnw -pl core test
./mvnw -pl core -am -DskipTests package
```

JaCoCo 报告使用仓库现有 `jacoco-maven-plugin`，路径为 `core/target/site/jacoco/jacoco.xml` 与 `index.html`。增量覆盖口径如下：

1. 以开发节点 before commit 为基线，通过 `git diff --unified=0 <before>...HEAD -- core/src/main/java` 取得本次新增/修改的可执行 Java 行。README、测试、纯注释、声明行及 JaCoCo XML 中没有 `<line>` 记录的非可执行行不进入分母。
2. 用 JaCoCo XML 中对应 `package/sourcefile/line` 的 `ci`、`mi` 计数逐行判定；`ci > 0` 为覆盖，`mi > 0 && ci == 0` 为未覆盖。分子为覆盖的增量可执行行数，分母为全部增量可执行行数。
3. 输出基线 SHA、分子/分母、覆盖率和未覆盖文件:行清单；覆盖率必须大于等于 90%，否则补测并重跑。新增/修改分支逻辑同时查看 JaCoCo branch counter，配置优先级、鉴权选择、管理边界与异常分支不得仅靠行覆盖通过。

若实现产生生成文件，必须说明仓库生成命令；按本设计不应产生任何生成文件。

## 12.3 Requirement → Design → Task → Test 追踪

| Requirement | Design 落点 | 开发任务 | Test |
| --- | --- | --- | --- |
| REQ-001 | §4.2、§4.3 | Task 3、4 | T-001、T-003、T-004、T-006、T-013 |
| REQ-002 | §4.2、§4.4 | Task 3、5 | T-007～T-010、T-013 |
| REQ-003 | §3.1、§4.1、§9 | Task 1、4、5 | T-001、T-002、T-007、T-011 |
| REQ-004 | §4.3、§4.4、§5 | Task 3～5 | T-005、T-010、T-011 |
| REQ-005 / NFR-001 | §4.1、§6 | Task 4、5、7 | T-003、T-006、T-009、T-014 |
| REQ-006 / NFR-002 | §4.2、§5、§11 | Task 2、3、6 | T-004、T-011～T-013、T-015 |
| NFR-003 | §1 ADR-003、§4.2 | Task 2、3 | T-001、T-005、T-007～T-010 |
| NFR-004 | §4.2、§5 | Task 2、3、6 | T-003、T-004、T-008～T-010、T-013 |
| NFR-005 | §3.1、§12.1 | Task 1、6 | T-001～T-014 |
| NFR-006 | §4.2、§8 | Task 2、7 | T-013、T-014 |
