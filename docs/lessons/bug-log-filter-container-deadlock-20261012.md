# 日志路径上的 Spring 容器查找——logback 过滤器 getBean 与上下文刷新的三方循环等待（DEF-1）

- 根因类别：性能·并发（非主线程 × 刷新窗口 × 单例锁的交叉死锁；反模式名：日志路径容器依赖）
- 状态：已修复·已沉淀（DEF-1 eng-2 修复 + TE-1 终版全量回归通过：库 519 + sample 17 全绿、fat jar 真启动 0 ERROR）
- 相关模块：starter/compliance/jevokit-desensitization/src/main/java/io/github/jevonsnotes/jevokit/desensitization/helper/DesHelper.java、同模块 util/DesUtil.java（getProperties）、support/logback/filter/DesensitizedLogFilter.java（触发入口）、support/logback/IDesensitizedLogback.java；触发载体 sample/jevokit-sample/src/main/resources/logback-spring.xml + redisson 连接回调（ConnectionsHolder 打点）；防线测试 starter/compliance/jevokit-desensitization/src/test/java/io/github/jevonsnotes/jevokit/desensitization/helper/DesHelperLogPathNoContainerTest.java

## 现象 / 复现

2.7.18 降级线 phase-8 验收：全量 `mvn test` 的 sample 段上下文启动卡死 20+ 分钟。jstack 实证（两份独立采样）：main 线程 TIMED_WAITING 停在 `Redisson.create → MasterSlaveConnectionManager.doConnect → CompletableFuture.get()`，"Signaller 对象轮换、周期性重试同一失败 future"；redisson-netty-5-1..32 中多条线程 **BLOCKED 在 `DefaultListableBeanFactory.getSingleton` 的对象监视器上**，栈顶穿 `DesensitizedLogFilter → DesHelper.getInstance → SpringUtil.getBean`，栈底是 `ConnectionsHolder.connectedSuccessful → initConnections` 的 INFO 日志打点。CPU 零增长——不是忙等，是死等。

一分钟级复现：`mvn -B test -pl sample/jevokit-sample -Dtest=ApplicationTest`（任意新启上下文的测试类即可，jstack 抓 fork 即同签名）。

**三态矛盾是本次最重要的定位线索**：① 基线（3.5 线）同位置 53ms 内 ERROR；② 2.7 测试上下文无限挂起；③ 同项目主应用 fat jar 冒烟实连成功（Redisson 1+24 连接）。三个"表象"曾把排查引向 localhost/IPv6 形态、redisson V1/V2 自动配置分叉——全部证伪（见根因）。

## 根因

**三方循环等待**：logback 过滤器（挂在任意 appender 上、**任意线程触发**）在处理日志事件时调 `SpringUtil.getBean`——

1. main 线程持 Spring 单例锁创建 redisson bean，进入 `Redisson.create` 后停在连接 future 上；
2. Redisson 连接成功回调在 redisson-netty 线程打 INFO 日志（ConnectionsHolder:132）；
3. 日志事件穿 DesensitizedLogFilter → DesHelper.desensitizeLogAfterClone 对象分支 → `SpringUtil.getBean(DesHelper.class)` → 阻塞在 main 持有的单例锁；
4. main 所等 future 恰需这些被阻塞的 netty 线程完成 → **循环等待，永久挂起**。"Signaller 轮换/周期重试"是 redisson timer 对未完成连接的超时重试副作用，非独立病因。

**为什么三态表象各异（竞态的正常形态）**：死锁需要"连接完成回调的日志恰好落在 main 持锁窗口内"。主应用 fat jar 冒烟时序错开未踩中；测试 fork 因**多上下文 + static ready 标志跨上下文残留 true**（第一个上下文的 DesHelper init 后 ready 永不回位）扩大了窗口而踩中；基线 53ms"秒败"是 Spring TestContext 失败缓存瞬时重抛 + 首发上下文在 RocketMQ 处快速失败——三态差异全部收敛到同一根因，无一需要独立解释。

**两个排查误区的教训**：
- "栈顶显示 `RedissonAutoConfiguration.redisson(RedissonAutoConfiguration.java:329)`" 曾被当作走了 V1 旧装配的证据——**V2 extends V1，redisson() 方法体在 V1 类里，任何实例的栈帧类名都必然显示 V1**。继承体系下栈帧类名 ≠ bean 定义源；bean 源要看装配日志（两处均确认 `RedissonAutoConfigurationV2`）。另一相关事实：redisson-spring-boot-starter 3.50.0 同时含 spring.factories（V1）与 AutoConfiguration.imports（V2），Boot 2.7 两种机制都生效、靠 @ConditionalOnMissingBean 背离出单 bean——本缺陷与该重复注册无关，但属 2.7 线特有装配形态。
- localhost/IPv6（本机 localhost 优先解析 ::1、Redis 仅监听 127.0.0.1）一度是头号嫌疑——修复后测试上下文 Redisson 同样以 `localhost/127.0.0.1:6379` 成功初始化，证明网络形态无涉。**环境差异假设要在"同机同配置双路径对照"全部收敛后才能升格为根因。**

## 同类隐患排查

全仓 `SpringUtil.getBean` 清点（非 desensitization 模块），统一评级**低危**——均为"运行期请求/回调线程调用"，Tomcat/容器启动在 refresh 完成之后，不构成"main 等待其完成"的等待环；升级条件：未来任何 starter 在 bean 创建中启动异步回调并等待其完成。已与 TE-1 独立复扫交叉确认，无新增盲区：

