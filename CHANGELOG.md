# 变更日志（Changelog）

本仓库遵循[四段式版本号](docs/preferences/pref-version-scheme.md)：前两段对齐 Spring Boot 版本线，第三位为本项目破坏性变更位（递增时第四位归 0），第四位为常规迭代位。

## [未发布]

## [2.7.3.0] — 2026-10-14

2.7 线首个版本：自 3.5.2.2 全功能降级至 Spring Boot 2.7.18（**2.7.3.0 ≙ 3.5.2.2 功能全集**），与 3.5 线并行维护。

### 破坏性变更（Breaking）

- **API 命名空间回归 `javax.*`**：全部模块源码从 `jakarta.servlet/persistence/validation/annotation/jws` 回归 `javax.*`（与 Spring Boot 2.7 生态一致；依赖 Maven 坐标保持 jakarta 的 EE8 过渡形态，见文末架构备忘）。自 3.5 线移植业务代码时，需将 `jakarta.servlet` 等 import 同步改回 `javax.*`
- **编译级别降至 Java 8**：`maven.compiler.release=8`，产物字节码 Java 8，运行环境 JDK 8+；构建工具链 JDK 17 交叉编译（构建者无需安装 JDK 8）。源码同步回退 Java 9+ API（`List.of` 家族 / `String.repeat` / `Files.writeString` / `InputStream.readAllBytes` / `Stream.toList` / instanceof 模式匹配等）
- **依赖版本线回退**：

  | 组件 | 3.5 线 | 2.7 线（本版） |
  |---|---|---|
  | Spring Boot | 3.5.16 | 2.7.18 |
  | Spring Framework | 6.x | 5.3.x |
  | Spring Cloud | 2025.0.3 | 2021.0.9 |
  | Camunda（workflow） | 7.23.0 | 7.19.0（javax 线末代；兜底 7.18.0） |
  | CXF（sample jaxws） | 4.1.2 | 3.5.11 |
  | logback | 1.5.x | 1.2.13（Boot 2.7.18 自管） |
  | Tomcat embed | 10.1.x | 9.0.122（root 显式钉版，覆盖 Boot 自管 9.0.83） |
  | Redisson | 3.50.0（spring-data-35） | 3.50.0（spring-data-27：starter 排除传递的 -35，显式引入 -27） |
  | RocketMQ spring / client | 2.3.0 / 5.2.0 | 不变——2.3.0 官方基线即 SB 2.7.18 + client 5.2.0，原生配套 |
  | H2 | 2.2.220 | 2.2.220 保持（与 Camunda 7.19 的兼容性验证安排见"已知行为差异"） |

- **okhttp 模块 `httpStatusSensitive` 配置属性删除**：非 2xx 响应处理语义统一由 `errorMode` 管理——原 `sensitive=true` 使用方改配 `errorMode=throwout`（行为等价，且异常类型不再被包装降级）。该变更将同步 3.5.x 线。

### 废弃（Deprecation）

- **版本属性名拼写修正：`spring.could.version` → `spring.cloud.version`（parent 构建属性面）**：jevokit-parent 中 Spring Cloud 版本属性更正历史拼写笔误，`spring.cloud.version` 为标准属性名；旧名 `spring.could.version` 保留一版作为废弃别名（值链式引用标准名，恒同值），下个大版本（4.0 线）移除。兼容性：经 Maven parent 继承 jevokit-parent、以 `<spring.could.version>` 属性覆盖 Spring Cloud 版本的项目，过渡期覆盖行为不变（版本管理的 spring-cloud-dependencies import 过渡期仍引用旧名）；直接 import `jevokit-dependencies` BOM 的使用方不受影响（BOM import 按 pom 自身解析值生效，不读取使用方属性）。过渡期新配置一律使用 `spring.cloud.version`。解析结果不变：Spring Cloud 仍为 2021.0.9。

### 迁移指引

**自旧版内部定制版本（2.7.2.x 线）迁移的 Boot 2.7 用户**

