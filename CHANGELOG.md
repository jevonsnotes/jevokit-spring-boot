# 变更日志（Changelog）

本仓库遵循[四段式版本号](docs/preferences/pref-version-scheme.md)：前两段对齐 Spring Boot 版本线，第三位为本项目破坏性变更位（递增时第四位归 0），第四位为常规迭代位。

## [未发布]

### 修复

- **jevokit-desensitization `desHelper` 容器循环依赖解环**：`desHelper` 的 bean 方法原本**构造注入** `SensitizationPowerInterceptor`——业务自定义拦截器（常注入带 Feign 的业务 service，而 Feign 侧配置又用 desHelper）与该边闭合即成容器环，应用启动失败（`BeanCurrentlyInCreationException`）。修复为 `ObjectProvider` 延迟解析：desHelper 构造期只依赖自身配置（环的 kit 侧边消失，业务链无论怎么绕都不成环），拦截器延至 `ContextRefreshedEvent`（单例锁已释放、容器完全就绪）懒解析发布到静态引用——DEF-1 死锁防御语义不变（`@PostConstruct` 阶段不碰业务 bean）；未覆盖时 provider 落 `@ConditionalOnMissingBean` 缺省实现，行为与直注入一致。窗口语义变化：init 到刷新完成之间全部静态入口按未就绪语义处理（desensitizeLog/View fail-fast、日志对象链路 fail-open、checkPower 安全默认 false）——容器刷新完成前不接流量，实际无影响。配套把 `ready` 门闩从 `@PostConstruct` 挪至事件回调末尾最后落（此前窗口期 ready=true 但实例未发布，就绪断言名不副实：过了断言却拿 null 实例靠判空 fail-open 兜底），现在 ready=true 严格等价于"全部静态引用已发布、静态入口可用"。另补直传拦截器的便捷构造（测试/无容器直构场景，发布时序仍走事件）。回归：`DesHelperCycleBreakTest` 复刻"业务拦截器→业务 service→desHelper"闭环形态，解环后上下文正常启动且业务拦截器经事件发布生效

## [3.5.3.0] — 2026-11-04

### 新增

- **JSecure 上传文件无感加密（场景六）**：`UploadChannel.add(..., encrypt=true)` 传入明文，客户端内部以终端 SM4 密钥 SM4-CBC 加密（随机 IV 前缀、字节级二进制安全）后作为文件 part；`digest` 按加密前明文计算、`size` 为密文字节数、条目新增 `encrypted` 标志（签名信封内不可伪造）。服务端校验链对加密条目：size 比对（密文）→ 解密 → 按明文复核 digest → 明文填充条目 `plainContent`（transient，不进信封）——Controller 单 meta 参数即可直接拿明文（无感解密），该形态下文件 part 名无需固定（服务端识别一切非 meta 的文件 part）；`upload-digest-verify=false` 仅豁免摘要复核，解密与明文注入恒执行；加密条目仅支持 push
- **JSecure 文件上传（`jevokit-api`）**：`@JSecureApi` 端点支持 multipart 双参数形态（`meta` 加密信封 + `files` 文件 part），业务体 `JSecureUploadDto<B, F>`（业务数据 + 文件清单）由协议链无感校验——元数据层（fileId 字符集白名单/format 白名单/size 上限/条目数对齐）、内容层（按 filename=fileId 对齐 part 后摘要比对）、pull 模式（URL 域名白名单 SSRF 防线 + 拉取后摘要复核）、refer 模式（业务钩子存在性校验）。客户端统一门面 `JSecureClient`：`post()` JSON 通道与 `upload()` 上传通道（`.add()/.pull()/.refer()/.entry()` 混合组包，默认 hutool 载体，可注入自定义载体）。摘要算法支持 sha256/sm3/md5 三种（`digestAlg` 字段分派，md5 仅遗留互操作场景建议显式放开），终端可配 `upload-digest-algorithms` 收紧允许范围（fail-closed，范围外拒绝并提示允许清单；范围配置与全集求交，笔误名收窄不扩权；refer 条目不受范围牵连）；`upload-digest-verify` / `upload-size-verify` 两开关可分别关闭摘要/大小**一致性**校验（`upload-max-size` 单文件上限与 pull 下载内存封底恒生效，不被开关豁免）；`upload-max-file-count` 单请求条目数上限（缺省 20，防聚合放大，0/负数不限制）。启动期顾问 `JSecureUploadStartupAdvisor` 对弱化决策（校验开关关闭）与配平失当（容器 `spring.servlet.multipart.max-file-size` > `upload-max-size`、算法范围含笔误名）打 WARN 留痕。删除已废弃的 `JSRequestExecutor`/`JSecureUploadBuilder`（均未发布过）。协议自实现参考：`JSecureProtocolDemo`（单文件、bcprov+fastjson2，可直接交付对接方）

### 破坏性变更（Breaking）

- **上传 DTO 字段与条目类更名**：`JSecureUploadDto` 的 `files` 字段更名为 `fileDetails`（信封明文键同步 `"files"` → `"fileDetails"`），条目类 `JSecureUploadFileDto` 更名为 `JSecureUploadFileDetailDto`——与 multipart 文件 part（part 名固定 `files`，承载文件字节）从命名上区分：前者是信封明文里的文件明细清单（元数据），后者是 wire 上的文件字节 part，二者按 fileId 对齐。均为 3.5.3.0 开发中命名（未随任何版本发布过），使用方无感
- **`jevokit.api.client[].fetch-url-whitelist` 更名为 `upload-fetch-url-whitelist`**：原名为 3.5.3.0 开发中引入（未随任何版本发布过），归入 upload 配置族（与 `upload-max-size` / `upload-format-whitelist` / `upload-digest-algorithms` 等同前缀），语义更清晰；使用方无感（无已发布版本携带旧名）
- **SM2 签名字符集配置化（`jevokit.crypto.charset`，默认 UTF-8）**：`SecurityUtil` 的 SM2 签名/验签原文取字节与 AES/SM4 密钥字符串转字节不再跟随 JVM 默认字符集（平台相关，GBK 平台 + 中文报文必验签失败），统一由新增配置 `jevokit.crypto.charset` 决定（`CryptoProperties` 新增 `charset` 字段并注入 `SecurityUtil.init`；非法字符集名应用启动即失败，该 fail-fast 不受 `startup-check` 开关管辖）。行为变更说明：JVM 默认字符集即 UTF-8 的环境（JDK 18+、主流 Linux 部署）行为不变；JVM 默认字符集非 UTF-8 的环境（如 Windows JDK 17 + zh_CN 即 GBK），SM2 签名字节口径由"跟随 JVM 默认"变为"默认 UTF-8"。迁移指引：中文报文且签名两端同为非 UTF-8 默认字符集的存量对接（原"碰巧两端都是 GBK"能互通的场景），升级后需两端显式配置相同字符集（如 `jevokit.crypto.charset=GBK`）；验签互通始终要求两端配置一致，任一端不同、含非 ASCII 报文即验签失败（纯 ASCII 报文两种字符集字节相同，不受影响）。SM4/AES 密文内的明文编码由底层固定 UTF-8，不受本配置影响；RSA 密钥为 Base64 解析、不涉字符集转换，亦不受影响

## [3.5.2.2] — 2026-10-14

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
