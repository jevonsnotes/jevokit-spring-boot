# 降级 Spring Boot 3.5.16 → 2.7.18 实施任务

> 依据 design.md：版本矩阵 §2、决策 D1-D7、改造顺序 §5、风险 §6、验收闸门 §7。
> 职责分工：后端工程师执行 1-7，test-engineer-1 执行 8，文档 9 由工程侧完成后架构复核。
> 顺序约束：阶段 0 → 1 → 2 → 3 →（4/5/7 可并行）→ 6 → 8 → 9。

## 0. 前置验证（版本矩阵定稿，阻塞后续全部）

- [x] 0.1 D4 验证一：redisson 3.50.0 发布 `redisson-spring-data-27`（通过；其 pom 依赖 spring-data-redis:2.7.18 与 Boot 2.7.18 BOM 同版）
- [x] 0.2 D4 验证二：SB2 自动配置在场性（通过，机制修正：2.7.18 下唯一激活入口是 AutoConfiguration.imports 注册的 V2，spring.factories 注册的主类被 @ConditionalOnMissingClass 关闭——kit 零配置介入；Boot3 专有 API 均有 CNFE/反射双防御，字节码实证恒跳过）
- [x] 0.3 D3 实查：camunda 定稿 **7.19.0**（7.19 线仅此一版，camunda-parent 实证 spring-boot=2.7.9/Spring 5.3.22 javax；7.20.0 实证 SB 3.1.1 排除）；H2 兼容性风险升级为独立风险 R11（7.19 官方矩阵仅 H2 1.4.x）
- [x] 0.4 实查：CXF 定稿 **3.5.11**（jar 开包实证 jws-api 2.1.0=javax/jws 包、xml.bind-api 2.3.3/xml.ws-api 2.3.3/validation-api 2.0.2/annotation-api 1.3.5 同形态，D2 预期成立）
- [x] 0.5 实查：tomcat 钉版定稿 **9.0.122**（9.0.x 线最新，覆盖 Boot 2.7.18 自管 9.0.83）
- [x] 0.6 结论回写 design §2/D3/D4/D7 版本矩阵定稿，向 main 同步最终矩阵（2026-10-12 完成）

## 1. 根 pom（jevokit-parent + jevokit-dependencies 壳）

- [x] 1.1 parent 3.5.16 → 2.7.18；版本号 → 2.7.3.0-SNAPSHOT（**实际落位 21 pom 而非 2**：19 个子模块 pom parent 块硬编码旧版本号，只改根会"假绿"——Maven 从本地仓库静默解析旧 parent；eng-2 单行随动修复，架构复核全 21 pom 自洽、3.5.2.2/3.5.16 零残留，唯一遗漏为 jevokit-api/pom.xml:50 注释字样归 7.4；lessons 立条候选见 design §5 阶段 1 注）
- [x] 1.2 java.version=1.8 + maven.compiler.release=8（已落位含机制注释；release=8 实测生效：859 编译错误全量暴露）
- [x] 1.3 版本矩阵落 properties：八项钉版/回退全落位 + **redisson-spring-data-27:3.50.0 确认落 root dependencyManagement 显式条目**（架构复核通过）
- [x] 1.4 CXF 3.5.11 入 root dependencyManagement + sample 硬编码解除（无版本引用，架构复核通过）
- [x] 1.5 R5 核查通过：bcprov/bcpkix-jdk15on 全树零引用，无需钉 1.70；BC 统一 jdk18on:1.86（R5 关闭）
- [x] 1.6 release=8 报错清单产出：**最终 110 处**（Java 9+ API 108 + Spring 6 API 2 处 test），含手工扫描双盲区：instanceof 模式匹配 1 处 main（api 模块 EncryptResponseAdviceAdapter.java:67，rg 正则 && 交集解析缺陷）+ @Nested static 5 处（core SecurityUtilTest，Java 16 前 static 非法）；编译器盲区实证：test 文件 import 失败时 javac 跳过方法体检查——88 处 List.of 以手工清单为准，阶段 2-4 修完 main 后重跑编译自动校验补全
- [x] 1.7 确认 surefire 2.22.2 下根 pom 的 `@{argLine}`/UTF-8 argLine 配置解析正常（先跑任一模块测试）——**已完成（evidence/20261012-surefire-check.log）：surefire 2.22.2 下 @{argLine} 延迟替换与 UTF-8 argLine 解析正常，136 用例零失败（AC-7 前置实证同源）**

## 2. L0 jevokit-core