| 位置 | 调用形态 | 评级 |
|---|---|---|
| jevokit-audit-log `filter/AuditLogFilter.java:90` | 请求线程运行期 getBean(AuditLogProperties) | 低危 |
| jevokit-audit-log `dao/AuditLogDao.java:39,44` | 请求线程直查 JdbcTemplate/properties | 低危 |
| jevokit-audit-log `dao/BaseAutoDao.java:39` | 同上 | 低危 |
| jevokit-audit-log `processor/DefaultAuditLogProcessor.java:46,69` | 处理器运行期 | 低危 |
| jevokit-nogen `helper/BzNoHelper.java:402`、`dao/NoGenDao.java:26` | 请求线程 | 低危 |
| jevokit-redis `config/JevokitRedisConfiguration.java:264`（keyGenerator）、`token/strategy/TokenStrategy.java:17` | 缓存/请求线程 | 低危 |
| jevokit-okhttp `util/OkHttpUtil.java:833/843/855-857/878-879` | okhttp 线程运行期 | 低危 |
| sample `mq/listener/v4/NormalMqListener.java:48`（MQ 回调，container refresh 后才 start）、`rpc/WSConfig.java:29`（同线程重入） | TE-1 复扫确认安全 | 低危 |

配条款（架构复核提出，随同类加固排期）：DesHelper 的 static 就绪状态无 ContextClosedEvent 清理联动——多上下文（spring-test）场景下 ready/staticInstance 跨上下文残留（本缺陷的窗口放大器之一），当前由 fail-open 兜底不产生挂起；规范沉淀时补"static 状态与 context 生命周期绑定"（ContextClosedEvent 清理或以 context 为键）。

## 修复要点

**反模式**：logback filter/appender 等"任意线程、任意时机触发"的日志路径上，出现任何 `SpringUtil.getBean`/容器查找。

**修复定式（可沉淀为 kit 规范，已落 design §4 第 11 条）：静态引用两阶段发布 + 链路分治 + fail-open**——

1. **两阶段发布**：`@PostConstruct`（init）只发布配置/无状态协作者引用——此刻单例锁仍被持有、`this` 是裸实例，发布实例会重新引入容器依赖；实例发布延后到 `ContextRefreshedEvent`（finishRefresh 阶段、锁已释放）且**经容器 getBean 取**——保 AOP 代理形态，`@Desensitization` 切面语义与原 getBean 一致（经 @PostConstruct 的 `this` 发布会静默丢失切面）。
2. **链路分治**：DesUtil.getProperties 按 `PROCESS_TYPE` 分治——日志链路（LOG 档）只读静态引用、对容器零依赖（ready 门闩保证静态必已发布）；非日志链路保持容器查找原语义、失败降级 null（调用方既有判空承接）。
3. **volatile**：静态标志/实例由容器线程写、netty 等非容器线程读，可见性必须显式。
4. **fail-open**：实例未发布（刷新早期/上下文失败）时跳过脱敏原样输出——与该过滤器既有"脱敏失败绝不阻断日志输出"语义自洽；未就绪时经 Assert 快速失败、由过滤器 catch，**绝不静默阻塞**。
5. **D 侧注意**：排障时 logback 过滤器栈出现在业务无关线程（netty/okhttp/MQ 回调）上即本反模式指纹；修复优先动"过滤器取 Bean 的方式"，不动触发源（Redisson 打日志是正常库行为，任何 chatty bean 都可能成为下一个触发器）。

## 回归用例

1. `DesHelperLogPathNoContainerTest`（desensitization 模块，已固化入回归库）：以"无任何可用 Spring 容器"（SpringUtil 静态上下文置 null）为前置锁定四条契约——未就绪 fail-fast（IllegalArgumentException "desHelper not ready"）、JSON 报文参数脱敏零容器可用、对象参数 fail-open 原样返回、实例发布后对象参数走静态实例且同样零容器。**无容器前置是关键**：带容器的测试永远测不出容器依赖（DEF-1 的死锁需要"容器在但锁被占"，恰好介于单测与集成的空隙）。
2. 集成面：sample 全量默认套件 17 用例（ApplicationTest/8 个 E2E）+ RedisE2ETest 单类（Redisson 1+24 连接先行成功）；AC-6 fat jar 真启动（重 package 后 18.7s、0 ERROR、缓存三形态 + 并发锁端点闭环）。

## 防范措施

1. **编码约定**：logback filter/appender/任何"任意线程任意时机"的回调路径上，禁止 SpringUtil.getBean；需要容器能力时按"两阶段发布"定式落静态引用（配置在 @PostConstruct、实例在 ContextRefreshedEvent 经 getBean 取代理），未发布时 fail-open。
2. **评审速查**：见到过滤器/appender/listener 类里出现 `SpringUtil`/`ApplicationContext.getBean` 即质询"这条路径最坏在哪个线程、什么时机被触发？那一刻单例锁可能被谁持有？持锁者是否在等这条路径完成？"——三问有一中招即本模式。
3. **static 就绪标志三件套**：跨线程读的 static 标志/引用必须 volatile；考虑 ContextClosedEvent 清理或以 context 为键（防多上下文残留）；发布点不得在持锁窗口内做容器查找。
4. **测试卡点**：竞态类修复的回归用例必须构造出"竞争的前置形态"（本条 = 无容器 + 手动发布），带正常容器的测试对此类缺陷永远假绿；jstack 里业务无关线程停在 `getSingleton` 即本缺陷指纹，直接按本条处置。
5. **关联条目**：bug-shared-mutable-static-state-20260911（static 可变状态两轴——本条的 ready 残留是其"跨上下文轴"的 Spring 场景版）；bug-rocketmq5-connect-null-misread-20260910（环境缺位误读——本条排查中 RocketMQ 形态变化同样制造过烟雾）。