- Maven 坐标整体替换为 `io.github.jevonsnotes:jevokit-*-spring-boot-starter:2.7.3.0`——旧版工具模块对应 `jevokit-core`、配置模块对应 `jevokit-autoconfigure`、各 starter 按功能名一一对应（历史坐标字样从略，需完整对照可联系维护者）；
- 配置前缀核对：配置键以 `jevokit.*` 为准——旧配置前缀统一将**首段整体替换为 `jevokit`**（其余段不变；更早期历史前缀同理整体替换）。双侧 `CONFIG_PREFIX` 全量对照 9 模块零反例，按功能域对照如下：

  | 功能域 | jevokit 2.7 线前缀 |
  |---|---|
  | 安全防护·XSS 过滤（security-xss） | `jevokit.security.xss.*` |
  | 安全防护·SQL 注入（security-sql-inject） | `jevokit.security.sql-inject.*` |
  | 安全防护·路径穿越（security-path-traversal） | `jevokit.security.path-traversal.*` |
  | 安全防护·代码注入（security-code-inject） | `jevokit.security.code-inject.*` |
  | 操作日志审计（audit-log） | `jevokit.audit.log.*` |
  | 数据脱敏（desensitization） | `jevokit.desensitization.*` |
  | nogen | `jevokit.nogen.*` |
  | api | `jevokit.api.*` |
  | OkHttp 集成（okhttp） | `jevokit.ok.http.*`（注意为 `ok.http` 三段形态，勿误写为 `jevokit.okhttp.*`） |
- **jasypt 加密密文兼容（jasypt 2.1.0 → 3.0.5）**：默认算法由 `PBEWithMD5AndDES` 分叉为 `PBEWITHHMACSHA512ANDAES_256` + 随机 IV（RandomIV）——默认配置下存量密文跨版本互解直接失败。隔离探针实证：两版属性源解析机制在 Boot 2.7.18 下一致（`ENC()` 内联、`@Value`、配置绑定、`spring.config.import`、bootstrap 均无问题），仅算法默认值分叉。存量 2.x 默认生成的 `ENC()` 密文升级后二选一：① 显式配置 `algorithm=PBEWithMD5AndDES` + `iv-generator-classname=org.jasypt.iv.NoIvGenerator`（密码/盐/迭代次数默认值两版一致，存量密文可平滑续用）；② 迁移期以新默认算法重新加密。
- **XSS reject 模式收紧（jsoup 1.15.3 → 1.23.2）**：jsoup 1.17.2 起 Cleaner 新增校验，含未闭合"必需结束标签"的输入（如 `<b>bold`、`<span>abc`）由放行改为拒绝——reject 模式下历史上含未闭合标签的入参会开始抛 `XssException`（只收紧不弱化）；clear/escape 模式 39 场景字节级一致，无感。reject 模式使用方请评估存量入参形态。
- `javax.*` 命名空间与 Boot 2.7 生态天然一致，业务代码通常零改动。

**自 jevokit-spring-boot 3.5.x 切线至 2.7 线的用户**

- 业务代码中直接 import 的 `jakarta.servlet` / `jakarta.annotation` / `jakarta.validation` 等（随 Boot 3 引入）需改回 `javax.*`；
- 配置键迁移：`spring.data.redis.*` → `spring.redis.*`（Boot 3 专属键空间回迁）；
- Redisson：Boot 3 的 `spring.data.redis.ssl.bundle`（SSL bundle 引用）配置形态不受支持，Boot 2.7 的 `spring.redis.ssl`（布尔开关）仍有效；
- logback 由 1.5 降回 1.2：`logback-spring.xml` 按 1.2 语法核对（jevokit 日志脱敏 filter 兼容两代，已验证）；
- 使用 Spring Cloud 的项目需同步降至 2021.0.x 线。

### 已知行为差异（相对 3.5 线）

- redis：不支持 `spring.data.redis.*` 配置空间与 ssl-bundle 形态（`spring.redis.ssl` 布尔仍有效）；
- logback 1.5 → 1.2：自定义 appender/filter 生态按 1.2 API 核对（jevokit 脱敏 filter 与 sample 的 logback-spring.xml 均为 1.2 兼容写法，已预核）；
- workflow：Camunda 7.19 官方支持矩阵仅含 H2 1.4.x，root 保持 H2 2.2.220——H2 2.2.220 下 Camunda schema 自动创建 / 流程部署 / 流程冒烟经实测兼容（R11 已关闭，降级预案归档备用）。

### sample 启动与默认测试集说明（下游用户可见）

