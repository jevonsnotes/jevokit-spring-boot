# 变更日志（Changelog）

本仓库遵循[四段式版本号](docs/preferences/pref-version-scheme.md)：前两段对齐 Spring Boot 版本线，第三位为本项目破坏性变更位（递增时第四位归 0），第四位为常规迭代位。

## [未发布]

### 破坏性变更（Breaking）

- **jevokit-okhttp 删除 `jevokit.ok.http.http-status-sensitive` 配置项**：非 2xx 响应处理语义统一由 `errorMode` 管理——原 `http-status-sensitive: true` 使用方改配 `error-mode: throwout`（行为等价，非 2xx 抛 OkHttpStatusRuntimeException 且携带 code/url/body）；quiet/log 模式吞错语义不变（文本路径返回错误体、字节/流路径返回 null）

### 废弃（Deprecation）

- **版本属性名拼写修正：`spring.could.version` → `spring.cloud.version`（parent 构建属性面）**：jevokit-parent 中 Spring Cloud 版本属性更正历史拼写笔误，`spring.cloud.version` 为标准属性名；旧名 `spring.could.version` 保留一版作为废弃别名（值链式引用标准名，恒同值），下个大版本（4.0 线）移除。兼容性：经 Maven parent 继承 jevokit-parent、以 `<spring.could.version>` 属性覆盖 Spring Cloud 版本的项目，过渡期覆盖行为不变（版本管理的 spring-cloud-dependencies import 过渡期仍引用旧名）；直接 import `jevokit-dependencies` BOM 的使用方不受影响（BOM import 按 pom 自身解析值生效，不读取使用方属性）。过渡期新配置一律使用 `spring.cloud.version`。解析结果不变：Spring Cloud 仍为 2025.0.3。

### 修复

- **XSS 安全修复**：`HtmlSafeList` 的 javascript 协议检查改为规范化后判定（小写 + 剥除值内 \t\r\n + trim 首尾空白），堵住 `java&#9;script:` 等实体/控制符/首部空格混淆形态绕过 `src`/`href` 拦截的漏洞；相对路径、锚点、`data:;base64` 图片特例等既有放行行为不变
- **jevokit-desensitization 日志脱敏链路容器依赖死锁修复（安全相关）**：logback 脱敏过滤器在任意线程的日志事件上调用容器 `getBean` 取 DesHelper 与配置（"日志路径容器依赖"反模式），上下文刷新期间 main 线程持单例锁创建 bean（如 redisson 连接回调打日志即触发）时，getBean 阻塞在单例锁上，若被阻塞者恰是 main 所等 future 的完成方即形成三方循环等待，可致启动/JVM 永久挂起。修复为静态引用两阶段发布（`@PostConstruct` 发布配置引用 + `ContextRefreshedEvent` 经容器发布实例代理保 AOP 语义），日志链路对容器零依赖；实例未发布/上下文关闭时按既有语义 fail-open/fail-fast，"脱敏失败绝不阻断日志输出"不变。`DesUtil` 配置查找按链路分治：日志链路只读静态引用，其余链路保持容器查找（失败降级 null，掩码算法回落 BASIC）。另补 `ContextClosedEvent` 清理静态引用（spring-test 多上下文/热重启下旧上下文残留引用不再被误用），附带修复 `checkPower` 关闭竞态窗口的潜在 NPE（引用快照判空，安全默认无权限）

### 内部改进（对使用方零影响）

- **运行期 `getBean` 同类加固与静态状态生命周期管理**：全仓运行期 `SpringUtil.getBean` 逐一定性处置——3 处消除（`AuditLogFilter` 事件匹配改装配期注入字段直读、`BzNoHelper` 单例自引用 `_this()` 消除、`JevokitRedisConfiguration` 缺省键生成器构造注入替代）、4 处保持惰性方法并附定性注释（`AuditLogDao`/`BaseAutoDao`/`NoGenDao`/`TokenStrategy`，调用均在容器刷新完成后，不构成等待环）、2 处按 DEF-1 两阶段发布定式改造（`OkHttpUtil` 静态门面 of() 家族 `ContextRefreshedEvent` 发布/`ContextClosedEvent` 清理，公共 API 不变）。顺带修复 `DefaultAuditLogProcessor` 降级契约死分支：类注释承诺无 `AuditLogDao`（无 DB 环境）时静默跳过，实际 hutool `getBean` 缺 bean 即抛异常——改 `getBeanProvider` 判空降级后契约真正生效

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
