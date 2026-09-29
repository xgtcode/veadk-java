# Viking 数据面 API Key 后端技术设计

## 1. 需求与仓库对齐

### 1.1 输入与结论

- 最新业务输入：`artifacts/workflow-node-artifact-yew395ryf4jxj0wbalkd/prd-spec.md`，对应仓库文件 `.agentnative/specs/active/feat/support-viking-apikey-4nm8/prd-spec.md`。
- Gate：`artifacts/workflow-node-artifact-yew4l9aq68rv0yt4f9x9/result.yaml`，结论为 `PASS / NONE`，Human 已批准。
- 需求澄清：`artifacts/workflow-node-artifact-yew36x8q9srv0yt4fg5e/viking-apikey-requirement-clarification-v2.md`。
- 返修输入：`artifacts/workflow-node-artifact-yew4qbsc8wrv0yt4ftbo/result.yaml`，P1 finding `DESIGN-REV-001` 要求补齐 Memory AddSession 的 `session_id` 来源、规范化、透传、重放/重试语义和测试断言。
- Python 行为基线：`volcengine/veadk-python@31d2c67be6fb9bd7f3139de42d2615c2c4a73e6f`；Memory API Key 传输语义另以其锁定依赖 `vikingdb-python-sdk==0.1.32` 的 `APIKey`、`VikingMem` 实现核验。
- 当前 Java 仓同步基线：`5d99ab3a620709fd0863154b4602239ffada0be3`，原设计提交已在该基线上 rebase；Java 17、Maven 多模块，目标实现均在 `core`。
- 仓库未提供 `.agentnative/templates/default/design.md` 或 `task.md`，本文按 `tech-design` 所要求的等价语义结构编写；仓库也未发现额外 `AGENTS.md`、`CLAUDE.md`、`CODEX.md` 或测试目录规范。

结论：只扩展 Volcengine Viking Knowledgebase 搜索以及 Viking Memory 添加、检索三条数据面路径。有效显式 API Key 优先于对应环境变量；没有有效 API Key 时才使用一对有效 AK/SK。API key-only 初始化跳过 collection 管理预检查；管理操作仍只走 AK/SK。

### 1.2 当前 Repo 职责边界

本仓负责：

- 在 Java 公共入口提供 Knowledgebase 与 Memory 各自的显式 API Key 参数。
- 解析 `DATABASE_VIKING_API_KEY`、`DATABASE_VIKINGMEM_API_KEY`，并保持现有 `VOLCENGINE_ACCESS_KEY`、`VOLCENGINE_SECRET_KEY` 兼容。
- 为三条数据面请求选择 Bearer API Key 或现有 AK/SK 签名链。
- 将 ADK `Session.id()` 作为 Viking Memory AddSession 的稳定业务会话标识，按服务约束校验后透传。
- 保持返回类型、查询参数、消息筛选、memory type、topK、rerank 和 chunk diffusion 语义。
- 补齐安全失败传播、单元测试、中英文 README。

本仓不负责：

- Viking 服务端 API、权限模型、API Key 签发或 collection 生命周期。
- BytePlus、新的 Viking 业务 API、TOS 上传、数据库、OpenAPI/RPC、前端或部署流程。
- 用 API Key 执行 Knowledgebase `addDoc` 或任意 collection 管理操作。

### 1.3 现状与约束

| 现状 | 设计约束 |
| --- | --- |
| `EnvUtil.getAccessKey()` / `getSecretKey()` 缺失即抛错 | 新增可空读取能力，保留现有 getter 行为，API key-only 构造不能先触发 AK/SK 异常 |
| `KnowledgeBase.Builder` 未暴露 Viking 凭证/资源定位参数 | 增加 Viking 专用可选 builder 字段，不改变现有调用 |
| `VikingKnowledgebaseBackend` 与 `VikingMemoryService` 构造时无条件检查/创建 collection | 只有存在一对 AK/SK 时执行管理预检查；API key-only 跳过 |
| 两个 wrapper 继承 `volc-sdk-java:1.0.250` 的 `BaseServiceImpl` | AK/SK 继续复用该签名链；API Key 不能走 `json()`，因为 `makeRequest()` 会用 SignerV4 覆盖 `Authorization` |
| 当前依赖没有 Java 版 `VikingMem` / `APIKey` | API Key 数据面复用 `BaseServiceImpl#getHttpClient()` 和既有 Apache HttpClient 类型，不新增依赖 |
| 当前 `VikingMemoryService` 未向 wrapper 传递 `Session.id()`，`AddSession` body 也缺少 `session_id` | 新 wrapper 签名显式接收 `sessionId`；从 ADK `Session.id()` 获取，规范化、校验并写入请求体 |
| wrapper 将部分失败转换为 `false` / 空列表 | API Key 路径必须传播有类别的异常；合法空 `result_list` 才返回空列表 |
| `ServiceInfo` 当前未显式声明 scheme | API Key 仅向固定 Volcengine host 通过 HTTPS 发送，禁止明文 HTTP |

