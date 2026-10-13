# 降级 Spring Boot 3.5.16 → 2.7.18 设计

## 0. 输入与交叉验证状态

| 输入 | 来源 | 状态 |
|---|---|---|
| 模块地图（20 模块、L0-L4 依赖层级） | java-backend-eng-3 | 与架构侧 pom 扫描一致 |
| jakarta 使用面（main 33 文件 71 处 + test 4 文件 6 处，逐 API 核对 javax 等价性） | java-backend-eng-1 | 已采纳；坐标策略经架构复核修正（D2） |
| 编译级别与语法扫描（main 仅 1 处 Java 9+ API；test 约 101 处；source/target 静默兼容炸弹） | java-backend-eng-2 | 已采纳，驱动裁决 D1 |
| 测试基线（533 用例、516 库模块全绿、sample 13 error 均为 RocketMQ 环境阻塞、依赖树 jakarta 足迹快照、AC-1~8） | test-engineer-1 | 已采纳；AC「零 jakarta」语义按 D2 修正 |
| 旧版依赖约束 | 前身定制版本 dependencies BOM（2.7.2.x 线，本地存档不入库） | 版本矩阵对照基准 |
| 插件漂移教训 | docs/lessons/bug-bom-import-no-pluginmgmt-plugin-version-drift-20260910.md | 已应用（风险节 R4） |

## 1. 决策记录（ADR）

### D1 编译级别：Java 8（裁决）

**背景**：eng-3 主张"Java 17 在 Boot 2.7 支持范围内，java.version 可不动"；eng-2 实证前身定制版本为 1.8，且发现 Boot 2.7.18 parent 走 source/target（非 release）的静默兼容炸弹。协调者要求架构裁决。

**决策**（已确认 2026-10-12，用户终确认）：`java.version=1.8` + 显式 `maven.compiler.release=8`。构建继续用现有 JDK 17 工具链交叉编译（release=8 使 javac 按 JDK 8 平台 API 符号表校验，List.of 之类直接编译报错，炸弹在编译期排除），无需为本变更配 JDK 8 本机环境；真实 JDK 8 运行时冒烟作为发布前可选加固（容器 Temurin 8 跑 sample fat jar）。

**理由**：
1. **产品定位**：2.7 线存在的唯一理由是服务"升不上 Boot 3"的用户，该人群主力是 JDK 8（Boot 3 硬性要求 JDK 17）。库字节码编到 61（Java 17）恰好把这群人排除在外，2.7 线就失去存在意义。JDK 8 字节码反向覆盖 JDK 8/11/17 全体用户——Java 8 目标是两个种群的全集，Java 17 目标只覆盖子集。
2. **先例**：前身定制版本 2.7 线（本线受众的血统来源）实证为 1.8。
3. **成本已实测且小**：main 生产代码仅 1 处 Java 9+ API（sample NoGenApi.java:92 Stream.toList）；零语法特性（record/sealed/switch 箭头/instanceof 模式/var 全仓 0）；test 约 101 处纯机械替换，约 0.5-1 人天。
4. **不放 17 的代价**：若确认受众全部 ≥JDK 17，回退本决策只需改一行 properties；反向（先 17 后 8）则要重做一遍排查。

**影响**：main 1 处 + test 101 处 API 替换；下文矩阵中"保留现版本"的三方库逐项核验 Java 8 字节码（class file 52.0），列入验收闸门。

**机制自洽确认（eng-2 交叉验证）**：maven-compiler-plugin 3.x 中 `maven.compiler.release` 优先级高于 source/target——Boot 2.7.18 parent 的 `java.version → maven.compiler.source/target` 属性链不会压制根 pom 的 release=8，两者并存时 release 生效，无需绕开 parent 属性；JDK 17 + release=8 走 ct.sym 符号校验，Java 9+ API 全部编译期报错。清单合并注意：release=8 编译错误清单与人工扫描清单高度重合但有两类边界项编译器不产生条目——空集合语义（`List.of()`/`Map.of()` 换 `Collections.emptyList/emptyMap`，报错形式可能不易关联）与行为等价改写（String.repeat 5 处换 StrUtil.repeat），合并时以编译错误清单为主、人工清单补语义核对项。

### D2 jakarta.*-api pom 坐标：保持不动（对工程扫描结论的修正）

**背景**：eng-1/eng-3 判断"4 处 pom 的 jakarta.*-api 坐标降级后失管，需换 javax.* 坐标"；eng-1 又疑"CXF 3.5 传递链存在坐标 jakarta、包名 javax 的合法形态"。

**决策**：`jakarta.servlet-api`（core，provided）、`jakarta.persistence-api`（core、nogen）、`jakarta.annotation-api`（desensitization）**坐标全部保持不动，不写版本号**。依据：Jakarta EE 8 的坐标/包名分裂——Boot 2.7.18 的 spring-boot-dependencies 恰好管理这批 EE8 过渡坐标，其字节码包名就是 `javax.*`：

| 坐标（不变） | Boot 2.7.18 管理版本 | 实际包名 |
|---|---|---|
| jakarta.servlet:jakarta.servlet-api | 4.0.4 | javax.servlet |
| jakarta.persistence:jakarta.persistence-api | 2.2.3 | javax.persistence |
| jakarta.annotation:jakarta.annotation-api | 1.3.5 | javax.annotation |
| jakarta.xml.bind:jakarta.xml.bind-api | 2.3.3 | javax.xml.bind |

