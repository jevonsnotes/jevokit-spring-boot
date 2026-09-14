# 改名类重构后本地 Maven 仓库陈旧 jar 混入单模块构建——防线测试首跑即真实拦截

- 根因类别：重复实现走偏
- 状态：已修复·已沉淀
- 相关模块：F:/mavenRepo（自定义 localRepository）、spring-boot-kit 全模块、`spring-boot-kit/server/spring-boot-kit-test-server/src/test/java/springbootkit/test/server/SpkitPrefixGuardTest.java`

## 现象 / 复现

配置前缀改名（`springbootkit` → `spkit`，2026-09-10）复测后补强回归防线，新增 SpkitPrefixGuardTest（classpath 扫描全部 `@ConfigurationProperties` 并断言前缀）。

1. 首跑 `mvn -pl server/spring-boot-kit-test-server test`（单模块、无 `-am`）即 **FAIL**：断言报 `AuditLogProperties` 前缀为旧值 `springbootkit.audit.log`——而源码同日复测已逐行确认是 `spkit.audit.log`，源码无残留；
2. 定位：单模块构建下 test-server 对 configuration 模块的依赖不走 reactor，从本地仓库解析。实际 localRepository 为自定义路径 **F:/mavenRepo**（非默认 `~/.m2`，用 `mvn help:evaluate -Dexpression=settings.localRepository` 确认），其中的 `spring-boot-kit-configuration-3.5.0.0-SNAPSHOT.jar` 为改名交付前安装的陈旧 jar（时间戳 05:50），解出 class 常量池实测含 `springbootkit.audit.log`；
3. 同命令加 `-am`（reactor 内模块源码产物优先）→ **PASS**。即全量/reactor 构建天然掩盖此问题，只有单模块或外部消费路径才暴露。

## 根因

源码已改，但本地 Maven 仓库仍保留改名前 `mvn install` 的旧 SNAPSHOT 构件。Maven 的解析顺序：reactor 内模块用源码编译产物，**不在 reactor 的依赖（`-pl` 单模块构建）回落到本地仓库** → 旧常量混入 classpath。

本质：改名重构改了"源"，没刷新"派生副本"（已安装构件）。同一标识符同时存在两份不一致的实现副本（源码 vs 仓库 jar），消费方按构建模式不同取到不同副本——与 [bug-rename-refactor-checklist-projection-loss-20260910.md](bug-rename-refactor-checklist-projection-loss-20260910.md) 同属"多处副本改一漏一"（重复实现走偏），只是副本从"清单/映射表"换成了"构建产物仓库"。

隐患能存活到防线首跑才暴露的原因：**验证路径与消费路径不一致**。全量 `mvn test` 走 reactor，本地仓库 jar 根本不参与，验证全绿；单模块 `-pl`、IDE 部分导入、外部工程引用仓库 jar 等真实消费路径才会踩中。

## 同类隐患排查

按"仓库已安装构件 vs 当前源码"对 F:/mavenRepo/spring-boot-kit/ 逐项核对（根 `mvn install -DskipTests` 刷新后复核）：

- **18 个含类模块 jar**：全部于 08:01–08:02 刷新，configuration jar 常量池实测为 `spkit.audit.log`（正向抽验）；
- **spring-boot-kit-security-all jar**（mtime 仍为 03:34，未随刷新重写）：实测为纯聚合 jar——仅 META-INF（manifest + 内嵌 pom），**零类零常量**，内容无陈旧风险；mtime 不更新是无源码模块的 maven-jar-plugin up-to-date 跳过行为，非漏刷；
- **pom 打包聚合器**（audit/others/security/parent）无 jar 仅有 pom；`spring-boot-kit-dependencies`（BOM）与 parent 的仓库 pom 时间戳与源码一致（01:08 / 02:47），当前无陈旧。注意 **BOM 在 reactor 之外**（位于仓库根的兄弟目录），未来修改 BOM 后须单独 install，否则外部工程会引用旧版；
- **同类面外推**：IDE 缓存/部分导入解析、远程私服上的同名 SNAPSHOT（若曾 deploy）同属"派生副本陈旧"面，本次未触及私服无从验证，列为环境侧待查项。

## 修复要点

从根执行 `mvn install -DskipTests` 刷新本地仓库 → 单模块 `-pl` 复跑 SpkitPrefixGuardTest **PASS**。刷新必须从根（或至少覆盖全部受影响模块）执行——局部 install 会造成仓库内新旧混合版本，比全旧更隐蔽。

## 回归用例

SpkitCryptoDefaultsTest 旁的 **SpkitPrefixGuardTest**（`spring-boot-kit/server/spring-boot-kit-test-server/src/test/java/springbootkit/test/server/SpkitPrefixGuardTest.java`）classpath 扫描段即本隐患的常驻防线：它按**运行时实际解析到的类**断言前缀，天然覆盖"仓库陈旧 jar 混入"路径。本次首跑 FAIL 恰是它在真实环境下的第一次有效拦截——防线按解析产物断言而非按源码断言，这正是它能看见仓库副本的原因。

## 防范措施

1. 凡改名/改契约类重构涉及已安装构件（常量、坐标、包名、配置键），收尾动作固定包含"从根 `mvn install -DskipTests` 刷新本地仓库"，并抽验 1–2 个关键 jar 的常量/时间戳佐证；
2. 防线索性测试优先按"运行时真实解析到的类"断言（classpath 扫描 / 反射 / Binder），而非源码 grep——源码级检索对派生副本陈旧天然失明；
3. reactor 全量验证与仓库消费路径不一致是系统性盲区：关键防线用例至少在单模块 `-pl` 模式（或外部消费方视角）跑过一次再下结论；
4. 本地仓库路径各异（本例为自定义 F:/mavenRepo），排查此类问题先用 `mvn help:evaluate -Dexpression=settings.localRepository` 确认实际仓库位置，不要只看默认 `~/.m2`。

## 关联（改名系教训族谱）

- [bug-rename-refactor-checklist-projection-loss-20260910.md](bug-rename-refactor-checklist-projection-loss-20260910.md)——**清单漏改**：规则层到操作层投影损失，pom 连锁引用漏改；
- [bug-glob-brace-pattern-silent-mismatch-20260910.md](bug-glob-brace-pattern-silent-mismatch-20260910.md)——**检索失配**：验收工具模式静默失配，"零命中"假通过；
- 本条——**仓库陈旧**：源码已改而本地仓库派生副本未刷新，单模块构建混入旧常量，全量构建掩盖。

三者构成改名类重构"改不全 / 验不出"的三个失防面：**操作层漏改、验证工具失明、派生副本陈旧**；对应防线分别是"映射表全量 grep 复核"、"朴素模式 + 阳性对照"、"刷新仓库 + 按解析产物断言"。