### 1.4 DESIGN-REV-001 返修闭环

| Review 关闭条件 | 本次设计落点 | 状态 |
| --- | --- | --- |
| 明确 `session_id` 来源及空白/非法值规则 | §4.3 固定为 ADK `Session.id()`，定义 trim、正则、长度与前置失败 | 已处理，待独立 Review |
| 加入 wrapper 方法签名和 AddSession body | §5.3 定义四参数签名和包含 `session_id` 的请求体 | 已处理，待独立 Review |
| 定义重放、重试与幂等边界 | §5.1、§6、§8 明确单发、结果未知、相同 ID 覆盖及同 Session 串行 | 已处理，待独立 Review |
| 增加来源透传、请求字段、非法 ID 测试 | §10.1 T-005/T-006 与 TASK-004～TASK-006 | 已处理，待独立 Review |
| 增加 Secret 不泄露断言 | §7.2、§10.1 T-006 与 TASK-006 | 已处理，待独立 Review |

## 2. 目标与非目标

### 2.1 目标

1. Knowledgebase 可用 API Key 搜索已有 collection，并保留现有 `KnowledgebaseEntry` 结果。
2. Memory 可用 API Key 执行 `addSessionToMemory` 与 `searchMemory`，并保留 ADK 的异步接口和结果类型。
3. 配置选择确定且实例隔离：有效显式参数 > 对应环境变量 > 一对有效 AK/SK。
4. 双凭证下数据面只用 API Key、管理面只用 AK/SK；API Key 服务端失败不降级。
5. 所有失败可区分且不泄露 API Key、AK/SK 或 Authorization。
6. Memory AddSession 使用稳定、合法的 ADK `Session.id()`；同一 Session 重放复用同一 `session_id`，不因重试生成新 ID。

### 2.2 非目标

- 不改变本次三条数据面以外的请求、业务模型或排序。
- 不新增自动重试、连接池实现、依赖或后台任务。
- 不补做既有 AK/SK 路径的全量错误模型重构。
- 不引入 Feature Gate；SDK 版本回退即为代码回滚手段。

## 3. 影响面

### 3.1 计划修改/新增文件

| 类型 | 文件 | 作用 |
| --- | --- | --- |
| MODIFY | `core/src/main/java/com/volcengine/veadk/utils/EnvUtil.java` | API Key 规范化、两个环境变量和可空 AK/SK 读取 |
| MODIFY | `core/src/main/java/com/volcengine/veadk/knowledgebase/KnowledgeBase.java` | Viking API Key、project、resource ID 的 builder 显式入口 |
| MODIFY | `core/src/main/java/com/volcengine/veadk/knowledgebase/backends/viking/VikingKnowledgebaseConfig.java` | 冻结解析后的凭证与资源定位配置 |
| MODIFY | `core/src/main/java/com/volcengine/veadk/knowledgebase/backends/viking/VikingKnowledgebaseBackend.java` | 初始化分支、管理权限边界、搜索鉴权选择 |
| MODIFY | `core/src/main/java/com/volcengine/veadk/integration/vikingknowledgebase/VikingKnowledgebaseWrapper.java` | Knowledgebase Bearer 数据面 POST 与响应映射 |
| MODIFY | `core/src/main/java/com/volcengine/veadk/memory/viking/VikingMemoryService.java` | 显式 API Key 构造入口、初始化分支与 wrapper 组装 |
| MODIFY | `core/src/main/java/com/volcengine/veadk/integration/vikingmemory/VikingMemoryWrapper.java` | Memory add/search Bearer 数据面 POST 与响应映射 |
| NEW | `core/src/main/java/com/volcengine/veadk/integration/viking/VikingDataPlaneException.java` | 两个 wrapper 共用的安全、可分类数据面异常 |
| MODIFY | 对应六个 `core/src/test/...` 测试文件 | 配置、入口、初始化、请求、错误与泄密反例 |
| MODIFY | `README.md`、`README_zh.md` | 两项 API Key、优先级、能力与管理边界 |