sample 主 `application.yml` 默认经 `spring.config.import` 导入随制品入库的 `application-ignore-mq.yml`，默认排除 RocketMQ 官方自动装配（否则 `@RocketMQMessageListener` 监听容器启动即连 name-server，连接失败会中止上下文刷新）——无 RocketMQ 环境（9876 nameserver 不在位）时示例应用可直接启动，默认测试集（`ApplicationTest` + 8 个非 MQ 的 E2E 类）`mvn test` 即全绿，上下文仅需本机 Redis（6379）+ H2 内存自给。演示 RocketMQ 收发（`/rocketmq/test`、7 个 MQ listener）或跑全量测试（`mvn test -DexcludedGroups=` 放开 `@Tag("mq")` 的 `RocketMqE2ETest`）前，移除/注释主 `application.yml` 中对 `application-ignore-mq.yml` 的 import 行恢复装配（连接参数见同文件 `rocketmq` 段）。另经 `optional:file:./application-local.yml` 保留工作目录调试覆盖导入（不入库，文件缺省不影响启动）。

### 修复

- **jevokit-desensitization 日志脱敏链路启动挂起（验收期缺陷 DEF-1）**：logback 脱敏过滤器在日志事件中调用容器 `getBean`（"日志路径容器依赖"反模式，见文末架构备忘），Boot 2.7 线上下文刷新窗口内与 redisson 等异步 bean 创建形成三方循环等待，可致启动/JVM 挂起。修复为静态引用两阶段发布（`@PostConstruct` 发布配置类引用 + `ContextRefreshedEvent` 经容器发布代理实例保 AOP 语义），日志链路零容器依赖。**下游用户行为无变化**：引用未发布时 fail-open 直通，保持"脱敏失败绝不阻断日志输出"既有语义。
- **jevokit-security-xss javascript 协议混淆绕过堵住（安全修复）**：`HtmlSafeList` 对 `src`/`href` 的 javascript 协议检查原按解码值字面前缀匹配，`java&#9;script:`、`jav&#x0A;ascript:`、`java&#115;cript:` 等实体/控制符混淆形态可绕过判定——jsoup 解析属性时按 HTML 规范完成一次实体解码，值中残留 \t、\n 等控制符，而浏览器 URL 解析会剥除这些字符后按 `javascript:` scheme 执行，构成 XSS 注入入口。修复为规范化后判定：`Locale.ROOT` 小写 + 剥除值内 \t\r\n + trim 首尾控制符（与浏览器 URL 解析器规范化行为对齐），混淆载荷在 clear 净化 / escape 转义 / reject 判定三模式一并堵住。只收紧不弱化：相对路径、锚点、含 `&` 与 `&#` 字面量的 query、http/https URL 判定不受影响（不误伤边界有固化用例）。
- **兼容性 API 恢复（jevokit-core）**：`OkHttpStatusRuntimeException.ofThrow(Integer, String, String)` 三参重载恢复（响应体置空串，兼容历史调用形态）；`RequestContentCachingRequestWrapper.getContentAsString()` 恢复（按请求声明字符集解码缓存体，未声明/非法字符集兜底 UTF-8，语义与表单体解析一致）——两个既有公开入口回归，使用方升级零改动。
- **jevokit-audit-log 审计降级契约修复**：`DefaultAuditLogProcessor` 类注释承诺"DB 模式下无 `AuditLogDao`（无 DB 环境）时静默跳过"，实际代码中 hutool `getBean` 缺 bean 即抛 `NoSuchBeanDefinitionException`——原 `if(x != null)` 判空为死分支，无 DB 环境的每个审计请求都会抛异常。改 `getBeanProvider().getIfAvailable()` 判空降级后，"无 DB 环境静默跳过"的既有契约真正生效。

### 内部改进（对使用方零影响）

- **运行期 `getBean` 同类加固与静态状态生命周期管理**：全仓运行期 `SpringUtil.getBean` 逐一定性处置——3 处消除（`AuditLogFilter` 事件匹配改装配期注入字段直读、`BzNoHelper` 单例自引用 `_this()` 消除、`JevokitRedisConfiguration` 缺省键生成器构造注入替代）、4 处保持惰性方法并附定性注释（`AuditLogDao`/`BaseAutoDao`/`NoGenDao`/`TokenStrategy`，调用均在容器刷新完成后，不构成等待环）、2 处按两阶段发布定式改造（`OkHttpUtil` 静态门面 of() 家族：`ContextRefreshedEvent` 发布/`ContextClosedEvent` 清理，公共 API 不变）。