- [x] 2.1 servlet 文件 jakarta→javax（已完成，eng-1）：wrapper 三件套（11 处，含 RequestContentCachingRequestWrapper:47 构造器——编译基线唯一告警点）+ **HttpUtil.java（第 4 个 servlet 文件，执行中补漏，main 已确认）**
- [x] 2.2 SqlUtil 的 persistence 注解 import → javax；jakarta.servlet-api 坐标核验解析到 4.0.4（D2，不写版本号）——**已完成（core 批次，eng-1）：随 2.4 core 单模块 install 98/98 全绿收口；D2 坐标解析实证 servlet-api 4.0.4 provided（见 2.4 落位清单）**
- [x] 2.3 core 测试 Java 9+ API 回退（eng-2 清单：约 26 处）+ test jakarta import（SqlUtilTest 等 2 条，core 内口径）→ javax。**语义保真注意（eng-1 已执行，回归重点）**：不可变语义处用 Collections.unmodifiableX/singletonX 等价保持（SingletonList/UnmodifiableSet 与 JDK17 ImmutableCollections 同走 SecurityUtil 降级分支，行为等价）；SecurityUtilFilterImmutableVerificationTest 对抗性覆盖点（变体①②③）全部保留——test-engineer AC-2 回归时重点关注该文件。**已完成（core 批次）：随 2.4 core 98/98 全绿收口，语义保真口径如上全数执行、对抗性文件全保留（AC-2 回归复核通过）；终校验随 7.1 全量 test-compile 21/21 SUCCESS**
- [x] 2.4 单模块 `mvn clean install` 绿——**core 98/98 全绿（2.4 完成，eng-1）**：D2 EE8 坐标落位实证（servlet-api 4.0.4 provided / persistence-api 2.2.3 / annotation-api 1.3.5 / spring-core 5.3.39 / tomcat 9.0.122 / release=8）；14 处补修提炼的 API 修复族谱已入 design §4 第 9 条（后续 Wave 指引）

## 3. L1 jevokit-autoconfigure

- [x] 3.1 spring-cloud-context 随 BOM 2021.0.9 回落验证——**依赖树实证（eng-1）：spring-cloud-context 3.1.8（2021.0.x 2.7 兼容线终版）+ spring-boot 2.7.18 + spring-context 5.3.39，版本线解析正确**；@AutoConfiguration 复核结论：autoconfigure 模块实际未用该注解（@Configuration + .imports 形态），main 使用面仅 audit-log 一处待 Wave 顺带确认（design §4 第 3 条已修正表述）
- [x] 3.2 单模块 `mvn clean install` 绿（eng-1；语法面 6 类零命中）；AutoConfiguration.imports 文件不动（回归在阶段 8）

## 4. L2 Wave A：main 含 jakarta 的 11 个 starter（可并行分派；**并行守则（eng-3 并发编辑实证后增补）：按文件划分归属，同一文件禁止双实例作业，dispatch 时逐文件点名责任人**）