不修改 `pom.xml`、生成物、数据库或其他 backend。

### 3.2 外部依赖与规范

- 继续使用 `com.volcengine:volc-sdk-java:1.0.250` 已传递的 Apache HttpClient；API Key 分支创建单次 `HttpPost`，复用 wrapper 的 `getHttpClient()`。
- `BaseServiceImpl.makeRequest()` 固定调用 SignerV4，Signer 会设置 `Authorization`；所以 API Key 分支必须绕过 `json()`，但仍复用同一 host、path 元数据与 client。
- 火山引擎官方《添加会话-AddSession》（`https://docs.volcengine.com/docs/84313/1946661`，正文核验状态 `VERIFIED_BODY`）规定 `session_id` 长度 `[1, 128]`、仅英文字母/数字/下划线且以英文字母开头；相同 ID 重复调用会覆盖此前生成的事件版本并从画像中撤回旧事件。
- Google ADK `Session` 的 `id()` 是当前会话线程的稳定标识；Java `VikingMemoryService.addSessionToMemory(Session)` 已持有该对象，无需新增公共参数或生成另一套身份。
- AgentKit 服务功能变更边界扫描结论：命中外部调用、超时、权限、配置与 Secret；不命中 OpenAPI/proto、DB、Rider、Metrics、Feature Gate 或发布编排。
- 安全基线来自《ArkClaw/Agentkit观测数据脱敏手册》（`https://bytedance.larkoffice.com/docx/VYRSdHS4UoAFbdxUnkqceXLtnWg`，revision 204）：Secret 和 Authorization 不进入日志/Trace。该 Java SDK 不适用 Friday 拦截器细节；原文时效性标记为 `needs_live_verification`，不降低本设计的禁止泄密要求。
- 提交遵循《计算 CommitMessage&MR规范》（`https://bytedance.larkoffice.com/wiki/WdrWw6RHAibRHHkdww6c9PJ6nuf`，revision 20）的 Conventional Commit 格式。

## 4. 公共配置与兼容设计

### 4.1 有效值与选择函数

在 `EnvUtil` 增加统一的可选 Secret 规范化函数，规则为：

```text
normalizeSecret(value):
  normalized = value == null ? null : value.trim()
  if normalized 为空，或 equalsIgnoreCase("none") / equalsIgnoreCase("null"):
    return null
  return normalized

resolveApiKey(explicit, envName):
  return normalizeSecret(explicit) != null
      ? normalizeSecret(explicit)
      : normalizeSecret(System.getenv(envName))
```

新增只读 API：

- `EnvUtil.getVikingApiKey()`：返回规范化的 `DATABASE_VIKING_API_KEY` 或 `null`。
- `EnvUtil.getVikingMemoryApiKey()`：返回规范化的 `DATABASE_VIKINGMEM_API_KEY` 或 `null`。
- `EnvUtil.getAccessKeyOrNull()` / `getSecretKeyOrNull()`：返回规范化的环境值或 `null`。
- `EnvUtil.resolveApiKey(String explicitApiKey, String environmentApiKey)`：统一实现显式值优先与无效显式值回退；不得记录参数。

现有 `getAccessKey()`、`getSecretKey()` 和异常文本不变，避免影响非 Viking 调用。只有当 `apiKey == null` 且 AK/SK 不是同时非空时，Viking 构造边界抛出 `IllegalStateException`；消息只列所需配置名。

### 4.2 Knowledgebase 公共入口

`KnowledgeBase.Builder` 新增以下方法，全部返回当前 builder：

