# 子模块 pom parent 块硬编码旧版本号——配置翻新后全量编译"假绿"（BUILD SUCCESS 但新配置零生效）

- 根因类别：重复实现走偏（多处副本改一漏一；Maven 静默回落机制放大）
- 状态：已修复·已沉淀
- 相关模块：jevokit-spring-boot 全部 21 个 pom（根 + jevokit-dependencies 壳 + 19 个子模块）、jevokit-parent/Boot parent 继承链

## 现象 / 复现

Spring Boot 3.5.16 → 2.7.18 降级任务（版本号 3.5.2.2-SNAPSHOT → 2.7.3.0-SNAPSHOT，任务 1.1）按"只改根 pom + BOM 壳 pom"执行后，首次全量 `mvn clean compile` 结果：

1. **BUILD SUCCESS**——但这是假绿：Reactor Summary 里 19 个子模块版本仍显示 `3.5.2.2-SNAPSHOT`（仅根与壳显示新版本）；
2. 编译日志实证新配置零生效：maven-compiler-plugin 走 **3.14.1**（Boot 3.5.16 parent 所管理版本）且参数为 `[debug parameters release 17]`——任务 1.2 刚在根 pom 声明的 `java.version=1.8 + maven.compiler.release=8` 完全没进入子模块的有效模型；
3. 若未察觉直接提交，"降级"实为只换了根/壳两份 pom 的文字，19 个模块照旧按 Boot 3.5.16 + Java 17 构建。

复现路径：改根 pom parent 版本 + 项目版本 → 不动子模块 → 全量编译 → 观察 Reactor Summary 各模块版本行与编译日志的插件版本/`release` 参数。

## 根因

19 个子模块 pom 的 `<parent>` 块各自**硬编码** `<version>3.5.2.2-SNAPSHOT</version>`。根 pom 改版后，parent 块声明的 GAV 与 relativePath 指向的根 pom 实际 GAV 不再匹配，Maven 对此的处理是**告警并回落本地仓库按 GAV 解析 parent**——本地仓库恰好存有降级前 `mvn install` 的旧 `jevokit-parent-3.5.2.2-SNAPSHOT`（继承 Boot 3.5.16 + Java 17），解析成功，构建继续。于是：

- 每个子模块的有效 pom 挂在**旧 parent**上，根 pom（磁盘上的新文件）对它们形同虚设；
- reactor 的模块聚合清单仍来自根 pom，所以"全量编译"照样跑 19 个模块——聚合与继承是两条独立通路，聚合成功掩盖继承挂错。

本质与 [bug-rename-stale-local-repo-jar-20260910.md](bug-rename-stale-local-repo-jar-20260910.md) 同族：**同一标识符存在两份副本（磁盘新根 pom vs 本地仓库旧 parent pom），消费方（子模块继承链）静默取了旧副本**。区别在副本载体：那条是"已安装 jar"，本条是"已安装 parent pom"；且那条只在单模块 `-pl` 构建暴露，本条在**全量构建**就发作——因为 parent 解析走的是每个模块自己的继承通路，与 reactor 聚合无关。

放大因素：`-B` 批量模式下 relativePath GAV 不匹配的告警一行带过，淹没在下载日志里；BUILD SUCCESS 的第一观感会终结排查。

## 同类隐患排查

1. **全仓版本残留清点**（已完成）：`grep -rl "3.5.2.2-SNAPSHOT" --include=pom.xml` 全仓 0 残留；`2.7.3.0-SNAPSHOT` 共 21 处 = 根 1 + 壳 1 + 子模块 19，自洽闭环；
2. **同族条目回访**：[bug-rename-stale-local-repo-jar-20260910.md](bug-rename-stale-local-repo-jar-20260910.md)（仓库陈旧 jar，单模块构建暴露）、[bug-central-release-pipeline-20260921.md](bug-central-release-pipeline-20260921.md) 中"旧 BOM 假绿"（已发布 BOM 的陈旧缓存）——三例合并结论：**凡是"改了源、留了派生副本"的场景（安装 jar / 安装 parent pom / 已发布 BOM / IDE 索引），消费通路都可能静默取旧**；
3. **外推面**：versions-maven-plugin 批量改版（`versions:set`）会同步改子模块 parent 引用，手工改版最容易漏；CI 缓存的本地仓库、私服上的同名 SNAPSHOT 同属本条"副本陈旧"面（本次未触及，列为环境侧待查项）。