- [x] 4.1 security-xss / path-traversal / sql-inject：拦截器 javax 化完成（eng-3，3 文件 6 处；test 8 处 List.of 由并行实例完成、eng-3 补 2 处 String.repeat，无覆盖冲突）
- [x] 4.2 api：7 文件 11 处完成（eng-3，validation + servlet）
- [x] 4.3 audit-log：5 文件完成（eng-3，含 AuditLogDto persistence→javax + test 泄漏回归测试工厂/toList 回退）
- [x] 4.4 desensitization：6 文件 35 处完成（eng-3：main DesHelper PostConstruct→javax.annotation + test 33 处 Java 9+ 回退）；**D2 附件核验通过：jakarta.annotation-api 不写版本在 2.7.18 解析 1.3.5（javax 包形态）**
- [x] 4.5 redis：源码 javax 化 + pom 适配全部完成（eng-3）：starter exclusion 排 `redisson-spring-data-35` + 显式声明 `redisson-spring-data-27`（不写版本走 root depMgmt 3.50.0）；**依赖树双断言通过**（-35 零在场、-27:3.50.0 compile 在场、starter/redisson 3.50.0 正常在树）；附加探明：core+autoconfigure `-am install` 全绿——D4 定稿完整落地。**统一编译补充收口（2026-10-12）**：首轮统一编译暴露 spring-data-redis 3.x API 漏项 2 处——JevokitRedisConfiguration.java:115-116/172-173（修复前快照行号 114/171）Jackson2JsonRedisSerializer 双参构造器 `(ObjectMapper, Class)` 为 3.x 新增，2.7.18 的 spring-data-redis 2.x 无此签名；**按 2.7.18 字节码实查定性后改单参构造 + setObjectMapper 注入**（两处均带机制注释防回改），编译通过。该漏项属阶段 2-4 逐模块编译闸门的盲区外项（单模块闸门时点 core 依赖的 spring-data-redis 已随根 pom 回落，唯 redis 模块自身 API 用点在统一编译全量 test-compile 才暴露），架构复核定性为 4.5 收口补充而非新风险
- [x] 4.6 nogen：3 文件 5 处完成（eng-3：persistence po/vo + servlet）；**D2 附件核验通过：jakarta.persistence-api 不写版本在 2.7.18 解析 2.2.3**；依赖 redis-starter 随动（redis pom 适配完成后联编）
- [x] 4.7 excel：1 文件 2 处完成（eng-3，resolver servlet）——无测试模块专项：编译验证随统一编译编排 + 依赖树校验；**追加冒烟点（eng-3）：xalan:serializer 显式依赖在 Boot 2.7/JDK17 下 TransformerFactory 查找序冲突面，入编译后冒烟清单**
- [x] 4.8 rocketmq：1 文件 2 处完成（eng-3，annotation）——无测试模块专项：D5 升级为官方原生配套实证（parent POM 钉 SB 2.7.18/rocketmq 5.2.0），2.2.2 回退预案归档留档
- [x] 4.9 okhttp：3 文件 12 处完成（eng-3，含测试侧）；mockwebserver 回归随 2.4 编译验证
- Wave A 批注（eng-3）：语义核对零踩雷（无 Set.of；多键 Map 用 LinkedHashMap/HashMap 时核实被测代码不依赖顺序；Files.writeString→Files.write+getBytes 显式 UTF-8 字节等价；readAllBytes→hutool IoUtil.read 经 core 传递可达）；7 模块终扫零残留；javax import 在旧 Boot 3.5 树上不可编译属预期——根 pom 已落位（阶段 1 复核通过），统一编译验证（2.4）已放行

## 5. L2 Wave B：main 零源码改动的 4 个 starter

- [x] 5.1 web-fixed / code-inject / jasypt：编译随动验证——**统一编译 21/21 SUCCESS 零源码改动收官（eng-2 复跑实证：web-fixed 2.013s / code-inject 3.902s / jasypt 2.039s）**；改动面抽验（架构复核）：三模块 git diff 仅 pom.xml 版本随动（code-inject 另有 1 个 test 文件属 7.1 横切清单），main 源码零触碰，与 design「main 零源码改动」预期一致；web-fixed 树 spring-webmvc/spring-web:5.3.39 正确
- [x] 5.2 workflow：camunda 7.19.0 随动编译通过；**R11 已前置验证通过（eng-1 隔离探针引擎级证伪，四项全过+fat jar 硬证据，决策点取消、预案归档）**——本项剩余=统一编译随动 + AC-2 sample E2E 真数据源作最终集成闸门；workflow 模块 2 基线用例不触达 H2/DataSource（模块内测不出 R11，探针/E2E 是唯一路径，已记 design R11 供后续维护者）；starter 耦合问题才走 D3 兜底 7.18.0。**pom 适配落位（eng-3）**：workflow pom.xml:46-62 与 redis 模块同模式——redisson-spring-boot-starter exclusion 排 -35 + 显式 -27（不写版本走 root depMgmt，注释与 redis 模块逐字一致）；依赖树实证修复后 -35 零在场、-27:3.50.0 在场、spring-data-redis 2.7.18 自洽（evidence tree log 中 -35 在 workflow 树在场系快照时点中间态，修复后已消，终验树证据按 8.13 重跑）
- [x] 5.3 security-all（L3 聚合）：编译随动通过（0.110s，pom-only 聚合壳符合预期）+ 5 个被聚合 starter 联编全绿

## 6. sample（L4 收尾）