**实证确认（eng-1，Central 拉取 spring-boot-dependencies-2.7.18.pom 与 jar 实测）**：上表 4 项管理条目坐实（pom 行号 1091/1086/1056/1121），且 jakarta.servlet-api:4.0.4 jar 内 class 路径为 javax/servlet/*——EE8 过渡形态非推测；另实证 jakarta.validation-api 2.0.2（javax.validation 包名）亦在 BOM 内，api/sample 的 10 条 validation import 纯换无需动坐标。**例外一项：jakarta.jws-api 不在 Boot 2.7.18 BOM 管理列表内**——sample 的 5 条 jws import（降级后 javax.jws.*）编译来源若靠 CXF 3.5.x 传递（cxf-rt-frontend-jaxws 传 jakarta.jws-api 2.x），则踩中传递依赖消费断点（教训 bug-eureka-transitive-dep-consumer-sweep 同型）：CXF 换版本即可能断编译。处置：sample pom **显式声明** `jakarta.jws-api:2.1.0`（EE8 末代，javax 包名；BOM 不管需写版本），CXF 传递仅作冗余；验收 G6 增加依赖树 jws-api 在场断言。

**理由**：坐标换 javax.*（javax.servlet:javax.servlet-api:4.0.1 等）是等效但更差的方案——版本脱离 parent 管理、diff 变大、且与 CXF 3.5 传递链的形态（坐标 jakarta、包名 javax）不一致。**代码 import 改 javax、pom 坐标留 jakarta，是本降级的标准形态而非漏改**。

**对测试 AC 的修正**：「依赖树零 jakarta」应为**依赖树零 EE9+ 版本的 jakarta 工件**——基线快照完整销账清单：servlet-api 6.0.0、persistence-api 3.1.0、annotation-api 2.1.1、xml.bind-api 4.0.5、activation-api 2.1.4、validation-api 3.0.2、ws.rs-api 3.0.2、inject-api 2.0.1、mail-api 2.1.5、**xml.ws-api 4.0.3、xml.soap-api 3.0.2、jackson-jakarta-rs-json-provider/base 2.21.4**（后三项为测试基线补全）降级后应全部消失或落回 EE8 版本；jakarta.*-api:2.x/4.x（javax 包名）形态合法保留；javax.cache:cache-api 1.1.1 为合法存量。基线日志留档于 `%TEMP%\baseline-compile-3.5.16.log` / `baseline-test-3.5.16.log` / `baseline-deptree-3.5.16.log`，验收时逐项对比。

### D3 Camunda：7.19.0（7.20+ 排除，7.18.0 兜底）【已定稿 2026-10-12，eng-3 Central 实查】

**决策**：workflow 模块 camunda `7.23.0 → 7.19.0`。

**理由**：camunda-bpm-spring-boot-starter 7.20.0 起切换 Spring Boot 3 / jakarta，必须排除；7.19 是 javax/SB 2.7 starter 的**末代线**，支持 JDK 8-17。workflow 模块 main 源码 0 jakarta import，引擎 API 在 7.x 内稳定，预期仅版本与回归成本。

**实证依据（eng-3，Central raw dump 阳性对照）**：7.19 线仅 7.19.0 一个正式版（5 个 alpha 不取，无补丁版可选）；camunda-parent 7.19.0 继承链实证 version.spring-boot=2.7.9、spring.framework=5.3.22（javax 线）；7.20.0 实证 version.spring-boot=3.1.1，坐实排除。

**兜底**：与 SB 2.7.18 集成回归若失败，回退 7.18.0（7.18 线同样仅 7.18.0 无补丁，前身定制线实证值）。7.20+ 不可回退使用。注意：7.18.0 与 7.19.0 同属"H2 官方矩阵仅 1.4.x"时代（见 R11），回退不解决 H2 风险。

### D4 Redisson：3.50.0 + 显式 redisson-spring-data-27【两项前置验证通过，已定稿 2026-10-12，eng-1 字节码级实证】

**决策**：`redisson.version=3.50.0` 保留，且 **redis 模块 pom 必须做两件事**（缺一不可，eng-3 4.5 预置检查发现的机制缺口）：
1. 对 `redisson-spring-boot-starter` 加 `<exclusion>` 排除 `redisson-spring-data-35`——**Maven dependencyManagement 机制边界：只对"树中已存在的 artifactId"管版本，不会把 starter 传递的 -35 替换成 -27**（eng-3 实证 starter 3.50.0 POM 传递依赖即 -35:${project.version}）；仅靠 root depMgmt 的 -27 条目，结果是 -35 与 -27 并存入树，-35 面向 spring-data-redis 3.x，Boot 2.7 下二进制不兼容；
2. 显式声明 `redisson-spring-data-27` 依赖（不写版本，走 root depMgmt 3.50.0 条目）。

**验收双断言**：redis 依赖树 -35 零在场、-27:3.50.0 在场（与 G6 的 "-27 在场且 -3x 不在场" 口径一致）。

**验证结论（两项前置均通过）**：
1. `redisson-spring-data-27:3.50.0` 在 Central 发布，其 pom 依赖 spring-data-redis:2.7.18——与 Boot 2.7.18 BOM 管理版本**完全同版**，compile 传递无漂移；
2. SB 2.7.18 下自动配置激活路径：starter 双轨注册——spring.factories 注册的 RedissonAutoConfiguration 带 @ConditionalOnMissingClass(AutoConfiguration)（Boot ≤2.6 专用，2.7+ 关闭），AutoConfiguration.imports 注册的 RedissonAutoConfigurationV2（@AutoConfiguration before=RedisAutoConfiguration）为 2.7.18 下**唯一激活入口**，kit 无需任何配置介入；
3. Boot 3 专有 API（RedisConnectionDetails/SslBundles 系）在 starter 内经 CNFE 防御与反射判空双保险跳过（字节码级实证：hasConnectionDetails() 在 SB2.7 恒 false、initSSL() 因 RedisProperties 无 getSsl() 必走跳过），签名级引用为 0，不会触发 NoClassDefFoundError。

**已知行为差异（入 §4 差异清单）**：SB 2.7.18 下 redis 配置不支持 ssl-bundle 形态（Boot 3.1+ 特性），`spring.redis.ssl` 布尔开关仍有效。

**兜底**：3.25.0 仅当集成回归失败时启用——对照组实证 3.25.0 与 3.50.0 同构双轨、构建主线同为 Boot 3 线（-32），回退无兼容收益，**默认不回退**。

### D5 RocketMQ：2.3.0 / 5.2.0【官方原生配套实证，已定稿 2026-10-12】

**决策**：`rocketmq-spring-boot-starter 2.3.0` 与 `rocketmq(client) 5.2.0` 保留。

**实证升级（eng-3，rocketmq-spring-boot-parent 2.3.0 官方 POM 实查）**：官方parent 钉 `rocketmq.version=5.2.0`、`spring.boot.version=2.7.18`——本裁决不是兼容性判断而是**官方原生配套**（SB 基线精确到 2.7.18），root `rocketmq.version=5.2.0` 与官方一致。2.3.0 在 2.7.18 上即官方目标环境；lessons 中 rocketmq5 相关教训（bug-rocketmq5-connect-null-misread）基于 5.x client 行为，保留可延续其结论有效性。

**回退预案归档**：2.2.2 + 4.9.8（前身定制线实证组合）仅留档备查——官方原生配套实证后无回退动机；配置键在 2.2.x 同名零改动（兜底成本已证极低）。

### D6 项目版本号：2.7.3.0-SNAPSHOT，首发 2.7.3.0

**决策**（已确认 2026-10-12，用户终确认）：`A.B.C.D = 2.7.3.0`（开发期 2.7.3.0-SNAPSHOT）。

**理由**（按 docs/preferences/pref-version-scheme.md 四段式）：
- **A.B=2.7**：对齐 SB 2.7.18 版本线（版本线只对齐 major.minor，.18 补丁号不入版本，与 3.5.2.x 不编码 3.5.16 同理）；
- **C=3**：本线受众（前身定制版本 2.7.2.x 线用户）迁移到 jevokit 2.7 线是一次破坏性跨越——坐标（旧版坐标→io.github.jevonsnotes）、配置前缀、包名、模块数全变，外加 jakarta→javax 回归；按"破坏性变更第三位 +1"，从既有 2.7.2.3 血统计数进到 3，同时数字上高于一切已知 2.7.2.x，避免"新版比旧号小"的误读；
- **D=0**：新代际首发归 0（与 tags v3.5.1.0/v3.5.2.0 的首发形态一致）。

**备选（已评估不取）**：`2.7.2.0`（把 jevokit 2.7 线当独立产品从代际 2 起计，优点是与 3.5.2.x 功能代际直观对齐；缺点是对前身血统用户第三位未体现破坏性、且 2.7.2.0 < 2.7.2.3 数字倒挂）。`2.7.2.3`/`2.7.2.4`（沿用旧版尾号）：不同血统、低估差异，排除。

**配套**：README/CHANGELOG 显式标注"2.7.3.0 ≙ 3.5.2.2 功能全集"；jevokit-dependencies BOM 壳随 project.version 联动 2.7.3.0-SNAPSHOT。

### D7 parent 管理核心件：属性覆盖钉版（继承旧版手法）

Boot parent 换 2.7.18 后，以下 parent 属性覆盖项**显式钉版**（机制与前身定制线完全一致：覆盖 parent 同名属性即重调整条管理线）：

| parent 属性 | 2.7.18 默认 | 钉版值 | 依据 |
|---|---|---|---|
| spring-framework.version | 5.3.31 | **5.3.39** | 5.3 线末代 OSS 版（前身定制线同值），含 5.3.31 之后全部修复 |
| tomcat.version | 9.0.83 | **9.0.122** | 9.0.x 线最新（2026-10-12 实查），覆盖 parent 默认 9.0.83；前身定制线同策略（9.0.102） |
| logback.version | 1.2.12 | **1.2.13** | 1.2 线终版（前身定制线同值）；随 SLF4J 回落 1.7 线 |
| snakeyaml.version | 1.3x | **2.0** | Boot 2.7.13+ 兼容 SnakeYAML 2.x（前身定制线在 2.7.18+2.0 实证）；解 CVE-2022-1471 |
| jackson-bom.version | 2.13.5 | **2.15.3** | 前身定制线实证值；2.13 线过老，2.15.3 与 2.7.18 兼容性已验证；后续升 2.17/2.19 作为第四位常规迭代另议 |

不钉（接受 parent 2.7.18 默认）：lombok（1.18.30）、junit（5.8.2）、mockito（4.5.1）、assertj（3.22.0）、hibernate-validator（6.2.5）、micrometer（未使用，验收时以依赖树确认）。

## 2. 依赖版本矩阵（properties 逐项）

图例：**保留**=维持现值；**回退**=采用旧版值；**钉版**=新增 parent 属性覆盖（D7）；值后括号为依据编号 [n]。

[1] 与 Spring Boot 无耦合（纯 Java 库，Java 8 兼容），降级不动，版本足够新且 class file 52.0 需验收核验
[2] 前身定制版本在 SB 2.7.18 上的实证值
[3] 与 Boot 2.7.18 的版本线配对要求（Spring 官方兼容矩阵）
[4] 本方案裁决（D1-D7）
[5] 全仓无引用，确认不再需要（jettison/logstash 为旧版 xstream JSON/日志场景预留，jevokit 无此用法——全仓 grep 实证）

| 属性 | 现值（parent 3.5.16） | 前身定制线（2.7.18） | 本方案 | 类别 |
|---|---|---|---|---|
| java.version | 17 | 1.8 | **1.8 + maven.compiler.release=8** | D1 裁决 |
| parent（无属性，直接坐标） | 3.5.16 | 2.7.18 | **2.7.18** | 目标本身 |
| hutool.version | 5.8.46 | 5.8.39 | **5.8.46** | 保留 [1] |
| jsoup.version | 1.23.2 | 1.15.3 | **1.23.2** | 保留 [1]（jsoup 1.x 线仍 Java 8；验收核验字节码） |
| spring.could.version | 2025.0.3 | 2021.0.9 | **2021.0.9** | 回退 [3]（SB 2.7 ↔ 2021.0.x Kilburn 末代；拼写遗留本变更不动，已决策） |
| fastjson.version | 2.0.53 | 2.0.52 | **2.0.53** | 保留 [1] |
| apache.common.version（commons-io） | 2.18.0 | 2.13.0 | **2.18.0** | 保留 [1] |
| poi.version | 5.4.1 | 5.2.5 | **5.4.1** | 保留 [1]（POI 5.x 线 Java 8） |
| redisson.version | 3.50.0 | 3.25.0 | **3.50.0 + 显式 redisson-spring-data-27:3.50.0**（两项前置验证通过，D4 定稿） | 定稿 D4 |
| transmittable.thread.local | 2.12.0 | 2.12.0 | 不变 | — |
| tablesaw.version | 0.43.1 | 0.43.1 | 不变 | — |
| camunda.spring-boot.version | 7.23.0 | 7.18.0 | **7.19.0**（兜底 7.18.0；7.19 线仅此一版，D3 定稿） | 定稿 D3 |
| rocketmq.spring.version | 2.3.0 | 2.2.2 | **2.3.0**（败则 2.2.2） | 裁决 D5 |
| rocketmq.version（client） | 5.2.0 | 4.9.8 | **5.2.0**（随 D5） | 裁决 D5 |
| h2.version | 2.2.220 | 2.2.220 | 不变 [2]（2.7.18 默认 2.1.214，属性覆盖机制同） | — |
| classgraph.version | 4.8.174 | 4.8.174 | 不变 | — |
| snakeyaml.version | （注释，Boot 管 2.4） | 2.0 | **钉 2.0**（取消注释） | 钉版 D7 |
| commons-text.version | 1.12.0 | 1.10.0 | **1.12.0** | 保留 [1] |
| postgresql.version | 42.6.0 | 42.3.9 | **42.6.0** | 保留 [1] |
| tomcat.version | （注释，Boot 管 10.1.x） | 9.0.102 | **钉 9.0.122** | 钉版 D7 |
| logback.version | （注释，Boot 管 1.5.x） | 1.2.13 | **钉 1.2.13** | 钉版 D7 |
| mysql.version | 8.2.0 | 8.2.0 | 不变（2.7.18 已管理 com.mysql:mysql-connector-j 坐标，属性名 mysql.version 兼容） | — |
| guava.version | 33.2.1-jre | 33.2.1-jre | 不变 | — |
| xstream.version | 1.4.21 | 1.4.20 | **1.4.21** | 保留 [1] |
| woodstox.version | 6.5.1 | 6.4.0 | **6.5.1** | 保留 [1] |
| spring-framework.version | （注释，Boot 管 6.2.x） | 5.3.39 | **钉 5.3.39** | 钉版 D7 |
| xmlunit2.version | 2.10.0 | 2.10.0 | 不变 | — |
| json-path.version | 2.9.0 | 2.9.0 | 不变 | — |
| commons-compress.version | 1.26.0 | 1.26.0 | 不变 | — |
| netty.version | 4.1.119.Final | 4.1.108.Final | **4.1.119.Final** [1]（属性覆盖机制 2.7 同） | 保留 |
| bouncycastle.version | 1.86 | 1.79 | **1.86** | 保留 [1]，附 R5 傍路风险 |
| okhttp.version | 4.12.0 | 4.12.0 | 不变 | — |
| commons-beanutils.version | 1.11.0 | 1.11.0 | 不变 | — |
| jackson-bom.version | （注释，Boot 管 2.19.x） | 2.15.3 | **钉 2.15.3**（取消注释） | 钉版 D7 |
| （无）jettison | — | 1.5.4 | **不引入** | 全仓无引用 [5] |
| （无）logback-logstash | — | 6.6 | **不引入** | 全仓无引用 [5] |
| （新增）CXF（sample 硬编码） | 4.1.2 | —（旧版无 CXF） | **3.5.x 线最新补丁**（≥3.5.9，实施实查） | 回退 [3]（CXF 4.x 全线 jakarta，与 SB2.7/Tomcat9 不兼容；3.5.x 是 javax 线） |

**注意（新依赖入管）**：CXF 版本当前硬编码在 sample pom，本次一并上移 root dependencyManagement（`cxf-spring-boot-starter-jaxws` ${cxf.version}），消除硬编码漂移面。**定稿 cxf.version=3.5.11**（3.5 线最新，eng-3 实查；jar 开包实证 jakarta.jws-api 2.1.0=javax/jws 包、xml.bind-api 2.3.3=javax/xml/bind 包、xml.ws-api 2.3.3/validation-api 2.0.2/annotation-api 1.3.5 同形态——D2 预期成立）。**同批入管**：`redisson-spring-data-27:3.50.0`（D4 定稿，防 starter 默认传递 -35）。

## 3. parent 回退受影响面（3.5.16 → 2.7.18 管理线整体位移）

- **Spring 生态**：Spring Framework 6.2.x→5.3.31（钉 5.3.39）；Spring Security 未使用（依赖树确认）；spring-cloud-context 由 BOM 2021.0.9 管理。
- **Web 容器**：Tomcat 10.1.x（jakarta）→9.0.x（javax），14 个模块 test/compile 引用的 tomcat-embed-core 随动。
- **序列化**：Jackson 2.19.x→2.13.5（钉 2.15.3）；jackson-jakarta-rs 等 jakarta 专属工件从树上消失（验收销账项）。
- **日志**：Logback 1.5.x/SLF4J 2.x→1.2.12（钉 1.2.13）/1.7.36；sample 的 logback-spring.xml 需复核 1.2 语法兼容。
- **校验**：Hibernate Validator 8.x（jakarta.validation 3.0）→6.2.5（javax.validation 2.0）。
- **测试栈**：JUnit 5.13→5.8.2、AssertJ 3.27→3.22.0、Mockito 5.x→4.5.1、spring-boot-starter-test 全家随动；测试代码需扫描新版线专有 API（如 Mockito 5/AssertJ 3.24+ 方法），配合 101 处 Java 9+ API 回退一次做完。
- **构建插件**：compiler 3.14.1→3.10.1（2.7 parent 自带 `<parameters>true</parameters>`，复核）；surefire 3.5.6→2.22.2（`@{argLine}` 延迟替换与 UTF-8 argLine 方案在 2.22.2 有效——AC-7 验证）；jar/install/deploy 等随动。自有钉版（jacoco 0.8.12、source 3.3.1、javadoc 3.11.3、gpg 3.2.7、central-publishing 0.8.0）不依赖 Boot 版本，不受影响。
- **未使用需复核为确实未引入**：micrometer/actuator、spring-security、spring-data-jpa——验收时依赖树 diff 确认。

## 4. 代码层 API 差异清单（SB/FW 3.5 → 2.7）

1. **namespace 回退（主工作量）**：`jakarta.*`→`javax.*` 七族——servlet 45 条（HttpServletRequest/Response、wrapper 三件套、Filter、Part、ServletInput/OutputStream、Read/WriteListener 等；含 core RequestContentCachingRequestWrapper:47 的 jakarta 构造器，编译基线唯一告警点）、validation 10（@Valid/@NotNull/@NotBlank）、persistence 9（@Table/@Column，仅 SqlUtil 反射读 name，无 EntityManager）、annotation 3（@PostConstruct/@PreDestroy）、jws 5、xml.ws 1、xml.bind 4。逐 API 已核对 javax 侧同名同签名，纯 import 替换；全仓无内联 FQN/字符串/反射形态；DesHelper.java:26 注释一并清理。**方法论（eng-2 阶段 6 实证后固化）：点名清单不可信——sample 在 6.1/6.2 点名清单外又抓到 6 处 validation/servlet 残留；各模块收尾必须做 java 层 jakarta 全字扫（与 glob 假阴性教训同族），零命中才可交付。**
2. **Spring Framework 6→5.3**：初期扫描结论"FW 层无源码改动"经 release=8 编译实证**修正**：main 存在 1 处 instanceof 模式匹配（api 模块 EncryptResponseAdapter 家族 EncryptResponseAdviceAdapter.java:67，需回退经典 instanceof+强转；初扫 rg 正则对 `&&` 交集解析缺陷致漏）+ test 2 处 Spring 6 专有 API（setCharacterEncoding(Charset) 重载→String 版）+ test 5 处 @Nested 类 static 成员（Java 16 前 static 非法，core SecurityUtilTest）。**方法论结论**：语法/API 盲区必须靠 release=8 编译器兜底，人工扫描（含 grep 正则）只作清单预置；且 test 文件 import 失败时 javac 跳过方法体检查——test 侧编译校验须在 main 修完后重跑才完整。最终清单 110 处（eng-2 产出，阶段 7 切分依据）。
3. **自动装配**：`META-INF/spring/...AutoConfiguration.imports` 注册文件是机制本体（17 个模块零改造）；`@AutoConfiguration` 注解 Boot 2.7 起提供、2.7.18 下编译兼容——eng-1 阶段 3 实证 autoconfigure 模块实际未用该注解（@Configuration + .imports 形态），main 代码中该注解使用面极小（初扫仅 audit-log main 一处，Wave 批次顺带确认即可），无论用否均零改动。仅回归（风险节 R6 备查：若未来再向 2.6- 降级则该机制全断，须全面回退 spring.factories）。
4. **配置键回迁（Boot 3 专属键 → 2.7 键）**：
   - `spring.data.redis.*` → `spring.redis.*`（sample application.yml:207 已实证，test-engineer AC-6 要求真实启动绑定验证）；
   - 全量排查结论（eng-3 逐行复核 247 行 yml 后修正其 glob 假阴性漏检）：sample yml 其余键在 2.7 语义不变——spring.config.import（2.4+）、datasource/hikari、h2.console、jasypt.*（3.0.5 两代兼容）、camunda.bpm.*（键名随 D3 版本走，7.19 下各键均在）；jevokit.* 自有键无 Boot 耦合；redisson 走 spring.redis.*（D4 V1 前置验证）。**待改仅 spring.data.redis 一处**，实施时以 sample 真实启动（AC-6）兜底确认无漏网。
5. **logback 1.5→1.2 线回落（低风险，已预核）**：sample 的 logback-spring.xml（%green/%cyan、SizeAndTimeBasedRollingPolicy、springProperty）与 desensitization 全部 logback API 引用（ILoggingEvent/Filter/FilterReply/Console/RollingFileAppender/LoggerContext/ListAppender 等）经逐项核对均为 1.2 稳定特性/API，无 1.3+ 专有引用；回归由 AC-2 全量用例（含 DesensitizedLogFilterTest）覆盖。
6. **servlet/JAX-WS 栈**：CXF 4.1.2→3.5.x 版本决策 + sample 5 条 jws + 1 条 xml.ws import 替换 + TestVo 4 条 xml.bind 注解替换；sample 显式补 `jakarta.xml.bind-api`（parent 管 2.3.3，javax 包名）——当前裸奔靠传递，本就是缺陷。
7. **测试栈 API**：Mockito 4.5/AssertJ 3.22 无 5.x/3.24+ 专有 API 使用则零改动（待工程侧扫描清单）；MockMvc/MockHttpServletRequest 等 6 处 test jakarta import 随主替换。
8. **redisson 已知行为差异（D4 定稿附带）**：SB 2.7.18 下 redis 配置不支持 ssl-bundle 形态（RedisConnectionDetails 为 Boot 3.1+ 特性，starter 内防御性跳过、字节码实证恒不生效）；`spring.redis.ssl` 布尔开关仍有效。kit 自身无 ssl-bundle 用法（sample/测试均未配置），对现用户零影响，仅作为迁移文档要点记录。
9. **release=8 编译暴露的 API 修复族谱（core 2.4 实证 14 处补修提炼，Wave 后续批次实施指引）**：
   - ① Java 10+ Charset 重载族：`URLEncoder.encode/URLDecoder.decode(String, Charset)` → `.name()` 字符串形态（grep "StandardCharsets" 作参数即可圈定）；decode 系抛 checked UnsupportedEncodingException，需确认既有 catch(Exception) 兜底或补 throws——core 共 6 处（4 main + 2 test）；
   - ② 匿名内部类钻石 `new X<>() {}`（Java 9+）→ 显式补类型参数；
   - ③ Spring 6 test API：`ContentCachingRequestWrapper.getContentAsString()`（5.3 无）→ `new String(getContentAsByteArray(), charset)`；
   - ④ @Nested 内 static 成员族（Java 16 才放开 static）：**对含 @Nested 的测试文件做全文件 static 声明清点（类/字段/方法三类 + 方法引用联动，如 FilterStr::mask），不可只信 javac 首轮报错清单（inner-class-static 报错可能截断）**；修复优先上提顶层类（保 fixture 语义——被测行为依赖 static 字段时去 static 会改变 fixture 性质），core 实证 6 处 + 1 方法引用，上提后 98/98 全绿；
   - ⑤ 归因纪律：编译错误以行号+符号实查为准，并行 import 插入会使行号偏移（教训：初归因 setCharacterEncoding(Charset)，实查真凶是 URLEncoder 重载）。
10. **failOnError=false 统一编译甄别方法论（统一编译收口 2026-10-12 实证固化，补充第 2 条编译器兜底方法论）**：阶段 4-6 全量收口用 `mvn test-compile -fae -Dmaven.compiler.failOnError=false` 一次暴露全部编译面错误（证据 evidence/20261012-unified-compile.log），甄别规则=**先剥离传递性级联、再定位真根因**——首轮 53 条错误的构成：根因 2 条（redis JevokitRedisConfiguration 双参构造器，模块自身 spring-data-redis 3.x API 漏项）+ 51 条级联（redis test 3 条 package does not exist、nogen 4 条引用 redis 符号失败、sample 42 条依赖上游产物缺失/符号缺失）；判定依据=错误形态（"package does not exist"/"cannot find symbol"指向上游模块坐标=级联，"no suitable constructor/method"指向具体 API 签名=真漏项）。**"级联剥洋葱"现象（本轮核心教训）**：failOnError=false 首轮的级联错误会**掩盖下游模块自身的独立存量错误**——javac 对 import 解析失败的文件跳过方法体检查（与第 2 条 test 盲区同机制），故 redis 2 条根因修复 + sample 6 处存量修复（readAllBytes/orElseThrow/MockPart/Map.of/匿名钻石×2）后复跑，暴露的并非直接归零而是更深一层的 sample 测试域存量错误，再修再跑方至真零（53→6→0，剥两层）。**结论：failOnError=false 的"零错误"判定必须"修根因→复跑→再甄别"迭代至连续两轮错误数不降且为零，单轮 BUILD SUCCESS（尤其 failOnError=false 下的 SUCCESS 表象）不可作零残留交付依据**——与阶段 1"假绿"教训（docs/lessons/bug-parent-version-stale-pom-false-green-20261012.md）互补：那条是"配置未生效的假绿"，本条是"错误分层暴露的假尽"，同属"编译通过 ≠ 收口完成"一族。
11. **日志路径容器依赖反模式与静态引用两阶段发布模式（DEF-1 验收期实证，eng-2 根因定性 + 架构定式化，kit 规范候选）**：**反模式**——logback filter（挂在任意 appender、任意线程触发）等日志链路在事件处理中调 `SpringUtil.getBean`：上下文刷新期 main 线程持单例锁创建 bean，任意异步线程（redisson netty 回调等）此刻打日志即阻塞在锁上；若 main 所等 future 的完成方恰为被阻塞线程（Redisson 连接回调打日志），成三方循环等待、JVM 永久挂起。**修复模式（定式）**：① `@PostConstruct`（init）发布**配置类静态引用**（此刻单例锁仍被持有，实例为裸形态不可发布）；② `ContextRefreshedEvent`（finishRefresh 阶段、锁已释放）经**容器 getBean** 发布实例引用（保留 AOP 代理/切面语义）；③ 日志链路只消费静态引用，未发布时 **fail-open**（与"脱敏失败绝不阻断日志输出"既有语义一致），绝不回退 getBean；④ 非日志链路保持容器查找原语义（按调用路径分治）。**已落码**：DesHelper（volatile 就绪标志 + 两阶段发布 + 专项回归 DesHelperLogPathNoContainerTest）。**已知边界（配条款）**：static 就绪状态为 JVM 全局单份、无 ContextClosedEvent 清理联动——spring-test 多上下文场景存在残留态（ready 跨上下文残留 true 为 DEF-1 测试挂起的同族时序因素），生产单 context 不触达、异常被 fail-open 兜底；规范沉淀时应补"static 状态与 context 生命周期绑定"条款。**同类加固（不在本变更范围，按 Non-goals 归后续第四位常规迭代，建议 code-refactorer 排期）**：AuditLogFilter:90、BzNoHelper:402、OkHttpUtil:833-879 等 6 处"运行期线程 getBean"——当前均在刷新完成后调用、不构成等待环（低危），但同属本反模式家族，可统一抽静态引用模式。

## 5. 模块清单与改造顺序

模块依赖层级（eng-3 模块地图 + pom 扫描复核）：

```
jevokit-parent ── jevokit-dependencies（BOM 薄壳，中性，仅随版本号）
   └─ L0 core/jevokit-core（无内部依赖；servlet wrapper 三件套 = 全生态复用点）
        └─ L1 core/jevokit-autoconfigure（+spring-cloud-context）
             └─ L2 15 个功能 starter（各自独立，仅依赖 autoconfigure）
                  ├─ L3 nogen → redis-starter
                  └─ L3 security-all → 5 个 security starter
                       └─ L4 sample（聚合 11 starter + CXF + RocketMQ）
```

**改造顺序（先地基后分支，逐层可编译闸门）**：

| 阶段 | 范围 | 出口条件 |
|---|---|---|
| 0 前置验证 | D4 redisson 两项验证、D3 camunda 7.19 线版本实查、CXF 3.5 线版本实查 | 版本矩阵定稿，无"实施中改决策" |
| 1 根 pom | parent 2.7.18、版本号 2.7.3.0-SNAPSHOT、java 1.8+release=8、D7 钉版属性、版本矩阵全部落 properties、CXF 入 dependencyManagement、bouncycastle jdk15on 旁路核查（R5） | `mvn -N validate` 过；依赖树抽查。**阶段 1 实施教训（已发生，lessons 立条候选）**：19 个子模块 pom parent 块硬编码旧版本号，只改根 pom 时 Maven 从本地仓库静默解析旧 jevokit-parent，出现"BUILD SUCCESS 假绿"（19 模块仍走 Boot 3.5.16/release 17）——与"陈旧 jar"教训（bug-rename-stale-local-repo-jar）同族：本地仓库对旧坐标的静默解析掩盖配置未生效。防回归守则：**版本翻新必须全链 grep 旧版本号**（含子模块 parent 块），且以"改后首次编译必须出现预期新错误"为生效证明（本次 859 错误即生效证据，零错误反而可疑） |
| 2 L0 core | servlet wrapper javax 化（3 文件 11 处）、SqlUtil persistence、jakarta.servlet-api 坐标核验（D2） | core 单模块 `mvn clean install` 绿（含其测试 API 回退，26 处） |
| 3 L1 autoconfigure | spring-cloud-context 2021.0.9 随动、@AutoConfiguration 编译复核 | 单模块 install 绿 |
| 4 L2 Wave A（main 含 jakarta，11 个） | xss、path-traversal、sql-inject、api、desensitization、audit-log、redis、nogen、excel、rocketmq、okhttp | 每模块 install 绿；audit-log 13 处 servlet 是 Wave A 最大单点 |
| 5 L2 Wave B（main 零源码改动，4 个） | web-fixed、code-inject、jasypt、workflow（camunda 7.19 随动回归） | 编译随动绿 |
| 6 L3/L4 | security-all（聚合随动）、sample（CXF 3.5 + jws/xml.bind/xml.ws + spring.data.redis→spring.redis + Stream.toList + text block + logback-spring.xml 1.2 复核） | sample 可启动（AC-6），RocketMQ 缺环境维持基线 4 过/13 阻塞不劣化 |
| 7 测试横切 | 全仓 test Java 9+ API 101 处、Mockito/AssertJ 版本线扫描适配、surefire 2.22.2 下 UTF-8/argLine 复验 | 库模块 516 用例全绿（AC-2） |
| 8 验收 | test-engineer-1 执行 AC-1~8 + 本设计新增闸门（见 §7） | 全部闸门过 |
| 9 文档收尾 | README（徽章/环境要求/BOM 链/2.7 线说明 + 2.7.3.0 ≙ 3.5.2.2 标注）、CHANGELOG 2.7.3.0 条目 | 评审过 |

并行建议：阶段 2-5 内部各模块相互独立（仅依赖前置层完成），可按工程位分派并行；阶段 7 可与 4/5 并行推进（按模块切分清单）。

**构建环境守则（eng-1 阶段 3 实证）**：`-pl` 单模块构建时 parent 走 relativePath 磁盘解析没问题，但产物 install 后**被其他模块依赖时**需 parent 已在本地仓库，否则下游 dependency:tree/install 报 "Could not find artifact jevokit-parent"——单模块作业前先 `mvn -N install` 一次；并行分片场景各实例本地仓库独立，Wave 开工前各自执行。同文件禁双改守则见 tasks 阶段 4 头部。

**无测试模块专项防线**：excel、rocketmq 两个 starter 完全无测试用例（dependencies、security-all 亦无，后两者为聚合壳/纯 pom，风险仅剩编译）——本次降级回归对它们零用例防线，显式安排「编译期验证 + 依赖树坐标/版本校验」专项（excel 涉及 poi 5.4.1 保留决策、rocketmq 涉及 D5 保留决策，二者恰是版本矩阵保留项的重点）；中期补冒烟用例不在本次范围（Non-goals）。

## 6. 风险清单

| # | 风险 | 概率/影响 | 缓解 |
|---|---|---|---|
| R1 | **release=8 编译暴露的三方库 Java 9+ API 用点**（main 已知仅 1 处，但 compile classpath 上经编译器校验后可能新增报错点） | 中/中 | 阶段 1 后立即全量 `mvn clean compile`，以编译错误清单驱动（eng-2 清单作预估值）；编译期暴露正是该机制的用途 |
| R2 | **保留的新版本三方库非 Java 8 字节码**（hutool 5.8.46/jsoup 1.23.2/POI 5.4.1/commons-io 2.18/redisson 3.50/netty 4.1.119/BC 1.86/xstream 1.4.21/woodstox 6.5.1/classgraph 4.8.174——[1] 类） | 低/高（下游 JDK 8 运行时 NoSuchMethodError/UnsupportedClassVersionError） | 验收闸门 G4：对全量 runtime 依赖 jar 做字节码版本核查（≤52.0），超标项按矩阵回退值处理 |
| R3 | **redisson 3.50 与 SB 2.7 适配断档**（D4 两项前置） | 中/中（redis/nogen/workflow 三模块） | 阶段 0 首项验证；败则整组回退 3.25.0+显式 spring-data-27（旧版实证） |
| R4 | **插件版本漂移**（教训 bug-bom-import-no-pluginmgmt）：本结构 jevokit-parent 有真实 Maven parent，pluginManagement 正常继承，sample 的 spring-boot-maven-plugin 自动锚定 2.7.18，历史陷阱不成立；但 versions-maven-plugin 仍无版本且 Boot parent 两代都不管理它（现状即漂移，教训中核定接受） | 低/低（仅本地用途，generateBackupPoms 配置兼容） | 本变更不动（与 spring.could 拼写决策同理，保持降级 diff 纯粹）；单独跟进钉版。保留教训防范措施 2 的 MANIFEST 卡点为验收闸门 G5 |
| R5 | **bouncycastle.version 属性同名覆盖的旁路效应**：3.5.16 parent 用该属性管理 jdk18on 工件，2.7.18 parent 用同名属性管理 jdk15on 工件（该 artifact 线止于 1.70）；若依赖树有传递引用 bcprov-jdk15on，会被解析到不存在的 1.86 版本导致构建失败 | 低/中 | 阶段 1 依赖树核查 bcprov/bcpkix-jdk15on 零引用；若有，额外钉 jdk15on=1.70 或迁移引用方到 jdk18on |
| R6 | **AutoConfiguration.imports 机制边界**：2.7.18 原生支持（两份扫描+架构复核三方一致），但仅 2.7+ 有效 | —/— | 无行动项；在架构备忘中记录：该线再向 2.6- 降级时 17 个模块须全面回退 spring.factories 注册 |
| R7 | **camunda 7.19.0 与 SB 2.7.18 starter 耦合摩擦**（camunda-parent 基于 2.7.9 构建，与 2.7.18 小版本差） | 低/中（workflow 模块） | D3 兜底 7.18.0；workflow 集成测试在阶段 5 出口强制回归（验证点见 R11） |
| R11 | **H2 2.2.220 × Camunda 7.19.0——已实测证伪（引擎级），降级为低**：eng-1 隔离探针（临时项目 fat jar 真启动、仓库零改动）四项全过——(a) ENGINE-03016 七组件 DDL 在 2.2.220 零摩擦 (b) classpath BPMN 自动部署 + processes.xml (c) 流程实例创建 + user task 查询 (d) admin-user 自动创建 + /camunda/ 302；fat jar 解包硬证据 h2-2.2.220.jar 与 camunda-engine-7.19.0.jar 实际在场（排除版本顶替）。**偏差解释：官方矩阵"仅列 1.4.x"是支持口径滞后，7.18/7.19 的 H2 DDL 资源已渐进吸收 H2 2.x 适配，非引擎事实**。已验证边界外的长尾（ACT_RU_JOB job executor DML、DMN 评估、history cleanup 批处理、并发乐观锁路径）留观备注；workflow 模块 2 基线用例不触达 H2/DataSource——模块内测不出本风险，探针/E2E 是唯一路径（对后续维护者是重要信息） | 低/中（仅长尾场景） | 剩余闸门：统一编译随动 + AC-2 sample E2E 真数据源作最终集成验证；**原预备决策点（①全局降 H2 1.4.200 / ②模块级隔离数据源）归档不启用**；如用户需更高保证可选加一轮 timer boundary event + async job 探针（eng-1 可复刻，约半小时） |
| R8 | **测试栈版本线回落的源码不兼容**（AssertJ 3.27→3.22、Mockito 5→4.5 专有 API） | 中/低 | 阶段 7 扫描适配；编译错误清单驱动 |
| R9 | **本地仓库陈旧 jar 陷阱**（教训 bug-rename-stale-local-repo-jar：本地库 F:\mavenRepo 非默认路径，降级换版本后 -pl 单模块构建可能解析到旧坐标 jar） | 中/中 | AC-8：根 install 刷库后单模块 -pl 抽验 |
| R10 | **spring.data.redis 漏网**（除 sample 已知一处外，test 资源/其他 yml 若藏 Boot 3 键） | 低/中 | AC-6 sample 真实启动绑定验证 + 全仓 yml/properties grep `data:`（已初扫，仅 sample:207 一处） |

**Top 3 风险（呈报用，2026-10-12 R11 探针证伪后更新）**：R2（保留的新版本三方库非 Java 8 字节码——影响所有下游 JDK 8 用户，静默失败形态，G4 核查前为最大悬置）、R8（测试栈回落的源码兼容——7.1/7.2 扫描进行中，已完成模块零改动）、R1 残余（test 侧编译盲区——main 清零后末批重跑 release=8 校验补全）。历史消除项：R3（redisson 断档，阶段 0 前置验证消除）、R11（H2×Camunda，隔离探针引擎级证伪）、R5（bcprov jdk15on，阶段 1 树核查消除）。**终验销账更新（test-engineer-1 阶段 8 全 PASS 后）**：R2 正式销账（G4 实测 321 项 compile/runtime 工件主代码字节码全 ≤52.0，module-info/MR-JAR 版本化类误报已甄别）；R8/R1 残余随 7.1/7.2 收口+终扫零残留消除；唯 8.12（真实 JDK 8 容器冒烟，可选项）未执行留观入遗留清单——编译层（release=8 交叉编译）与字节码层（G4）双防线已覆盖，运行时层实证缺位为已知边界。

## 7. 验收闸门（对齐 test-engineer AC-1~8 并补充）

- AC-1 编译不劣于基线（core wrapper 告警消失）；AC-2 库模块 516 用例全绿；AC-3 源码零 `import jakarta.`（含 DesHelper 注释清理）；AC-4 parent=2.7.18；AC-5 依赖树零 EE9+ 版本 jakarta 工件（按 D2 修正语义，对比基线快照逐条销账）；AC-6 spring.data.redis→spring.redis 真实启动绑定；AC-7 surefire 2.22.2 下 UTF-8 报告与 argLine 复验；AC-8 根 install 刷 F:\mavenRepo 后 -pl 单模块抽验。
- **G4（新增）**：runtime 依赖树全量 jar 字节码版本 ≤52.0（Java 8），超标回退矩阵值。
- **G5（新增）**：sample fat jar `META-INF/MANIFEST.MF` 的 `Spring-Boot-Version=2.7.18`（教训防范措施 2 卡点）。
- **G6（新增）**：`dependency:tree` 与 3.5.16 基线快照 diff 评审——确认 micrometer/spring-security 未混入、jakarta EE9+ 工件销账、CXF 3.5 传递链形态符合 D2 预期（坐标 jakarta、包名 javax 的 2.x 版本合法存在）。
- 基线对照：sample 维持 4 通过/13 环境阻塞（RocketMQ 9876 缺失，与降级无关），不得新增。

## 8. 明确不做（Non-goals）

不动 `spring.could.version` 拼写（已决策）；不钉 versions-maven-plugin（既有接受风险，另行跟进）；不支持 2.6-（AutoConfiguration.imports 断代，见 R6）；不改 3.5.x 主线；不新增/删减功能模块；不借机做任何"顺手重构"——降级 diff 保持纯粹，一切改进进第四位常规迭代。
