# 洋葱型环境阻塞——fail-fast 依赖故障（Redis 连不上）长期掩盖 camunda 配置缺陷（admin id 连字符）

- 根因类别：其他（环境阻塞下的验证路径盲区：fail-fast 掩盖 + 降级冒烟未覆盖被掩盖段）
- 状态：已修复·已沉淀
- 相关模块：spring-boot-kit/server/spring-boot-kit-test-server/src/main/resources/application.yml（camunda.bpm.admin-user.id）、docs/refactor/acceptance.md（降级冒烟记录）

## 现象 / 复现

2026-09-10 BUG-TE1-01（test-engineer-1 移交）：Redis 环境修复到位后，任一 `@SpringBootTest` 加载上下文即抛 `org.camunda.bpm.engine.ProcessEngineException: User has an invalid id: 'demo-admin' is not a valid resource identifier`（Camunda 7.23.0），上下文崩溃。阻断面：MockTest 10 + ApplicationTest 2 + OkhttpTest 2 共 14 个测试全阻，demo 应用本身也无法全量启动。

- 复现：Redis 在位时 `mvn -pl server/spring-boot-kit-test-server test -Dtest=MockTest`，约 15-25 秒稳定复现；
- 对照（掩盖态）：Redis 不在位时同一命令在 BeanPostProcessor 阶段死于 RedisConnectionException（redisson → stringRedisTemplate → jCache 顾问链），**永远走不到 camunda 段**——这就是缺陷长期不可见的原因。

## 根因

两层叠加：

1. **配置缺陷本体**：`camunda.bpm.admin-user.id: demo-admin` 含连字符，违反 Camunda 7 资源 ID 白名单。源码实证（camunda-engine-7.23.0-sources.jar）：
   - `org.camunda.bpm.engine.ProcessEngineConfiguration.java:317`——默认 `generalResourceWhitelistPattern = "[a-zA-Z0-9]+|camunda-admin"`（User/Group/Tenant 未单独配置 pattern 时回落此值）；
   - `org.camunda.bpm.engine.impl.cmd.SaveUserCmd.java:51`——`ensureWhitelistedResourceId(commandContext, "User", user.getId())` 对用户 ID 执行该校验。
   即缺陷自该配置写入起就存在，与 2026-09-10 的 spkit 配置改名无关（camunda 段不在改名 18 文件面内，存量判定）。
2. **掩盖机制（洋葱型环境阻塞）**：全量测试在本环境长期被 Redis fail-fast 挡在上下文早期；历史验收降级走的冒烟路径因 lazy-init 从未加载 camunda 段。两层失败现象同形（都是 BeanCreationException + Application run failed），Redis 修好后才剥出第二层——修一层、露一层，故称"洋葱"。

**洋葱量化实证（本条独特证据）**：修复 camunda 前，14 errors 全为 invalid-id；修复后**同签名** 14 errors 根因整体切换为 RocketMQ 层（delayMqListener `RemotingConnectException: connect to null failed`）——同签名不同根因，剥层直接可见。

与盲区族（见 bug-transitive-dep-test-source-miss 的复发计数表）同族不同形：检索盲区是"没搜到"，本条是"**没执行到**"——执行路径盲区，文本 grep 防线对此结构性无感。

## 同类隐患排查

test-engineer-1 执行（2026-09-10）：

- camunda `filter.create: All tasks` 为 filter 名称非资源 ID，无白名单约束；yml 无其他 camunda 用户/组/租户段；
- MqConst 全部 topic/group 及 yml 的 TOPIC_TRACE_SPRING_BOOT_KIT、SPRING_BOOT_KIT_PRODUCER_GROUP 均在 RocketMQ 命名域（允许连字符）内合法；
- jasypt `springBootKitStringEncryptor` 等 bean 标识无同类白名单约束。

结论：无第二个 demo-admin 式隐患。

## 修复要点

- `application.yml` camunda 段 `id: demo-admin` → `id: demoAdmin`（驼峰，匹配 `[a-zA-Z0-9]+`），行内注释白名单规则防回退；
- `password: demo-password` 实证不受限：SaveUserCmd 仅对 `user.getId()` 走资源 ID 白名单，password 仅在 `enablePasswordPolicy`（默认关闭）时走密码策略校验——含连字符合法，保留未动。

## 回归用例

ApplicationTest.contextLoads（上下文启动断言）即常驻防线：上下文全链路启动天然覆盖 camunda 段，camunda 再出配置问题会在该用例显式失败，无需另立测试类。前提注意：该防线仅在 Redis 在位环境生效，环境缺失时它退化为洋葱外层的牺牲品（见防范措施 ①②）。

## 防范措施

1. **环境 fail-fast 时禁止把降级冒烟当"通过"**：冒烟结论必须显式标注未覆盖面（如"camunda 段未验证"），并登记为环境债务待环境恢复后核销；
2. **环境修复后立刻全量重跑**：洋葱每揭一层都可能暴露下层，"Redis 修好了"≠"环境通了"——以全量测试签名（而非冒烟通过）作为环境恢复判据；
3. **资源 ID/标识符类配置值写入前先查目标组件命名白名单**：用户名、组名、租户名、topic/group、bean 名等，不要凭直觉起名（demo-admin 即直觉起名——"看着没问题"的典型）。