- [x] 6.1 CXF 4.1.2 → 3.5.11（引 root 管理）；jws/xml.ws import → javax 完成（eng-2）；**计划外全扫增益：点名清单外抓到 4 文件 6 处 jakarta 残留（SecureApi validation+servlet、ExcelApi/ExcelVo validation、TestVo validation×2）同口径修复，sample java 层 jakarta 终扫 ZERO**——"点名清单不可信、收尾全字扫"已固化 design §4 第 1 条
- [x] 6.2 TestVo xml.bind → javax 完成；sample pom 两件落位（eng-2）：`jakarta.jws:jakarta.jws-api:2.1.0` 显式版本（dependency:get 实拉 + jar tf 字节级核验 javax/jws 命名空间）+ `jakarta.xml.bind-api` 无版本走 parent 2.3.3；带"D2 常规/D2 例外"注释防误改
- [x] 6.3 application.yml `spring.data.redis.*` → `spring.redis.*`（207-211 行）——**落位实证（架构复核）：yml:207 已为 `redis:` 顶层键（host/port 6379），全仓 yml/properties `data:` 键 grep 零命中**（R10 销账）；真实绑定生效由 6.6 AC-6 闭环
- [x] 6.4 完成（eng-2）：NoGenApi:92 Stream.toList 清除——**main 侧 110 清单全部清零**；两段已注释 Java 17 text block 死代码选择删除（解除注释即断的陷阱不如清）；NogenE2ETest text block+formatted → 等价拼接
- [x] 6.5 logback-spring.xml 复核 logback 1.2 语法兼容——**1.2.13 兼容实证完成（eng-3/sample 启动渲染验证）：springProperty 占位、%green/%cyan 彩色、SizeAndTimeBasedRollingPolicy 滚动策略在 1.2.13 下全部正常渲染**；回归由 AC-2 的 DesensitizedLogFilterTest 覆盖
- [x] 6.6 sample 可启动冒烟——**AC-6 通过（eng-2 fat jar 真启动）**：Boot 2.7.18 banner + Tomcat 9.0.122；`spring.redis.*` 绑定生效、启动日志无 `spring.data.redis` 失效警告；Redisson 实连 6379 成功；缓存读写闭环通过；启动 0 ERROR。RocketMQ 缺环境处置：以排除 MQ 自动配置方式绕开 9876 环境阻塞完成启动验证（MQ 基线"4 通过/13 环境阻塞"口径不变，回归仍按 AC-2 基线对照）

## 7. 测试横切（与 4/5 并行，按模块清单切分）

- [x] 7.1 全仓 test Java 9+ API 回退：以 1.6 清单（110 处）+ design §4 第 9 条族谱为指引——**实施面收官（2026-10-12）：前置批 4 模块 23 处（eng-2）+ core（随 2.4）+ desensitization 33 处（随 4.4）+ security 三兄弟（随 4.1）+ sample 测试域 6 处存量修复（readAllBytes→StreamUtils、orElseThrow()→orElseThrow(NoSuchElementException::new)、MockPart 四参→三参+setContentType、Map.of→singletonMap、匿名钻石×2→显式泛型）**；**终校验完成：修复后复跑全量 test-compile 21/21 SUCCESS 零错误（约 1:14）——release=8 编译期拦截零命中，110 清单全清**；语义核对口径不变（空集合 `Collections.emptyList/emptyMap`、只读 fixture `Arrays.asList/singletonList`、双键 Map HashMap 构建且核实被测代码不依赖顺序、StrUtil.repeat 行为等价）；grep 族零残留新鲜证据（jakarta 7 族 + Java 9+ 语法保险层）按 8.13 规范随终验产出，本项证据形态为编译期拦截
- [x] 7.2 Mockito 5.x / AssertJ 3.24+ 专有 API 扫描与回退（版本线随 parent 回落 4.5.1/3.22.0）——**全部模块扫描完成（eng-2）：结论预期零改动，清单已交付 main；javap 对真实 3.22.0/4.5.1 工件全量验证，结论可信度高**；sample 收尾后复跑编译零错误佐证无漏项；grep 终扫证据随 8.13 归档
- [x] 7.3 test 侧 jakarta import 6 处（SqlUtilTest、audit-log、api 测试）→ javax（若未在 4.x 完成）——**已完成（各模块批次内完成，eng-1/eng-2/eng-3）：随 2.x/4.x 模块批次收口；AC-3 终扫全仓 java 任意 jakarta 字样零命中（evidence/20261012-acceptance-final-sweep.log，扫描时点 19:58 晚于末批源码改动 19:47）**
- [x] 7.4 杂项清理：DesHelper.java:26 注释内 jakarta 字样（AC-3 完整性）；~~仓库根未跟踪的 jaxbapi.jar/jwsapi.jar~~（已完成，eng-3 确证系其下载产物、已自仓库根删除）；jevokit-api/pom.xml:50 注释内"spring-boot-dependencies 3.5.16"字样（阶段 1 遗留注释陈旧）；**统一编译收口复核新增 2 处同族注释残留（架构复核 2026-10-12 登记）：① jevokit-core/pom.xml:68 注释"版本经 jevokit-dependencies 导入的 spring-boot-dependencies 3.5.16 管理"（与 api:50 同族同文）；② 根 pom.xml:429 surefire 注释"版本由 spring-boot-starter-parent 3.5.16 管理（3.5.6…）"——降级后事实性陈旧，现 parent 2.7.18 管理 surefire 2.22.2（evidence surefire-check.log 已实证），注释需随事实更新**；jevokit-dependencies/pom.xml:6"3.5.2.0 及之前"为版本历史说明属合法保留不改。两处新残留不阻塞编译（注释字样），与 api:50 同批清理——**已完成（eng-2 末批 2026-10-12：core:68 与根 pom:429 两处，evidence/20261012-final-batch-comment-cleanup.md；架构 2026-10-13 补清 api:50 同款陈旧注释"3.5.16 管理"→"2.7.18 管理"，纯 XML 注释零构建影响，补清记录已并入该 evidence 文件）**