- `vikingApiKey(String apiKey)`
- `vikingProject(String project)`，空值按 `default` 处理
- `vikingResourceId(String resourceId)`，空值表示不发送

只在 `backend("viking")` 时消费这些字段。`KnowledgeBase.viking(String appName)`、现有 builder 方法及 `backendInstance` 路径不变。Builder 将字段传给 `VikingKnowledgebaseConfig.resolve(...)`：

- API Key：有效显式值 > `DATABASE_VIKING_API_KEY`。
- project：有效显式值 > `DATABASE_VIKING_PROJECT` > `default`。
- resource ID：有效显式值 > `DATABASE_VIKING_RESOURCE_ID` > 不发送。
- AK/SK：分别读取现有环境变量，但只有完整一对才算管理凭证。

`VikingKnowledgebaseConfig` 保留现有四参数构造方法，并新增覆盖 API Key、project、resource ID 的完整构造/解析入口；旧构造默认 `apiKey=null`、`project=default`、`resourceId=null`，保持源码兼容。配置对象只提供 getter，不实现 `toString()`。

### 4.3 Memory 公共入口

保留 `VikingMemoryService(String appName)`，新增：

- `VikingMemoryService(String appName, String apiKey)`
- `VikingMemoryService(String appName, String apiKey, String projectName)`

旧构造委托新构造并传 `null` API Key、`default` project。解析规则：

- API Key：有效显式值 > `DATABASE_VIKINGMEM_API_KEY`。
- project：有效显式值 > `DATABASE_VIKINGMEM_PROJECT` > `default`。
- AK/SK：与 Knowledgebase 一致，只有完整一对才有效。

不增加新的 Memory builder/config 类型，避免仅为三个值引入额外抽象。现有 `appName` 校验、message 筛选、metadata、memory type 与 `topK=5` 不变。

AddSession 的 `session_id` 规则固定如下：

```text
normalizeSessionId(value):
  if value == null:
    throw IllegalArgumentException("session.id is required for Viking Memory AddSession")
  normalized = value.trim()
  if normalized does not match ^[A-Za-z][A-Za-z0-9_]{0,127}$:
    throw IllegalArgumentException("session.id must start with a letter and contain 1-128 letters, digits, or underscores")
  return normalized
```

- 唯一来源是传入 `addSessionToMemory` 的 ADK `Session.id()`；不读取环境变量、不使用 `appName`/`userId` 拼接，也不在空值或非法值时生成 UUID，避免同一 Session 重放改变业务身份。
- `messages` 过滤结果为空时沿用原行为，不读取/校验 `session.id()`、不发请求并正常完成；存在待写消息时，在任何鉴权或 HTTP 调用前执行上述校验。
- 校验失败不发请求；错误消息只描述字段和约束，不回显原始 ID、messages、metadata 或任何凭证。
- trim 后的合法值作为唯一规范值传给 wrapper 并写入 body；同一 `Session` 的重复调用得到相同规范值。

### 4.4 鉴权状态机

| 本地状态 | 初始化 | 三条数据面 | 管理面 |
| --- | --- | --- | --- |
| API Key 有效，AK/SK 完整 | 执行既有 collection 预检查 | API Key | AK/SK |
| API Key 有效，AK/SK 不完整/缺失 | 跳过预检查 | API Key | 调用前报管理凭证缺失 |
| API Key 无效/缺失，AK/SK 完整 | 执行既有预检查 | AK/SK | AK/SK |
| API Key 无效/缺失，AK/SK 不完整/缺失 | 构造边界报配置缺失 | 不发请求 | 不发请求 |

选中 API Key 后，HTTP 失败、业务失败或解析失败均直接传播；同一次请求不得换环境 API Key 或 AK/SK 重试。

## 5. 请求与领域流程

### 5.1 公共 API Key HTTP 规则

两个 wrapper 各自保留一个包内可测试的 `executeApiKeyJson(operation, body)` 方法，避免改变 AK/SK `json()`。该方法：

