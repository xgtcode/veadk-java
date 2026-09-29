# Viking 数据面 API Key 后端任务拆分

## 1. 执行约束

- 设计来源：同目录 `backend-design.md`；需求来源：同目录 `prd-spec.md`。
- 只修改 `core` 中的 Viking 配置、Knowledgebase/Memory 入口与 wrapper、对应测试，以及中英文 README。
- 不修改 `pom.xml`，不新增依赖，不扩展 BytePlus，不重构其他 backend，不修改数据库/OpenAPI/部署。
- 新增或修改的每一行代码都需能映射到 REQ-001～REQ-006 或必要验证。
- API Key、AK/SK、Authorization 不得进入日志、异常、测试失败输出、快照或文档实值。
- 开发完成后执行真实格式化检查、编译、定向测试、全量 `core` 测试和 JaCoCo 增量覆盖率统计；增量覆盖率必须达到 90% 以上。

## 2. 任务列表

### TASK-001：实现统一的可选凭证解析

- 对应设计：`backend-design.md` §4.1。
- 对应需求：REQ-003、REQ-004、REQ-005。
- 修改：
  - `core/src/main/java/com/volcengine/veadk/utils/EnvUtil.java`
  - `core/src/test/java/com/volcengine/veadk/utils/EnvUtilTest.java`
- 实现：
  1. 增加 Knowledgebase/Memory API Key 环境变量常量与可空 getter。
  2. 增加统一规范化/解析函数：trim 后为空或大小写不敏感的 `none` / `null` 视为 absent；有效显式值覆盖环境值。
  3. 增加不抛错的 AK/SK getter，保留现有必填 getter 和调用行为。
  4. 函数及异常不得拼接 Secret 值。
- 验收：T-001 全部通过，两种 API Key 互不复用，现有 EnvUtil 测试不回归。

### TASK-002：扩展 Knowledgebase 配置入口与初始化状态机

- 对应设计：§4.2、§4.4、§5.2、§7.1。
- 对应需求：REQ-001、REQ-003、REQ-004。
- 修改：
  - `core/src/main/java/com/volcengine/veadk/knowledgebase/KnowledgeBase.java`
  - `core/src/main/java/com/volcengine/veadk/knowledgebase/backends/viking/VikingKnowledgebaseConfig.java`
  - `core/src/main/java/com/volcengine/veadk/knowledgebase/backends/viking/VikingKnowledgebaseBackend.java`
  - `core/src/test/java/com/volcengine/veadk/knowledgebase/KnowledgeBaseTest.java`
  - `core/src/test/java/com/volcengine/veadk/knowledgebase/backends/viking/VikingKnowledgebaseBackendTest.java`
- 实现：
  1. Builder 增加 `vikingApiKey`、`vikingProject`、`vikingResourceId`，仅 Viking backend 消费。
  2. Config 保留旧构造，新增解析后的 apiKey/project/resourceId 和完整 AK/SK pair 判定。
  3. 两类凭证均无效时在构造/鉴权边界报安全配置错误。
  4. API key-only 跳过 collection 检查/创建；AK/SK 完整时保持预检查。
  5. `addDoc` 不得使用 API Key；API key-only 调用时在发请求前报管理凭证缺失。
- 验收：T-002、T-003；旧 `KnowledgeBase.viking(appName)` 与旧 builder 在 AK/SK 环境下保持可用。

### TASK-003：实现 Knowledgebase Bearer 搜索与安全异常

- 对应设计：§5.1、§5.2、§7.2、§8。
- 对应需求：REQ-001、REQ-005。
- 修改/新增：
  - `core/src/main/java/com/volcengine/veadk/integration/vikingknowledgebase/VikingKnowledgebaseWrapper.java`
  - `core/src/main/java/com/volcengine/veadk/integration/viking/VikingDataPlaneException.java`
  - `core/src/test/java/com/volcengine/veadk/integration/vikingknowledgebase/VikingKnowledgebaseWrapperTest.java`
- 实现：
  1. 保留两参数 wrapper 构造，新增完整配置构造。
  2. API Key search 使用现有 HttpClient 直连固定 HTTPS host 与固定 search path，设置 Bearer header 和 5 秒 connect/socket timeout。
  3. body 包含 name/project/query/limit、可选 resource_id、现有 filter 与 post_processing。
  4. 成功结果复用现有 `KnowledgebaseEntry` 映射；合法空结果返回空列表。
  5. 非 2xx、业务错误、网络、非法/错误结构 JSON 抛分类 `VikingDataPlaneException`，不回退 AK/SK、不包含 Key/body/header。
  6. AK/SK search 继续使用 `json()`。
- 验收：T-004 覆盖 request capture、所有失败分类、空结果与泄密反例。

### TASK-004：扩展 Viking Memory 公共构造与初始化状态机

- 对应设计：§4.3、§4.4、§5.3、§7.1。
- 对应需求：REQ-002、REQ-003、REQ-004。
- 修改：
  - `core/src/main/java/com/volcengine/veadk/memory/viking/VikingMemoryService.java`
  - `core/src/test/java/com/volcengine/veadk/memory/viking/VikingMemoryServiceTest.java`
- 实现：
  1. 保留单参数构造；新增 `(appName, apiKey)` 和 `(appName, apiKey, projectName)`。
  2. 应用显式 API Key > Memory 环境变量 > AK/SK 的选择，并将冻结配置传给 wrapper。
  3. API key-only 跳过 collection 预检查；双凭证与 AK/SK-only 保持管理预检查。
  4. 保持 appName 校验、message 过滤、metadata、memory type、topK 与 Rx 返回语义。
  5. 不捕获后吞掉 API Key 数据面异常，不记录异常对象。