## 8. 验收（test-engineer-1，对照 AC-1~8 + G4~G6）

- [x] 8.1 AC-1 编译不劣于基线——**PASS（终验 2026-10-12 18:36）**：全量 clean compile 21/21 SUCCESS 零错误（evidence/20261012-acceptance-compile.log）；core wrapper 编译告警源（RequestContentCachingRequestWrapper jakarta 构造器，3.5.16 基线唯一告警点）已随 2.1 javax 化消除，不劣于基线
- [x] 8.2 AC-2 库模块用例全绿（对照 baseline-test-3.5.16.log 逐模块计数）——**PASS（test-engineer-1 终验 2026-10-12），基线口径显式变更两项（main 拍板，最终验收汇报向用户披露）：库模块 516→519（DEF-1 回归防线 DesHelperLogPathNoContainerTest +3）；sample 12 error/5 通过 → 17/17 全绿（application-local.yml 入库、MQ 排除机制，测试基建改善非劣化修复）**
- [x] 8.3 AC-3 源码（含注释）零 `import jakarta.`——**PASS（终扫 2026-10-12 19:58:05，晚于末批源码改动 19:47:25，符合"末批改动后重跑新鲜证据"规范）**：jakarta 7 族 import + 全仓 java 文件任意 jakarta 字样（含注释）grep 零命中（evidence/20261012-acceptance-final-sweep.log）
- [x] 8.4 AC-4 parent=2.7.18 生效——**PASS（终验）**：help:evaluate 单项未单独留档，生效事实由全量证据交叉实证——全树 spring-boot 2.7.18/spring-framework 5.3.39/jackson 2.15.3 + 2.7 parent 插件管理线（compiler 3.10.1/surefire 2.22.2，evidence/20261012-acceptance-g6-tree-diff.log、test-final2.log）+ fat jar MANIFEST `Spring-Boot-Version=2.7.18`（G5 佐证）
- [x] 8.5 AC-5 依赖树零 EE9+ jakarta 工件——**PASS（终验）**：design §2-D2 销账清单 14 项逐条核销（EE9+ 全部消失或落回 EE8 版本，销账表见 evidence/20261012-acceptance-g6-tree-diff.log，全量树 deptree-full.log/deptree-runtime.log）；EE8 形态合法保留清单（activation 1.2.2/annotation 1.3.5/jws 2.1.0/persistence 2.2.3/servlet 4.0.4/validation 2.0.2/ws.rs 2.1.6/xml.bind 2.3.3/xml.soap 1.4.2/xml.ws 2.3.3 + javax.cache 1.1.1 存量）与 D2 对齐
- [x] 8.6 AC-6 sample 真实启动，spring.redis.* 绑定生效——**PASS（eng-2 首验 + test-engineer-1 终版复跑，fat jar 真启动 2026-10-12 20:02）**：Boot 2.7.18 + Tomcat 9.0.122、Redisson 3.50.0 实连 localhost:6379、`spring.redis.*` 绑定生效、启动 0 ERROR（终版 evidence/20261012-acceptance-ac6-final-boot.log；首验 ac6-fatjar-boot.log；sample-repro.log 为 -pl 单模块复构建）
- [x] 8.7 AC-7 surefire 2.22.2 下 72 份报告全严格 UTF-8、argLine/jacoco 生效——**PASS（终验）**：surefire 2.22.2 + @{argLine} 延迟替换 + jacoco 0.8.12 注入全模块生效（每模块 "argLine set to -javaagent…" 日志，evidence/20261012-acceptance-test-final2.log）；参数族（UTF-8 报告编码）生效前置实证 evidence/20261012-surefire-check.log（136 用例零失败）；72 份报告逐份编码检查记录以 test-engineer-1 终验判定表为准，本项按其 PASS 判定落账
- [x] 8.8 AC-8 根 install 刷 F:\mavenRepo 后 `-pl` 单模块抽验——**PASS（终验 2026-10-12 19:59:20）**：根 install 21/21 BUILD SUCCESS 刷库（evidence/20261012-acceptance-install-final.log；全量含 sources/javadoc attach 见 install.log）+ `-pl jevokit-sample` 单模块抽验解析 2.7.3.0-SNAPSHOT 新鲜工件构建成功（evidence/20261012-acceptance-sample-repro.log）——陈旧 jar 防线（教训 bug-rename-stale-local-repo-jar）通过
- [x] 8.9 G4 runtime 依赖全量 jar 字节码 ≤52.0 核查——**PASS（终验实测）：321 项 compile/runtime 工件主代码字节码全 ≤52.0；module-info/MR-JAR 版本化类误报已甄别排除；design §2 矩阵 [1] 类保留库（hutool/jsoup/POI/redisson/netty/BC 等）全数合规，R2 残余风险正式销账**
- [x] 8.10 G5 sample fat jar MANIFEST `Spring-Boot-Version=2.7.18`——**PASS（终验判定 + 架构落账时实读复核）**：终版 fat jar META-INF/MANIFEST.MF 实读 `Spring-Boot-Version: 2.7.18`（jevokit-sample-2.7.3.0-SNAPSHOT.jar）；manifest 检查输出见 evidence/20261012-acceptance-g5-manifest.log（test-engineer-1 补档 2026-10-12 20:18，含 repackage 证据与 JarLauncher/Start-Class/Boot-Classes/Layers-Index 全字段判定），evidence/ 共 16 份
- [x] 8.11 G6 依赖树 diff 评审——**PASS（终验，evidence/20261012-acceptance-g6-tree-diff.log）**：micrometer 基线 38 处结构性消失（Spring 5.3.39 无此传递链，非依赖丢失）；spring-security-crypto 6.5.11×18 → 5.7.11×18 位置/数量一致（版本随管理线回落，非混入）；CXF 全链 3.5.11 传递形态符合 D2 预期；sample 树 jakarta.jws-api 2.1.0 在场；redisson-spring-data-27:3.50.0 四树在场、-3x/-35 零命中；camunda 全家 7.19.0 无残留
- [ ] 8.12 可选加固：真实 JDK 8 运行时（Temurin 8 容器）跑 sample fat jar 冒烟——**终验标注"未执行-环境不具备"（可选项，evidence/20261012-acceptance-8.12-skip-note.log），留观入遗留清单（本文件第 10 节①）**；release=8 交叉编译 + G4 字节码全合规双防线已覆盖编译/字节码层，仅剩运行时层实证缺位
- [x] 8.13 excel/rocketmq 无测试模块专项：编译 + 依赖树校验留档复核——**PASS（终验，evidence/20261012-acceptance-final-sweep.log"8.13 终扫 PASS"）**：excel SUCCESS[1.287s]/rocketmq SUCCESS[2.352s]（compile.log）+ 终版 install 复确认 + 两模块树 jakarta 全 EE8（deptree-full.log）+ 两模块 src grep 零命中；**全局终扫新鲜证据同批归档**：jakarta 7 族 + 任意字样零命中、Java 9+/16 语法保险层零残留（repeat 10 处甄别为 Hutool StrUtil 误报）、扫描时间戳 19:58:05 晚于末批改动 19:47:25，符合 eng-2 提案、架构采纳的终扫证据规范
- [x] 8.14 验收期缺陷 DEF-1 处置（eng-2 根因定性 + 修复已落码，架构复核 2026-10-12）：**根因=脱敏模块"日志路径容器依赖"存量反模式被 2.7 线时序暴露**（logback 过滤器任意线程调 SpringUtil.getBean——刷新期 main 持单例锁建 bean（redisson），异步线程（netty 回调）打日志阻塞在锁上，main 所等 future 完成方恰为被阻塞线程即三方循环等待、JVM 挂起），**非降级引入回归、非 redis/redisson 配置问题**；修复=静态引用两阶段发布（@PostConstruct 发布配置类引用 + ContextRefreshedEvent 经 getBean 发布代理实例保 AOP 语义 + 日志链路零容器依赖、未发布 fail-open，与"脱敏失败绝不阻断日志输出"既有语义一致），专项回归 DesHelperLogPathNoContainerTest 覆盖（就绪无容器/未发布 fail-open/发布后静态实例通路）；边界澄清：V1/V2 分叉不成立（V2 extends V1，栈帧类名必显 V1），fat jar 与测试上下文 bean 源均确认 V2，主应用成功 vs 测试挂起为同窗口时序竞态（测试 fork 多上下文、static ready 跨上下文残留 true）。**架构复核附注（非阻塞，随 kit 规范沉淀闭环）**：static 就绪状态无 ContextClosedEvent 清理联动，spring-test 多上下文场景残留态为已知边界（生产单 context 不触达，异常被 fail-open 兜底），配条款入 design §4 第 11 条。DEF-1 为缺陷修复（有挂起实害）非顺手重构，允许入本变更 diff。**遗留议题闭环（main 2026-10-12 拍板）**：application-local.yml 入库 + 默认套件 MQ 排除采纳——"12 个 MQ 阻塞 error 清零"定性为**基线口径变更**（测试基建改善，非劣化修复），AC-2 对照基线"4 通过/13 环境阻塞"随之更新，口径变更在最终验收汇报向用户披露；配套的套件行为说明归 9.2 定稿批待办③。**终验同类点排查结论（test-engineer-1，架构采纳落档）**：全仓其余 getBean 回调点（审计链 6 处/MQ listener 1 处/WSConfig 1 处）时序上均无刷新窗口暴露、可接受——与 eng-2 低危定性互证，统一加固仍归后续常规迭代（design §4 第 11 条）

