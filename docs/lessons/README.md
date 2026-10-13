# 经验沉淀库（Lessons Learned）

> 本文件由 team-creator 技能生成。它是一个**索引 + 使用说明**，具体经验沉淀在本目录下的各条目文件里。

## 这是什么

团队踩过的坑、bug 根因、最佳实践的沉淀库。它是项目级的"长期记忆"——区别于记录"本次团队有哪些人"的 [`.claude/team-contract.md`](../../.claude/team-contract.md)，这里记录的是**跨会话、跨团队都成立的知识**，随项目提交 git。

**与 `../preferences/` 的分工**：本库记"错"（bug 根因、踩坑），目的是**防范复发**；`docs/preferences/` 记"对"（用户认可的风格、约定），目的是**主动复用**。两者方向相反，不要混——bug 根因写这里，用户偏好写 `preferences/`。

## 团队开局必读（核心机制）

新会话或新成员加入时，**领导在第一次派活前先读本文件的索引表**，把相关历史经验纳入派发指令。这就是"不重复踩坑"的关键——每一次开工都站在历史经验之上，而不是从零开始。

读法：扫一眼下表的"根因类别"和"标题"，凡是与本次需求相关的条目，点进去看根因和防范措施，在派活时提醒对应成员避开。

## 根因分类法

每条经验必须归入下列类别之一，便于检索和发现反复出现的模式：

| 类别 | 典型表现 |
|---|---|
| 边界条件遗漏 | 空值、极值、首末次循环、分页边界未处理 |
| 契约不一致 | 前后端/模块间接口字段、类型、语义对不上 |
| 重复实现走偏 | 多处各写一套同类逻辑，改了一处忘了另一处 |
| 未复用已有能力 | 已有工具函数/组件没复用，新造的轮子带新 bug |
| 异常路径未处理 | 只考虑正常流程，失败/超时/重试路径裸奔 |
| 性能·并发 | N+1 查询、资源竞争、死锁、热路径瓶颈 |
| 其他 | 上述之外 |

## 条目索引

> 每沉淀一条经验，在本表追加一行；条目文件命名为 `bug-<简述>-<日期>.md`（或按主题归并）。