### 架构备忘（维护者查证）

- **R6 版本下限边界**：本线自动配置注册采用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（17 个模块）。该机制自 Boot 2.7 引入——若未来再降至 2.6 及以下将失去读取支持，需整体回退 `META-INF/spring.factories` 注册方式。
- **D2 依赖坐标的 EE8 形态（坐标 jakarta、包名 javax）**：2.7 线依赖坐标沿用 jakarta 命名空间、实际包名为 javax 的过渡版本（Jakarta EE 8 形态，2.7 时代生态普遍如此）：`jakarta.servlet-api`（javax.servlet）、`jakarta.persistence-api` 2.2.3（javax.persistence）、`jakarta.annotation-api` 1.3.5（javax.annotation）、`jakarta.jws-api` 2.1.0（javax.jws）、`jakarta.xml.bind-api` 2.3.3（javax.xml.bind）、`jakarta.validation-api` 2.0.2（javax.validation）。坐标空间保留 jakarta 为未来向 3.x 线迁移预留平滑路径；注意个别坐标版本号高于包名直觉（如 jws-api 2.1.0 实为 javax.jws 包），以 jar 实测为准。
- **"日志/回调线程路径触碰容器"反模式（验收期 DEF-1 根因，kit 规范候选）**：logback filter 等日志链路在事件处理中调用 `SpringUtil.getBean`——上下文刷新期 main 线程持单例锁创建 bean，任意异步线程此刻打日志即阻塞在锁上；若 main 所等 future 的完成方恰为被阻塞线程，成三方循环等待、JVM 永久挂起。定式修复=静态引用两阶段发布（配置类引用先行、刷新完成后经容器发布实例）+ 日志链路未发布 fail-open + 非日志链路保持容器查找原语义。完整机理、已知边界（static 状态无 ContextClosedEvent 清理联动）与同类加固点清单见本变更 design §4 第 11 条（`openspec/changes/downgrade-spring-boot-2-7-18/design.md`）。
- **redisson starter 双注册装配形态（redisson-spring-boot-starter 3.50.0，2.7 线特有）**：V1 `RedissonAutoConfiguration`（`META-INF/spring.factories` 注册）与 V2 `RedissonAutoConfigurationV2`（`AutoConfiguration.imports` 注册，extends V1）在 Boot 2.7.18 下双注册共存，`@ConditionalOnMissingBean` 条件背离后 V2 单 bean 生效（eng-2 实证：fat jar 与测试上下文 bean 源均确认 V2；因 V2 继承 V1，运行期栈帧类名必显 V1，勿以栈帧行类名判定装配来源）——排查 redisson 装载问题以 V2 为实际入口。

## [3.5.2.1] — 2026-10-08

### 构建（对使用方零影响）

- **BOM 并入主 reactor，发布流程单 bundle 化**：jevokit-parent 直接继承 spring-boot-starter-parent，jevokit-dependencies 降级为兼容薄壳 module（坐标与版本管理语义不变，import 姿势不变）；发布由"BOM 先行 + 主包后至"两步半简化为一次 deploy 一次 Publish
- 插件基线随 Boot parent 统一（compiler 3.14.1 等），删除手动钉版

## [3.5.2.0] — 2026-09-24

### 破坏性变更（Breaking）

- **jevokit.crypto 启动校验默认开启**：新增 jevokit.crypto.startup-check（默认 true），四密钥任一缺失时启动即失败（异常一次性列出全部缺失键）；不配置密钥也能启动的旧行为需显式配 startup-check=false 退回
- 密钥读取入口统一：SecurityUtil.getSm4Key() 等统一入口（requireKey 语义），DesUtil 不再直读配置

### 修复

- **jevokit-core 容器中立**：两个 request wrapper 去除对 Tomcat 内部类（B2CConverter / coyote.Constants）的依赖，pom 删 tomcat-embed-core 换显式 jakarta.servlet-api（provided）——Jetty/Undertow 项目不再有 NoClassDefFoundError 风险