## 9. 文档与发布准备

- [x] 9.1 README：JDK/Spring Boot 徽章（JDK 8+ / Spring Boot 2.7.18）、环境要求、BOM 版本链说明（jevokit-dependencies → jevokit-parent → starter-parent 2.7.18）、2.7 线与 3.5 线并行维护说明、"2.7.3.0 ≙ 3.5.2.2 功能全集"标注——**出稿完成（eng-3），架构复核通过：徽章（release 2.7.3.0-SNAPSHOT / Boot 2.7.18）、双线并行维护节、功能全集标注、BOM 链、构建工具链（JDK 17 交叉编译）+ 运行环境（JDK 8+）分层表述齐备，与 design D1/D6 一致，可直接定稿**；**定稿确认（2026-10-12 定稿批，架构）：工作区 jevokit-spring-boot/README.md 即终版、零修改定稿**；发布时随 release 动作把徽章 SNAPSHOT 后缀去除（非验收事项）
- [x] 9.2 CHANGELOG：2.7.3.0 条目（破坏性变更 + 双受众迁移指引 + 已知行为差异 + 架构备忘节）——**出稿完成（eng-3），架构复核通过（主体可定稿），定稿写入前带 3 个待办（main 2026-10-12 拍板采纳）：① CHANGELOG:32"旧坐标映射表待架构复核后补充"——映射表实证基础架构复核已核（前身定制版本与 jevokit 双侧 CONFIG_PREFIX 全量对照 9 模块：security.xss/sql-inject/path-traversal/code-inject、audit.log、desensitization、nogen、api、ok.http 全部严格首段替换为 jevokit 零反例；ok.http 三段形态两边一致，映射表须显式点名防 jevokit.okhttp.* 误写），正文可按定稿基线（实证稿+首段替换规则+ok.http 三段特例）于阶段 8 验收通过后写入；② CHANGELOG:33 迁移指引补"旧配置前缀首段统一替换为 jevokit（其余段不变）"一句——前身血统用户（迁移指引第一节受众）存量前缀首段为前身项目名、其余分段与 jevokit 侧一致（如 security.xss.*），与映射表同批落稿；③ 测试套件行为说明（架构提议、main 采纳）：一句说明 sample 默认套件的 @ActiveProfiles("local") + application-local.yml MQ 排除机制（下游用户可见的套件行为），与映射表同批落稿**。~~正式定稿等阶段 8 验收通过~~→**定稿条件已满足（阶段 8 终验 AC-1~8 + G4/G5/G6 全 PASS，test-engineer-1 2026-10-12），定稿批可执行**→**定稿写入完成（架构 2026-10-12，工作区 CHANGELOG.md 同树编辑）：① 映射表 9 行（jevokit 侧 CONFIG_PREFIX 逐条实读复核）+ 首段替换规则 + ok.http 三段特例（显式点名防 jevokit.okhttp.* 误写）入迁移指引第一节；② "旧配置前缀首段统一替换为 jevokit（其余段不变）"句并入前缀核对条（旧坐标/旧前缀字样按用户偏好脱敏，映射表旧前缀列以功能域通称呈现）；③ "测试套件行为说明（下游用户可见）"节落稿（@ActiveProfiles("local") + application-local.yml 排除 RocketMQAutoConfiguration 机制、@Tag("mq") 全量口径）；另补"修复"节（DEF-1，下游用户行为无变化 + fail-open 语义保持）与"redisson starter 双注册装配形态"备忘条（V1 spring.factories + V2 imports 双注册、@ConditionalOnMissingBean 背离后 V2 单 bean 生效，架构对 3.50.0 jar 实读 spring.factories/imports/V1 注解佐证）**
- [x] 9.3 架构备忘归档：R6 边界（2.6- 降级将断 AutoConfiguration.imports）、D2"坐标 jakarta/包名 javax"EE8 形态说明——**出稿完成（eng-3，以 CHANGELOG"架构备忘（维护者查证）"节落稿），架构复核通过：R6 与 design 风险节一致、D2 六坐标版本（servlet 4.0.4/persistence 2.2.3/annotation 1.3.5/jws 2.1.0/xml.bind 2.3.3/validation 2.0.2）与 design D2 表+例外完全对齐，可直接定稿**；可选增强（非阻塞）：前身版本前缀对照结论（首段替换规则+ok.http 三段形态）可随映射表一并入备忘供后续维护者查证；**终验追加（test-engineer-1 建议，架构采纳）："日志/回调线程路径触碰容器"反模式条目入备忘**（DEF-1 根因 + 静态引用两阶段发布 + fail-open 定式，指向 design §4 第 11 条全文）——**已落 CHANGELOG"架构备忘（维护者查证）"节（2026-10-12 定稿批）**
- [ ] 9.4 release profile 发布演练（-Prelease 干跑签名与 central-publishing 配置，不实际发布），确认 gpg 3.2.7/central-publishing 0.8.0 在新基线可用——**main 2026-10-12 拍板：归遗留清单（本文件第 10 节⑥），不本批安排；触发条件=用户确定发布计划时执行**