- 验收：T-005 覆盖三种构造、四种鉴权状态、无消息分支和异常传播。

### TASK-005：实现 Viking Memory Bearer 添加/检索

- 对应设计：§5.1、§5.3、§6～§8。
- 对应需求：REQ-002、REQ-005。
- 修改：
  - `core/src/main/java/com/volcengine/veadk/integration/vikingmemory/VikingMemoryWrapper.java`
  - `core/src/test/java/com/volcengine/veadk/integration/vikingmemory/VikingMemoryWrapperTest.java`
  - 若 TASK-003 已新增异常类，则复用 `VikingDataPlaneException.java`，不再新增第二套异常。
- 实现：
  1. 保留两参数 wrapper 构造，新增 apiKey/projectName 完整构造。
  2. API Key `AddSession` / `SearchMemory` 复用现有 HttpClient，发送到固定 HTTPS path，Bearer header，5 秒 connect/socket timeout。
  3. body 保留现有字段并增加 `project_name`；不改变 messages、metadata、filter、memory type 或 limit。
  4. 成功仍返回 boolean / `MemoryEntry` 列表；合法空结果为空列表。
  5. 服务/网络/解析失败抛分类异常，不返回 `false`/空列表、不重试、不降级、不泄密。
  6. AK/SK add/search 继续使用现有签名链。
- 验收：T-006 覆盖两条 path、body/header/timeout、成功/空结果、所有失败类别、无重试/降级和泄密反例。

### TASK-006：完成跨路径兼容与安全回归

- 对应设计：§4.4、§6～§8。
- 对应需求：REQ-003、REQ-004、REQ-005。
- 修改：仅允许补充 TASK-001～TASK-005 已列测试与必要实现，不新开范围。
- 实现/验证矩阵：
  1. API Key-only、API Key+AK/SK、AK/SK-only、无凭证。
  2. Knowledgebase/Memory Key 隔离；无效显式值回退对应环境；已选择 Key 服务失败不降级。
  3. 并发调用不修改静态 header；每请求 Authorization 与 body 独立。
  4. 假 Secret 不出现在异常 message/cause、捕获日志或测试快照。
  5. addDoc 与 collection 管理永不走 Bearer。
- 验收：相关定向测试和 `core` 全量测试通过；无无关源文件修改。

### TASK-007：更新中英文用户文档

- 对应设计：§9、§10.1。
- 对应需求：REQ-006。
- 修改：`README.md`、`README_zh.md`。
- 内容：
  1. `DATABASE_VIKING_API_KEY` 只用于已有 Knowledgebase collection 搜索。
  2. `DATABASE_VIKINGMEM_API_KEY` 只用于已有 Memory collection 添加与检索。
  3. 展示两个显式 Java 入口；说明有效显式参数优先、无效显式值回退环境变量。
  4. 说明 API key-only 跳过管理预检查、管理和 Knowledgebase `addDoc` 仍需 AK/SK。
  5. 使用 `<YOUR_VIKING_API_KEY>` 占位值，不出现可用 Secret。
- 验收：T-007，中英文配置名、范围和示例一致。

### TASK-008：执行验证与增量覆盖率门禁

- 对应设计：§10.2。
- 对应需求：REQ-001～REQ-006。
- 执行并保存退出码/关键日志：

```bash
./mvnw spotless:check
./mvnw -pl core -am -DskipTests compile
./mvnw -pl core -am -Dtest=EnvUtilTest,KnowledgeBaseTest,VikingKnowledgebaseBackendTest,VikingKnowledgebaseWrapperTest,VikingMemoryServiceTest,VikingMemoryWrapperTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl core -am test
```

- 从 `core/target/site/jacoco/jacoco.xml` 按 `git diff <before>..<after>` 的 changed executable lines 统计增量分子/分母、百分比和未覆盖行/方法；不足 90% 必须补测并重跑。
- 检查 `git diff --check`、`git status --porcelain` 和完整 diff；只显式 stage 本任务文件。
- 验收：所有命令真实成功，增量覆盖率 `>= 90%`，提交后 worktree 干净，push 后远端 ref 等于 after commit。

## 3. 建议执行顺序与依赖

```text
TASK-001
  ├─ TASK-002 ─ TASK-003 ─┐
  └─ TASK-004 ─ TASK-005 ─┼─ TASK-006 ─ TASK-008
                          └─ TASK-007 ──────────┘
```

- TASK-003 与 TASK-005 共用异常契约，先由 TASK-003 落类，TASK-005 复用。
- TASK-002/003 与 TASK-004/005 可按单 writer 顺序实施，禁止并行修改同一公共异常类。
- TASK-006 不扩大实现范围，只补闭环测试。

## 4. 交付检查清单

- [ ] 仅三条数据面请求支持 API Key；管理操作和 `addDoc` 未扩大权限。
- [ ] 显式 > 对应环境变量 > AK/SK 的状态机和 `none`/`null` 规则均有测试。
- [ ] API key-only 跳过预检查；双凭证管理走 AK/SK、数据面走 API Key。
- [ ] API Key 只发往固定 HTTPS Volcengine Viking host。
- [ ] 服务失败不降级，空结果与失败可区分。
- [ ] 无 Secret 出现在日志、异常、测试输出或文档。
- [ ] 不新增依赖、数据库、OpenAPI、Feature Gate、重试或 BytePlus 行为。
- [ ] 格式化检查、编译、定向单测、全量 core 测试均通过。
- [ ] JaCoCo 增量覆盖率证据可复核且达到 90% 以上。
- [ ] 变更文件逐项归属，显式 stage、commit、push 并核对远端 SHA。
