# jevokit-spring-boot

A Spring Boot starter kit covering security hardening, data desensitization, audit logging, distributed locks, idempotent number generation and encrypted configuration.

一套面向 Spring Boot 3.5.x / JDK 17 的后端增强组件库：安全加固（XSS / SQL 注入 / 路径遍历 / 代码注入 / 接口签名防重放）、数据脱敏、审计日志、分布式锁、幂等单号生成、配置加密、Excel 导入，以及 RocketMQ / OkHttp / Camunda 工作流集成。按需引入 starter，统一配置前缀 `jevokit.*`，即插即用。

![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)
![JDK](https://img.shields.io/badge/JDK-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-6DB33F.svg)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.jevonsnotes/jevokit-dependencies.svg)](https://central.sonatype.com/artifact/io.github.jevonsnotes/jevokit-dependencies)

## 仓库结构

仓库根下有两个独立的 Maven reactor（根目录没有聚合 pom，分别构建）：

```text
jevokit-spring-boot/                     # 主 reactor（jevokit-parent）：18 个库模块 + 示例应用
├── core/
│   ├── jevokit-core/                    # 纯工具库（非 starter）：通用工具 + 加解密
│   └── jevokit-autoconfigure/           # 自动装配基座 + jevokit.* 配置约定
├── starter/
│   ├── security/                        # 安全加固（8 个模块）
│   ├── compliance/                      # 合规：脱敏 / 审计
│   ├── data/                            # 数据：redis / 单号生成 / excel
│   └── integration/                     # 集成：rocketmq / okhttp / workflow
└── sample/
    └── jevokit-sample/                  # 示例应用（不发布到仓库）
jevokit-spring-boot-dependencies/        # BOM reactor（jevokit-dependencies）：全家版本管理
docs/                                    # 工程经验沉淀（lessons / preferences）
openspec/                                # 变更管理
LICENSE                                  # Apache-2.0
```

## 模块清单

> 坐标统一使用 groupId `io.github.jevonsnotes`，下表省略；版本统一由 BOM `jevokit-dependencies` 管理。

### 公共基座（core）

| 模块 | 职责 |
|---|---|
| `jevokit-core` | 纯工具库（非 starter）：通用工具类 + 加解密能力 |
| `jevokit-autoconfigure` | 自动装配基座：starter 公共装配逻辑与 `jevokit.*` 配置基础设施 |

### 安全加固（starter/security）

| 模块 | 职责 |
|---|---|
| `jevokit-security-xss-spring-boot-starter` | XSS 防护：按 URL 拦截请求/响应，支持 clear 清洗 / escape 转义 / reject 拒绝 |
| `jevokit-security-sql-inject-spring-boot-starter` | SQL 注入防护：基于污染正则表清洗或拒绝报文，支持自定义规则 opt-in |
| `jevokit-security-path-traversal-spring-boot-starter` | 路径遍历防护：识别并处置 `../` 等路径穿越载荷 |
| `jevokit-security-code-inject-spring-boot-starter` | 代码注入检测：规则命名空间 + `CodeInjectUtil.isSafe(...)` 判定动态内容 |
| `jevokit-security-web-fixed-spring-boot-starter` | 已知漏洞加固：禁用 `HttpInvokerServiceExporter`（CVE-2016-1000027） |
| `jevokit-security-all-spring-boot-starter` | 上述安全防护聚合，一键引入 |
| `jevokit-api-spring-boot-starter` | JSecure 接口安全：加签验签、防重放、SM2/SM4 国密混合加解密、统一请求响应体（`@JSecureApi`） |
| `jevokit-jasypt-spring-boot-starter` | 配置加密：`ENC(...)` 密文自动解密，支持指定盐值/加密器 |

### 合规（starter/compliance）

| 模块 | 职责 |
|---|---|
| `jevokit-desensitization-spring-boot-starter` | 数据脱敏：注解（`@DstProperty` / `@Desensitization`）与属性规则匹配，覆盖传输对象与日志输出 |
| `jevokit-audit-log-spring-boot-starter` | 审计日志：事件规则/注解驱动，支持 DB（自动建表）与日志文件持久化 |

### 数据（starter/data）

| 模块 | 职责 |
|---|---|
| `jevokit-redis-spring-boot-starter` | Redis 增强：`@Lock` 分布式锁、随机过期缓存（防雪崩/击穿）、token 策略 |
| `jevokit-nogen-spring-boot-starter` | 分布式业务单号生成：redis + db 号段模式，支持 mysql / pg，实测约 40000 tps |
| `jevokit-excel-spring-boot-starter` | Excel 导入：`@Excel` 参数绑定，自动解析文件 / JSON 上传 |

### 集成（starter/integration）

| 模块 | 职责 |
|---|---|
| `jevokit-rocketmq-spring-boot-starter` | RocketMQ 收发封装：`RocketMqHelper`、统一消息结构与配置装配 |
| `jevokit-okhttp-spring-boot-starter` | OkHttp 客户端：正向代理场景、多代理自动切换，默认禁用重试 |
| `jevokit-workflow-spring-boot-starter` | Camunda 7 工作流集成 |

> `sample/jevokit-sample` 为示例应用（演示各 starter 用法与可用的演示配置），不发布到仓库。

## 快速开始

环境要求：JDK 17+，Spring Boot 3.5.x。

**1. 引入 BOM**（父 pom 或任意模块的 `dependencyManagement`）：

```xml
<dependencyManagement>
    <dependencies>
        <!-- 3.5.2.0 已发布 Maven Central；当前主干开发版为 3.5.2.1-SNAPSHOT -->
        <dependency>
            <groupId>io.github.jevonsnotes</groupId>
            <artifactId>jevokit-dependencies</artifactId>
            <version>3.5.2.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

> BOM 继承自 `spring-boot-starter-parent` 3.5.16：import 后同时获得 Spring Boot 官方与 jevokit 全家的版本管理，各 starter 引入时无需再写版本号。

**2. 按需引入 starter**（版本随 BOM 统一对齐）：

```xml
<dependency>
    <groupId>io.github.jevonsnotes</groupId>
    <artifactId>jevokit-security-xss-spring-boot-starter</artifactId>
</dependency>
```

**3. 最小配置**（`application.yml`，配置前缀 `jevokit.*`）：

```yaml
jevokit:
  xss:
    enabled: true          # 开启 XSS 防护
    mode: clear            # 处理模式：clear 清洗 / escape 转义 / reject 拒绝访问
    url-patterns:          # 拦截范围
      - /**
    url-exclude-patterns:  # 排除路径
      - /example
```

各 starter 的完整配置项与代码示例见详细文档；密钥类配置的演示默认值（开箱可用、生产必须替换）见示例应用配置。

- 详细文档：[jevokit-spring-boot/README.md](jevokit-spring-boot/README.md)（中文，按模块展开）
- 示例应用：[jevokit-spring-boot/sample/jevokit-sample](jevokit-spring-boot/sample/jevokit-sample)

## 示例应用

`sample/jevokit-sample` 聚合演示了各 starter 的用法，演示配置均为开箱可用的默认值（含演示密钥，生产环境必须替换）。

本地启动（无需 RocketMQ，激活 `local` profile 即排除 MQ 自动装配；需本机 Redis `localhost:6379`，数据库用 H2 内存自给）：

```bash
cd jevokit-spring-boot/sample/jevokit-sample
mvn spring-boot:run -Dspring-boot.run.profiles=local
# 或打包后 java -jar --spring.profiles.active=local，或 IDE 中激活 local profile
```

## 测试与构建

源码构建顺序：先安装 BOM，再构建主 reactor（主 reactor 通过 import 解析 `jevokit-dependencies` 的版本管理）。

```bash
# 1) 安装 BOM 到本地仓库
cd jevokit-spring-boot-dependencies && mvn install

# 2) 主 reactor：默认测试集（冒烟 + 非 MQ E2E）——需本机 Redis，无需 RocketMQ
cd ../jevokit-spring-boot && mvn test

# 3) 全量测试（含 RocketMQ E2E）——另需 RocketMQ nameserver localhost:9876
mvn test -DexcludedGroups=
```

说明：默认构建通过 surefire `excludedGroups=mq` 排除唯一真依赖 MQ 的用例（`@Tag("mq")`）；`-DexcludedGroups=` 传空串即不排除。

## 版本号与变更日志

版本号四段式 `A.B.C.D`：前两段对齐 Spring Boot 版本线（`3.5` ⇔ Spring Boot 3.5.x）；第三位为本项目**破坏性变更位**（不兼容改动时 +1，第四位归 0，如 3.5.0.0 → 3.5.1.0）；第四位为常规迭代位（兼容性变更 +1）。

各版本变更明细见 [CHANGELOG.md](CHANGELOG.md)；GitHub Releases 与 tag 对应发布版本。

## License

[Apache License 2.0](LICENSE)