## 10. 降级后遗留跟进清单（架构落档 2026-10-12，均不阻塞本变更验收）

> 定位：本变更验收范围外的已知边界与既定接受风险的统一台账，供后续常规迭代排期；各项均已评估当前风险等级，无实施时点压力。

- [ ] ① **真实 JDK 8 容器冒烟**（对应 8.12，可选项未执行——环境不具备，evidence/20261012-acceptance-8.12-skip-note.log）：具备 Temurin 8 容器环境时对 sample fat jar 补一次 `java -jar` 冒烟。补偿防线已全过：release=8 交叉编译（编译层）+ G4 字节码 321 项全 ≤52.0（字节码层），残余仅 JDK 8 JVM 运行时行为差异（JIT/类校验边界），**低风险留观**。
- [ ] ② **"运行期线程 getBean"同类加固 + DesHelper static 状态生命周期条款**（design §4 第 11 条）：AuditLogFilter:90、BzNoHelper:402、OkHttpUtil:833-879 等 6 处"运行期线程 getBean"（eng-2 清单在案；终验同类点排查已定性——时序上均无刷新窗口暴露、不构成等待环，**低危**），建议归后续第四位常规迭代统一抽静态引用模式（可派 code-refactorer）；随规范沉淀补"DesHelper static 就绪状态无 ContextClosedEvent 清理联动"配条款（spring-test 多上下文残留态为已知边界，生产单 context 不触达、fail-open 兜底）。
- [ ] ③ **spring.could.version 属性名拼写修正**（root pom，`could` → `cloud`）：既有拼写遗留（design §2 矩阵已注明"本变更不动"以保持降级 diff 纯粹），后续单独提交修正——注意属性名是公共 API 面（下游可 override），修正时需评估兼容性说明。
- [ ] ④ **versions-maven-plugin 无版本漂移**：既有接受风险（教训 bug-bom-import-no-pluginmgmt-plugin-version-drift 中核定接受，仅本地用途），R4 备查；后续单独跟进钉版。
- [ ] ⑤ **jevokit-dependencies/pom.xml:6"3.5.2.0 及之前"历史说明保留**：版本历史说明属合法保留，**不清理**（7.4 清理清单已显式排除）；列档仅为防止后续维护者误判为注释残留。
- [ ] ⑥ **release profile 发布演练**（自 9.4 移入，main 2026-10-12 拍板）：`-Prelease` 干跑签名与 central-publishing 配置（不实际发布），确认 gpg 3.2.7 / central-publishing 0.8.0 在 2.7.18 新基线可用；**触发条件=用户确定发布计划时执行**，发布节奏由用户决定。