1. 从各自 `API_INFO_LIST` 读取固定 path，并只允许 `SearchKnowledge`、`AddSession`、`SearchMemory` 三个白名单 operation。
2. 使用固定 `https` 与当前固定 Volcengine host 组装 URI；不接受用户 URL，API Key 不会发往其他域。
3. 创建每请求独立的 `HttpPost`，设置 `Accept: application/json`、`Content-Type: application/json`、`Authorization: Bearer <apiKey>`。
4. 设置 connect/socket timeout 均为现有 `5000ms`，复用 `getHttpClient()`；不修改共享 header、不引入可变全局状态。
5. 读取并消费 response entity。HTTP 非 2xx、空响应、非法 JSON 或业务 `code` 非 `0` 时抛 `VikingDataPlaneException`；只有成功 JSON 进入原有结果映射。
6. 不自动重试。`AddSession` 的 `session_id` 是覆盖语义的业务身份键，不把它误称为服务端 no-op 幂等键；网络超时可能产生结果未知，SDK 禁止隐式重放，查询也保持同一策略。

`VikingDataPlaneException extends IOException`，公开只读字段：

- `operation`；
- `failureKind`：`AUTHENTICATION`（401）、`PERMISSION`（403）、`RESOURCE`（404）、`SERVICE`（其他 HTTP/业务错误）、`NETWORK`、`RESPONSE_PARSE`；
- 可空 `statusCode`、`requestId`、`serviceCode`。

异常消息只组合上述非敏感元数据，不拼接请求 header、请求 body、响应全文或 cause 中可能出现的 URI/header。测试用假 Secret 对正常、服务端失败、网络失败与解析失败做反向包含断言。

### 5.2 Knowledgebase 搜索

API Key 分支继续调用 `/api/knowledge/collection/search_knowledge`，请求体为：

```json
{
  "name": "<collectionName>",
  "project": "<project>",
  "resource_id": "<optionalResourceId>",
  "query": "<query>",
  "limit": 5,
  "query_param": {},
  "post_processing": {
    "rerank_switch": true,
    "chunk_diffusion_count": 3
  }
}
```

`resource_id` 为空时不发送，`query_param` 仅在调用方 metadata 非空时发送；其余字段与当前映射一致。成功响应仍从 `data.result_list` 映射 `content` 和 `doc_info.doc_meta`。`result_list` 缺失、null 或空数组表示合法空结果；非数组表示 `RESPONSE_PARSE`，不能伪装为空。

`VikingKnowledgebaseBackend.search` 的空 query 短路不变。`addDoc` 永远走 AK/SK；API key-only 时在发请求前抛只列 `VOLCENGINE_ACCESS_KEY` / `VOLCENGINE_SECRET_KEY` 的管理凭证错误。双凭证时构造仍执行既有 collection 检查/创建，且这些管理调用只走 AK/SK。

### 5.3 Memory 添加与检索

API Key 分支复用当前路径：

- `AddSession`：`POST /api/memory/session/add`；wrapper 主方法签名改为 `addSession(String collectionName, String sessionId, List<Message> messages, Metadata metadata)`，body 必须包含规范化后的 `session_id`，并保留 `collection_name`、`messages`、`metadata`、`project_name`。
- `SearchMemory`：`POST /api/memory/search`；body 保留 `collection_name`、`query`、`filter.user_id`、`filter.memory_type`、`limit`，并补充 `project_name`。

`VikingMemoryService.addSessionToMemory` 在得到非空消息列表后读取 `session.id()`，把该值作为 `sessionId` 传给 wrapper；wrapper 在鉴权分支选择前执行 §4.3 的 trim/格式校验，并将规范值用于 API Key 与 AK/SK 两条 AddSession body，避免两种鉴权产生不同会话身份。请求体形态为：

```json
{
  "collection_name": "<session.appName()>",
  "project_name": "<projectName>",
  "session_id": "<normalized session.id()>",
  "messages": [],
  "metadata": {}
}
```

两者均以 `Authorization: Bearer <apiKey>` 鉴权，这与 Python `vikingdb-python-sdk==0.1.32` 的 `APIKey.sign_request()` 一致。add 成功仍返回 `true`，search 成功仍映射 `data.result_list[*].memory_info.summary` 到 `MemoryEntry`；合法空列表返回空 `SearchMemoryResponse`。

