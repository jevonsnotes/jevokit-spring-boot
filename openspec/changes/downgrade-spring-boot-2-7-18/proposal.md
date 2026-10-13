# 降级 Spring Boot 3.5.16 → 2.7.18（2.7 兼容线）

## Why

jevokit-spring-boot 当前唯一发布线基于 Spring Boot 3.5.x / JDK 17。存在一批因运行环境（JDK 8、容器中间件、集团基线）无法升级 Boot 3 的使用方，其参照基线是历史上的前身定制版本 2.7.x 线（Spring Boot 2.7.18 适配线，项目版本 2.7.2.3-SNAPSHOT）。为满足这部分用户，需要在当前 2.7.x 分支上把整套组件库降级回 Spring Boot 2.7.18，产出一条与 3.5 线功能全集对齐（≈3.5.2.2）的 2.7 兼容线。

## What Changes

- **父级与版本号**：`jevokit-parent` 的 Maven parent 由 `spring-boot-starter-parent:3.5.16` 切换为 `2.7.18`；项目版本号按四段式方案定为 `2.7.3.0-SNAPSHOT`（首发 2.7.3.0，已确认 2026-10-12，见 design 决策 D6）。
- **编译级别（裁决 D1，已确认 2026-10-12）**：`java.version` 由 17 降为 **1.8**，并显式设置 `maven.compiler.release=8` 防止 JDK 17 工具链交叉编译时的 Java 9+ API 静默兼容炸弹。构建仍可用现有 JDK 17 工具链，无需本机 JDK 8。
- **代码 namespace 回退**：全仓 `jakarta.*` → `javax.*`（main 33 文件 71 处 + test 4 文件 6 处，覆盖 servlet/validation/persistence/annotation/jws/xml.bind/xml.ws 七族；已逐 API 核对 javax 侧同名同签名，纯机械替换）。
- **第三方依赖版本矩阵整体回退**（详见 design 版本矩阵）：spring-cloud 2025.0.3→2021.0.9、camunda 7.23.0→7.19.x（7.20 起转 SB3/jakarta，不可用）、CXF 4.1.2→3.5.x（sample）、spring-framework 显式钉 5.3.39、tomcat 钉 9.0.1xx、logback 钉 1.2.13、snakeyaml 钉 2.0、jackson-bom 钉 2.15.3；hutool/jsoup/POI/redisson/okhttp/guava 等无 Boot 耦合项保留现版本（Java 8 兼容性逐项核验）。
- **pom 坐标策略（对工程扫描结论的修正）**：4 处 `jakarta.*-api` 坐标**保持不动**——Boot 2.7.18 parent 的 dependencyManagement 恰好管理这批 EE8 过渡坐标（`jakarta.servlet-api:4.0.4`、`jakarta.persistence-api:2.2.3`、`jakarta.annotation-api:1.3.5` 等，坐标名 jakarta、字节码包名 javax），无需换 `javax.*` 坐标、无需写版本号。
- **配置键回退**：sample `application.yml` 的 `spring.data.redis.*` → `spring.redis.*`（Boot 3 专属键，此前扫描漏报，本方案已纠正）。
- **测试侧适配**：约 101 处 Java 9+ API（`List.of` 家族 88、`Stream.toList` 6、`String.repeat` 5、`Files.writeString` 1、text block 1）机械回退；JUnit 5.8.2 / AssertJ 3.22 / Mockito 4.5 版本线随 parent 回落，需扫描测试代码专有 API。
- **自动装配机制不动**：17 个模块的 `AutoConfiguration.imports` 注册文件 Boot 2.7.18 原生支持，无需回退 spring.factories，仅回归验证（再往 2.6- 降则会全断，见 design 风险节）。

## Capabilities

### New Capabilities
- `boot-2-7-compat-line`: Spring Boot 2.7.18 / JDK 8 兼容线的依赖基线、javax namespace、编译级别与版本号契约——本变更的主体能力。

### Modified Capabilities
<!-- 本变更是平台基线移植，不改变 jevokit.* 配置契约与组件功能语义，无既有 spec 级行为变化 -->

## Impact

- **代码**：19 个模块中 13 个含 jakarta import（core 三个 servlet wrapper 是全生态复用点，修复后 web-fixed/code-inject/security-all/workflow 四模块 main 源码零改动）；测试代码全量 API 线回退。
- **依赖**：parent 管理面整体回退（Spring Framework 6.2→5.3、Tomcat 10.1→9.0、Jackson 2.19→2.13、Logback 1.5/SLF4J 2→1.2/1.7、Hibernate Validator 8→6.2、JUnit/Mockito/AssertJ 测试线回落）；影响面与对冲钉版见 design「parent 回退受影响面」。
- **构建**：surefire 3.5.6→2.22.2（`@{argLine}` 延迟替换仍有效）、compiler 3.14.1→3.10.1；自有钉版插件（jacoco 0.8.12、source 3.3.1、javadoc 3.11.3、gpg 3.2.7、central-publishing 0.8.0）不受影响；sample 的 `spring-boot-maven-plugin` 版本经真实 Maven parent 继承自动锚定 2.7.18，历史「BOM import 不传 pluginManagement 插件漂移」教训在本结构下不成立，但保留 fat jar MANIFEST `Spring-Boot-Version` 卡点作为回归闸门。
- **文档**：README（JDK/Spring Boot 徽章、环境要求、BOM 版本链说明）、CHANGELOG（2.7.3.0 条目，标注与 3.5.2.x 功能代际对应关系）。
- **不做**（Non-goals）：不改 `spring.could.version` 属性拼写遗留（已决策，另行处理）；不钉 versions-maven-plugin 版本（既有已接受风险，另行处理）；不向 2.6- 兼容；不动 3.5.x 主线。