## [3.5.1.0] — 2026-09-21

### 破坏性变更（Breaking）

- **配置前缀 `spkit.*` 全量更名为 `jevokit.*`**：所有 starter 的配置键前缀统一为家族令牌（如 `spkit.xss.enabled` → `jevokit.xss.enabled`），四层命名（坐标/前缀/包名/类名）全部归一 jevokit 系。升级自 3.5.0.0 的项目需要把配置文件中的 `spkit:` 前缀整体替换为 `jevokit:`。

### 版本语义

- 自本版起版本号语义固化为四段式：前两段对齐 Spring Boot 版本线，第三位为破坏性变更位（本版 3.5.0.0 → 3.5.1.0 即第三位 +1），第四位为常规迭代位。

## [3.5.0.0] — 2026-09-21

首个发布到 Maven Central 的版本（Spring Boot 3.5.x / JDK 17）。

### 新增

- **公共基座**：`jevokit-core`（通用工具 + SM2/SM4/RSA/AES 加解密）、`jevokit-autoconfigure`（自动装配基座 + `jevokit.*` 配置基础设施）
- **安全加固（8 个 starter）**：
  - XSS 防护（`jevokit-security-xss`）：按 URL 拦截请求/响应，clear 清洗 / escape 转义 / reject 拒绝三模式
  - SQL 注入防护（`jevokit-security-sql-inject`）：污染正则表清洗/拒绝，支持自定义规则
  - 路径遍历防护（`jevokit-security-path-traversal`）
  - 代码注入检测（`jevokit-security-code-inject`）：规则命名空间 + `CodeInjectUtil.isSafe(...)`
  - 已知漏洞加固（`jevokit-security-web-fixed`）：禁用 `HttpInvokerServiceExporter`（CVE-2016-1000027）
  - 一键聚合（`jevokit-security-all`）
  - JSecure 接口安全（`jevokit-api`）：加签验签、防重放、SM2/SM4 国密混合加解密、统一请求响应体（`@JSecureApi`）
  - 配置加密（`jevokit-jasypt`）：`ENC(...)` 密文自动解密
- **合规（2 个 starter）**：
  - 数据脱敏（`jevokit-desensitization`）：注解（`@DstProperty`/`@Desensitization`）与属性规则匹配，覆盖传输对象与 logback 日志脱敏（标准 filter 形式，挂任意 appender）
  - 审计日志（`jevokit-audit-log`）：事件规则/注解驱动，支持 DB（自动建表）与日志文件持久化
- **数据（3 个 starter）**：
  - Redis 增强（`jevokit-redis`）：`@Lock` 分布式锁、随机过期缓存（防雪崩/击穿）、token 策略
  - 分布式单号生成（`jevokit-nogen`）：redis + db 号段模式，支持 mysql / pg，实测约 40000 tps
  - Excel 导入（`jevokit-excel`）：`@Excel` 参数绑定，自动解析文件 / JSON 上传
- **集成（3 个 starter）**：RocketMQ 收发封装（`jevokit-rocketmq`）、OkHttp 正向代理客户端（`jevokit-okhttp`，多代理自动切换）、Camunda 7 工作流（`jevokit-workflow`）
- **BOM**：`jevokit-dependencies` 统一全家版本管理（继承 spring-boot-starter-parent 3.5.16）

### 修复（发布前质量基线）

- 依赖组件安全升级 12 项：FastJSON 2.0.53、XStream 1.4.21、Commons Text 1.12.0、PostgreSQL JDBC 42.6.0、Hutool 5.8.46、jsoup 1.23.2、Netty 4.1.119.Final、POI 5.4.1、Woodstox 6.5.1、BouncyCastle 1.86、Redisson 3.50.0，SnakeYAML 交由 Spring Boot 管理（2.4）
- `JSecureContext` 三个 ThreadLocal 请求结束不清理——线程池复用会串终端配置/交易号（拦截器 afterCompletion 兜底清理）
- `AuditLogFilter` 异常路径 ThreadLocal 清理缺失（`chain.doFilter` try/finally 兜底）
- surefire 测试报告中文乱码（JVM file.encoding=GBK 与报告 UTF-8 声明错配）
- jsoup 1.23.x 兼容：`Safelist#isSafeAttribute` 覆写可见性随父类提升为 public