`VikingMemoryService.addSessionToMemory` 的无有效用户消息分支保持“不发请求并完成”。存在消息但 `session.id()` 为空白、超过 128 字符、不是字母开头或包含字母/数字/下划线以外字符时，wrapper 在执行 HTTP 前抛出 `IllegalArgumentException`；service 不包装该运行时异常，保持其类型经 `Completable` 向订阅方传播，且 HttpClient 不被调用。其他 API Key 异常由 wrapper 抛出，经 `Completable` / `Single` 向订阅方传播，不记录异常对象，避免第三方异常文本携带 Secret。

### 5.4 流程图

```text
构造实例
  ├─ resolve API Key（显式 > 对应环境变量）
  ├─ resolve AK/SK pair
  ├─ 两者均无效 → 配置缺失异常
  ├─ AK/SK 完整 → 执行既有管理预检查
  └─ API key-only → 跳过管理预检查

数据面调用
  ├─ API Key 有效 → HTTPS Bearer POST
  │                    ├─ 成功且业务 code=0 → 原有结果映射
  │                    └─ HTTP/业务/网络/解析失败 → 分类异常，不降级
  └─ API Key 无效 → 既有 AK/SK json()/SignerV4 路径

Memory AddSession（有有效消息）
  ├─ 读取 Session.id()
  ├─ trim + ^[A-Za-z][A-Za-z0-9_]{0,127}$ 校验
  │    └─ 无效 → IllegalArgumentException，不发请求
  ├─ 将同一规范值传给 wrapper 并写入 session_id
  └─ 发送一次；失败不自动重试
```

## 6. 数据、状态与并发

- 不新增数据库、Schema、DDL/DML、迁移、回填、缓存或持久化状态。
- API Key、project、resource ID、AK/SK 在对象构造时解析并保存为不可变字段；后续环境变量变化不改变已构造实例，保证同一实例选择稳定。
- wrapper 共享现有 HttpClient，但每次调用新建 `HttpPost`、body 和 header；不把 Authorization 写入静态 `API_INFO_LIST` 或共享 `ServiceInfo`，并发请求不会串 Key。
- 无事务或跨资源原子性。`AddSession` 是唯一写副作用；保持单次发送、无自动重试。服务端对相同 `session_id` 的语义是覆盖此前事件版本并撤回旧画像事件，不是追加，也不保证 no-op。
- 同一个 ADK `Session` 的正常调用、失败后的显式重放或调用方重试必须复用同一个规范化 `session_id`；不得在 wrapper、重试器或异常恢复路径生成新 ID。调用方只有要创建独立会话/消息批次时才应创建新的、满足约束的 ADK Session ID。
- 网络超时或连接中断后的写入结果可能未知；SDK 直接传播 `NETWORK`，不自行判断成功与否。调用方若根据业务确认重试，应使用原 `Session` 和相同消息；该操作触发服务端覆盖语义，不能假设不会重新抽取记忆。
- 同一 `session_id` 的并发写入没有本地锁或顺序保证，最终结果取决于服务端完成顺序；调用方必须串行化同一 Session 的写入。不同合法 `session_id` 可并发，且请求 body/header 均为每调用独立对象。
- API key-only 跳过预检查不等于 collection 可选；资源不存在由首个数据面请求以 `RESOURCE` 或服务端错误暴露。

## 7. 兼容性、权限与安全

### 7.1 兼容性

- 保留所有面向用户的 `MemoryService`/Knowledgebase public 构造、static factory、builder 方法、返回类型和 AK/SK 环境变量；`addSessionToMemory(Session)` 签名不变。
- 新增重载和 builder 方法，不改已有方法签名；`backendInstance` 不受新增 Viking 字段影响。
- `VikingMemoryWrapper` 是 integration 层实现接缝；其 AddSession 主签名增加 `sessionId` 参数，并同步修改仓内唯一调用方与测试。该签名变化不暴露到 `MemoryService` 接口；不保留会生成不稳定 ID 的三参数旁路。
- AK/SK-only 时仍调用现有 `json()` 与 SignerV4，管理预检查、返回映射维持原行为。
- 两个 Viking API Key 绝不交叉兜底，也不读取 `MODEL_AGENT_API_KEY`。
- 只支持现有固定 Volcengine endpoint；不新增 BytePlus 行为。