## 修复要点

19 个子模块 pom 的 parent `<version>` 逐文件随动为 `2.7.3.0-SNAPSHOT`（每文件仅此一行，与壳 pom 同性质机械随动）。验证闭环分两步：

1. 静态：新旧版本号全仓 grep，21 处新版本自洽、0 处旧版本残留；
2. 动态（关键）：重跑全量编译，**必须出现预期的新错误**——本次 release=8 生效后全 reactor 暴露 859 个编译错误（jakarta 包缺失 + Java 9+ API 拦截），错误清单与手工扫描清单对得上，证明新 parent + 新编译配置真实生效。"从假绿到预期红"本身即修复成立的证据。

## 回归用例

目前无自动化卡点，建议（按成本递增）：

1. **CI 静态断言（推荐先做）**：流水线加一步——`grep -rh "<artifactId>jevokit-parent</artifactId>" --include=pom.xml -A1` 提取全部 parent version，断言与根 pom `<version>` 一致（多一行 shell 即可）；版本翻新 PR 必过此闸；
2. **Reactor Summary 版本行巡检**：全量构建后对 Reactor Summary 段落断言"所有模块版本号一致"（可并入上述同一步）；
3. **预期错误断言**：配置翻新类变更的首次构建任务单里，显式写明"预期错误族"（如本次的 jakarta/Java 9+ API），构建产物与预期核对后才算通过——防"零错误假绿"与"错误清单不含预期族"两种走向。

## 防范措施

1. **版本翻新随动检查清单**：改任何 pom 的 parent 版本 / 项目版本 / 依赖坐标版本前，先 `grep -rn "旧版本号" --include=pom.xml` 摸清全部引用点；改完后反向 grep 新旧两个版本号，旧号必须清零、新号数量与引用点数对账；
2. **锐判据（架构师裁定，普适于一切配置翻新）**：**配置翻新后的首次编译必须出现预期的新错误——零错误反而可疑**。"预期错误"是配置生效的直接证据；本次 859 错正是 release=8 真实生效的证明，反过来 BUILD SUCCESS 才是事故现场；
3. **Reactor Summary 是最低成本的生效探针**：全量构建先扫一眼各模块版本行/插件版本/关键编译参数（`release N`），再谈成败——三处任何一处出现旧值即继承链挂错；
4. `-B` 模式下 Maven 的 relativePath 失配告警与"回落仓库解析"都不足以中断构建：凡 parent/依赖版本变更，不要依赖 Maven 报错兜底，静态对账必须人工（或 CI）执行。

## 关联（配置翻新"改不全 / 验不出"教训族谱）

- [bug-rename-stale-local-repo-jar-20260910.md](bug-rename-stale-local-repo-jar-20260910.md)——**仓库陈旧 jar**：源码已改而本地仓库派生副本未刷新，单模块构建混入旧常量；
- [bug-central-release-pipeline-20260921.md](bug-central-release-pipeline-20260921.md)——**旧 BOM 假绿**（五连障之一）：已发布 BOM 的陈旧解析；
- [bug-glob-brace-pattern-silent-mismatch-20260910.md](bug-glob-brace-pattern-silent-mismatch-20260910.md)——**检索假通过**：验收工具静默失配，"零命中"被当通过；
- 本条——**继承链挂错**：子模块 parent 块副本未随动，Maven 静默回落旧 parent，全量构建即假绿。

四例共同防线：**"静态对账清零 + 动态预期错误"双闸**——静态证明改全了，动态证明改的生效了，缺一不可。