| 标题 | 根因类别 | 状态 | 文件 |
|---|---|---|---|
| 机械改名重构中"表格映射成立但清单文字漏列"的引用漏改 | 重复实现走偏 | 已修复·已沉淀 | [bug-rename-refactor-checklist-projection-loss-20260910.md](bug-rename-refactor-checklist-projection-loss-20260910.md) |
| BOM import 不传 pluginManagement：无版本插件漂移到 Central metadata 的 4.2.0-M1 | 契约不一致 | 已修复·已沉淀 | [bug-bom-import-no-pluginmgmt-plugin-version-drift-20260910.md](bug-bom-import-no-pluginmgmt-plugin-version-drift-20260910.md) |
| 删除传递依赖提供方时"只扫 main 漏扫 test"的消费面盲区 | 边界条件遗漏 | 已修复·已沉淀 | [bug-transitive-dep-test-source-miss-20260910.md](bug-transitive-dep-test-source-miss-20260910.md) |
| 删除模块的传递依赖消费断链：字样级 grep 的盲区 | 边界条件遗漏 | 已修复·已沉淀 | [bug-eureka-transitive-dep-consumer-sweep-20260910.md](bug-eureka-transitive-dep-consumer-sweep-20260910.md) |
| 验收检索中花括号 glob 模式静默失配，"零命中"险被当通过 | 其他 | 已修复·已沉淀 | [bug-glob-brace-pattern-silent-mismatch-20260910.md](bug-glob-brace-pattern-silent-mismatch-20260910.md) |
| 改名类重构后本地 Maven 仓库陈旧 jar 混入单模块构建——防线测试首跑即真实拦截 | 重复实现走偏 | 已修复·已沉淀 | [bug-rename-stale-local-repo-jar-20260910.md](bug-rename-stale-local-repo-jar-20260910.md) |
| 洋葱型环境阻塞——fail-fast 依赖故障（Redis 连不上）长期掩盖 camunda 配置缺陷（admin id 连字符） | 其他 | 已修复·已沉淀 | [bug-env-failfast-masking-config-defect-20260910.md](bug-env-failfast-masking-config-defect-20260910.md) |
| rocketmq-client "connect to null failed" 报错形态误导：环境缺位被误判为装配/绑定断链 | 其他 | 已定性·已沉淀 | [bug-rocketmq5-connect-null-misread-20260910.md](bug-rocketmq5-connect-null-misread-20260910.md) |
| 姊妹/三胞胎方法防御不对称族谱（BUG-2/3/7/8/9 五实例） | 重复实现走偏 | 已修复·已沉淀 | [bug-sister-methods-defense-asymmetry-family-20260911.md](bug-sister-methods-defense-asymmetry-family-20260911.md) |
| 跨边界隐式契约断裂三合一：值捕获跨线程/资源所有权跨方法/getBytes 默认编码跨平台（BUG-1/4/5） | 契约不一致 | 已修复·已沉淀 | [bug-boundary-implicit-contract-break-20260911.md](bug-boundary-implicit-contract-break-20260911.md) |
| 共享可变静态状态两轴：跨实例构造偷换（JasyptUtil 静态字段）与半截双检锁漏 put（DesUtil 惰性缓存永远 miss） | 性能·并发 | 已修复·已沉淀 | [bug-shared-mutable-static-state-20260911.md](bug-shared-mutable-static-state-20260911.md) |
| 包名迁移翻转 FQCN 字母序——@ConditionalOnBean 跨第三方 Bean 静默不装配 | 其他（装配顺序的隐式依赖） | 已修复·已沉淀 | [bug-package-rename-fqcn-sort-flip-20260917.md](bug-package-rename-fqcn-sort-flip-20260917.md) |
| 测试报告编码不一致：Surefire XML UTF-8 声明与 JVM 平台默认 GBK 冲突（Windows + JDK17） | 契约不一致 | 已修复·已沉淀 | [bug-surefire-report-encoding-gbk-mismatch-20260918.md](bug-surefire-report-encoding-gbk-mismatch-20260918.md) |
| JSecureContext 三个 ThreadLocal 请求结束后不清理——线程池复用串终端/串交易号 | 异常路径未处理 | 已修复·已沉淀 | [bug-jsecure-context-threadlocal-leak-20260920.md](bug-jsecure-context-threadlocal-leak-20260920.md) |
| Maven Central 首发五连障：插件静默跳过/token 失效/两步序少半步/模块缺 name/旧 BOM 假绿 | 契约不一致 | 已修复·已沉淀 | [bug-central-release-pipeline-20260921.md](bug-central-release-pipeline-20260921.md) |
| 子模块 pom parent 块硬编码旧版本号——配置翻新后全量编译"假绿"（BUILD SUCCESS 但新配置零生效） | 重复实现走偏 | 已修复·已沉淀 | [bug-parent-version-stale-pom-false-green-20261012.md](bug-parent-version-stale-pom-false-green-20261012.md) |
| 日志路径上的 Spring 容器查找——logback 过滤器 getBean 与上下文刷新的三方循环等待（DEF-1） | 性能·并发 | 已修复·已沉淀 | [bug-log-filter-container-deadlock-20261012.md](bug-log-filter-container-deadlock-20261012.md) |

## 单条目骨架

新建 `bug-<简述>-<日期>.md` 时，按以下结构写：

```markdown
# <bug 或坑的标题>

- 根因类别：<上述分类之一>
- 状态：<已修复·已沉淀 / 待跟进>
- 相关模块：<文件或模块>

## 现象 / 复现
<怎么发现的、怎么复现>

## 根因
<为什么会发生，不止"怎么绕过">

## 同类隐患排查
<按根因搜出的同类点、各自处置>

## 修复要点
<关键改动、为什么这么改>

## 回归用例
<转化为哪条回归用例、位置>

## 防范措施
<以后如何避免再犯——编码约定、检查项、工具卡点>
```