### 7.2 权限与安全

- API Key 仅允许三条白名单数据面 path；`addDoc`、Get/CreateCollection 不能进入 Bearer helper。
- `https` 是 API Key 发送前提；host/path 由代码常量提供，防止凭证转发。
- 不记录 API Key、AK/SK、Authorization、完整请求对象、完整配置对象或含敏感 header 的异常。
- `session_id` 不是认证 Secret，但校验异常不回显非法原值；API Key/AK/SK/Authorization 仍不得出现在该异常、cause、日志或测试失败输出。
- 允许记录 operation、`authMode=api_key|ak_sk`、collection/project、HTTP status、服务端 request ID 和非敏感 service code。查询文本、messages、metadata 沿用当前行为但本次不新增日志；API Key 分支不打印请求/响应正文。
- 示例只写 `<YOUR_VIKING_API_KEY>` 一类显然占位值。

## 8. 可靠性、性能与可观测性

- 超时：API Key connect/socket 均为 5 秒，与现有 wrapper 配置一致。
- 重试：SDK 不重试；`AddSession` 显式重放复用原 `session_id` 并遵循服务端覆盖语义，网络不确定性留给调用方决策；任何失败都不得换身份或生成新 ID 后重试。
- 性能：每次请求仅增加一次鉴权分支和 header 设置；复用现有连接池。API key-only 少一次或两次管理调用，未引入集合、线程或缓存增长。
- 可观测性：不新增业务 Metrics 或 Trace；失败异常保留 operation、failure kind、status、request ID，满足 SDK 调用方诊断。日志仅记录模式和非敏感定位字段。
- 正常空结果与错误严格分离；无结果不会抛异常，依赖失败不会返回空列表或 `false`。

## 9. 发布、灰度与回滚

- 无 DB、配置中心、服务部署或 Feature Gate；能力随 SDK 新版本交付。未配置新 API Key 的用户仍走 AK/SK，形成天然兼容路径。
- 发布说明与中英文 README 同步列出配置优先级、三条数据面范围、已有 collection 前提、管理操作 AK/SK 要求，以及 Memory AddSession 对 ADK Session ID 的格式与重复覆盖语义。
- 回滚方式为回退 SDK 版本/对应 commit，恢复 AK/SK-only；没有数据清理、配置回滚或补偿动作。API Key 环境变量在旧版本中不会被消费。
- 上线前以 mock 单测验证全部协议分支；真实 Viking 凭证与网络集成由后续集成/E2E 节点按环境执行，本设计不要求把凭证写入仓库。

## 10. 验证方案

### 10.1 单元测试映射

| Test ID | 测试文件 | 断言 |
| --- | --- | --- |
| T-001 | `EnvUtilTest` | 两个 API Key 环境变量分别读取；trim；空白/大小写 `none`/`null` 为 absent；显式有效值优先，无效显式值回退环境 |
| T-002 | `KnowledgeBaseTest` | 旧 builder 仍可构造；新增 builder 参数准确传入 Viking config；非 Viking/backendInstance 不受影响 |
| T-003 | `VikingKnowledgebaseBackendTest` | API key-only 跳过预检查；双凭证执行预检查；AK/SK-only 兼容；无凭证报错；API Key search、空 query、addDoc 管理边界 |
| T-004 | `VikingKnowledgebaseWrapperTest` | Bearer header、HTTPS host/path、project/resource ID/filter/后处理 body、5 秒超时、成功/空结果；401/403/404/5xx/业务 code/网络/非法 JSON 分类；失败不走 AK/SK 且假 Key 不在异常 |
| T-005 | `VikingMemoryServiceTest` | 三个构造入口、优先级、API key-only/双凭证/AK-SK-only 初始化；有消息时 `Session.id()` 原值传给 wrapper；无消息不读取/校验 ID 且不发请求；空白/非法 ID 由 wrapper 前置拒绝并经 Rx 传播；add/search 异常经 Rx 传播 |
| T-006 | `VikingMemoryWrapperTest` | 新四参数 add 签名；trim 后的合法 `session_id` 与 collection/project/messages/metadata 一起进入 body；null、空白、超长、非字母开头、非法字符均在 HTTP 前失败；相同 ID 的两次显式调用捕获到相同 `session_id` 且每次只发送一次；add/search Bearer header、HTTPS path、成功/空结果、HTTP/业务/网络/解析失败分类、无降级；假 API Key/AK/SK/Authorization 和非法 ID 原值均不出现在异常/cause/日志 |
| T-007 | `README.md` / `README_zh.md` 人工 diff | 变量、显式入口、优先级、已有 collection 与管理边界中英文一致；只有占位凭证 |

