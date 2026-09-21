# 变更日志（Changelog）

本仓库遵循[四段式版本号](docs/preferences/pref-version-scheme.md)：前两段对齐 Spring Boot 版本线，第三位为本项目破坏性变更位（递增时第四位归 0），第四位为常规迭代位。

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
