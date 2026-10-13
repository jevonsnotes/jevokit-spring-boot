# boot-2-7-compat-line 变更规格（新增能力）

## ADDED Requirements

### Requirement: Spring Boot 2.7.18 依赖基线
jevokit-parent 的 Maven parent 必须为 `org.springframework.boot:spring-boot-starter-parent:2.7.18`，版本矩阵按 design §2 定稿执行：spring-cloud 2021.0.9、camunda 7.19.0（兜底 7.18.0）、CXF 3.5.11、redisson 3.50.0 + 显式 redisson-spring-data-27、spring-framework 5.3.39、tomcat 9.0.122、logback 1.2.13、snakeyaml 2.0、jackson-bom 2.15.3；无 Boot 耦合的纯 Java 库保留现版本。

#### Scenario: parent 切换后依赖树符合 EE8 形态
- **WHEN** 全量 `mvn dependency:tree` 与 3.5.16 基线快照对比
- **THEN** 基线中的 EE9+ 版本 jakarta 工件（servlet-api 6.0.0、persistence-api 3.1.0、annotation-api 2.1.1、xml.bind-api 4.0.5、activation-api 2.1.4、validation-api 3.0.2、ws.rs-api 3.0.2、inject-api 2.0.1、mail-api 2.1.5、xml.ws-api 4.0.3、xml.soap-api 3.0.2、jackson-jakarta-rs-json-provider/base 2.21.4）全部消失或落回 EE8 版本（坐标 jakarta、包名 javax 的 2.x/4.x 形态合法）；javax.cache:cache-api 1.1.1 保留；Spring Framework 为 5.3.39、Tomcat 为钉版 9.0.x

### Requirement: javax namespace 契约
全部源码（main + test）不得出现 `jakarta.*` import（含注释形态）；servlet/validation/persistence/annotation/jws/xml.ws/xml.bind 七族使用 `javax.*` 等价 API；4 处 pom 的 `jakarta.*-api` 坐标保持不变且不写版本号（由 Boot 2.7.18 parent 管理，解析到 javax 包名版本）。

#### Scenario: 源码 namespace 归零
- **WHEN** 全仓 grep `import jakarta\.`（含 java 与资源注释）
- **THEN** 零命中；core 三个 servlet wrapper、audit-log 13 处 servlet、sample jws/xml.bind/xml.ws 均为 javax 形态且编译通过

### Requirement: Java 8 编译级别（裁决 D1，已确认 2026-10-12）
2.7 线编译目标为 Java 8：`java.version=1.8` 且 `maven.compiler.release=8`（JDK 17 工具链交叉编译），main 与 test 代码不得使用 Java 9+ API；全部 runtime 依赖 jar 字节码版本 ≤52.0。

#### Scenario: release=8 编译闸门
- **WHEN** 以 JDK 17 工具链执行 `mvn clean compile`（含 test-compile）
- **THEN** 任何 Java 9+ API 使用（List.of 家族、Stream.toList、String.repeat、Files.writeString、text block 等）在编译期报错并被替换为 Java 8 等价写法，而非运行期 NoSuchMethodError

#### Scenario: 三方库字节码合规
- **WHEN** 对 runtime 依赖树全量 jar 核查 class file 版本
- **THEN** 全部 ≤52.0；超标项按版本矩阵回退值处理

### Requirement: Boot 3 专属配置键回迁
应用配置不得使用 Boot 3 专属键；已知回迁：`spring.data.redis.*` → `spring.redis.*`（sample application.yml）。

#### Scenario: sample 真实启动绑定
- **WHEN** sample 应用以 Boot 2.7.18 启动
- **THEN** redis 连接配置经 `spring.redis.*` 真实绑定生效（AC-6），无"配置静默失效回落默认值"

### Requirement: 自动装配机制延续
17 个模块继续以 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动装配（Boot 2.7 原生支持），不引入 spring.factories 双注册。

#### Scenario: 自动装配回归
- **WHEN** 库模块 516 条基线用例执行
- **THEN** 全绿，自动装配行为与 3.5.16 基线一致

### Requirement: 项目版本号形态
2.7 线版本号为 `2.7.3.0-SNAPSHOT`（首发 2.7.3.0），前两段对齐 SB 2.7 版本线、第三位承载对 2.7.2.x 血统用户的破坏性跨越、第四位归 0；jevokit-dependencies BOM 壳随 project.version 联动。

#### Scenario: 全 reactor 版本一致
- **WHEN** 20 个模块构建
- **THEN** 全部以 2.7.3.0-SNAPSHOT 发布坐标产出；README/CHANGELOG 标注"2.7.3.0 ≙ 3.5.2.2 功能全集"

### Requirement: 构建产物版本锚定（教训防范）
fat jar 的 `Spring-Boot-Version` 必须与依赖基线一致（2.7.18），作为 CI/验收卡点。

#### Scenario: MANIFEST 卡点
- **WHEN** sample fat jar 构建后检查 `META-INF/MANIFEST.MF`
- **THEN** `Spring-Boot-Version: 2.7.18`，无插件漂移（对应教训 bug-bom-import-no-pluginmgmt-plugin-version-drift 防范措施 2）