所有 HTTP 单测通过 `setHttpClient(mockHttpClient)` 注入现有 client，捕获 `HttpPost` 后检查 URI、header、RequestConfig 与 JSON body；测试输出不得打印 header。

### 10.2 开发阶段命令

开发节点应记录每条命令的退出码与关键输出：

```bash
./mvnw spotless:check
./mvnw -pl core -am -DskipTests compile
./mvnw -pl core -am -Dtest=EnvUtilTest,KnowledgeBaseTest,VikingKnowledgebaseBackendTest,VikingKnowledgebaseWrapperTest,VikingMemoryServiceTest,VikingMemoryWrapperTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl core -am test
```

JaCoCo 报告路径为 `core/target/site/jacoco/jacoco.xml` / `index.html`。增量覆盖率以本次修改/新增 Java 文件的 changed executable lines 为分母、其中 JaCoCo covered lines 为分子，逐行与 `git diff <before>..<after>` 交叉核对；必须大于等于 90%，并列出未覆盖行/方法。README 不纳入代码覆盖率分母。

## 11. 风险与取舍

| 风险 | 处理 |
| --- | --- |
| SignerV4 覆盖 Bearer | API Key 分支不调用 `json()` / `makeRequest()`，用现有 HttpClient 直接 POST |
| API key-only 被管理预检查阻塞 | 仅完整 AK/SK pair 才执行预检查 |
| 双凭证请求身份不确定 | 构造时冻结选择；数据面 API Key，管理面 AK/SK |
| 服务失败被误判为空结果 | API Key 非成功统一抛分类异常；仅成功空数组为空结果 |
| Key 经日志/异常泄露 | 不打印 header/body/config；异常仅含安全元数据；单测做 secret 反向断言 |
| Session ID 缺失或不符合服务端格式 | 统一从 `Session.id()` 获取，trim 后按官方 `[A-Za-z][A-Za-z0-9_]{0,127}` 前置校验；不生成替代 ID，不发非法请求 |
| 写请求超时后重复或覆盖 | 不增加自动重试；显式重放复用原 ID，文档和测试明确相同 ID 是覆盖语义且同 Session 写入需串行 |
| 直连实现与未来 Java SDK 重复 | 当前依赖无等价能力且不升级依赖；未来 SDK 具备同等契约时可内部替换，不影响公共入口 |

无阻塞待确认项。

## 12. Requirement → Design → Task → Test 追踪

| Requirement | Design | Task | Test |
| --- | --- | --- | --- |
| REQ-001 Knowledgebase API Key 搜索 | §4.2、§5.1、§5.2 | TASK-002、TASK-003 | T-002、T-003、T-004 |
| REQ-002 Memory API Key 添加/检索 | §4.3、§5.1、§5.3、§6 | TASK-004、TASK-005、TASK-006 | T-005、T-006 |
| REQ-003 配置优先级与隔离 | §4.1～§4.3 | TASK-001、TASK-002、TASK-004 | T-001、T-002、T-005 |
| REQ-004 AK/SK 兼容与双凭证 | §4.4、§7.1 | TASK-002、TASK-004、TASK-006 | T-002、T-003、T-005 |
| REQ-005 可诊断且不泄密 | §5.1、§7.2、§8 | TASK-003、TASK-005、TASK-006 | T-004、T-005、T-006 |
| REQ-006 文档可发现 | §9、§10 | TASK-007 | T-007 |
| DESIGN-REV-001 AddSession `session_id` 契约 | §1.3、§4.3、§5.3、§6、§8、§10.1 | TASK-004、TASK-005、TASK-006、TASK-007 | T-005、T-006、T-007 |
