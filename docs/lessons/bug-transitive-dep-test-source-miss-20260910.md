# 删除传递依赖提供方时"只扫 main 漏扫 test"的消费面盲区

- 根因类别：边界条件遗漏（源码集边界：main/test 双源码集只扫了其一）
- 状态：已修复·已沉淀
- 相关模块：others/spring-boot-kit-eureka（已删）、server/spring-boot-kit-test-server SimpleTest.java、docs/refactor/target-structure.md §1.2-2 / §1.2-10 / ADR-6

## 现象 / 复现

去 picc 化重构（2026-09-10）删除 eureka 模块后，三片分片自检全部通过（grep picc 零命中），统一编译验证阶段报全仓唯一错误：

```
spring-boot-kit-test-server testCompile 失败：
SimpleTest.java:11 程序包 reactor.cache 不存在
```

基线编译日志（docs/refactor/baseline-testserver-package.log）对账证实：重构前 testCompile 通过——当时 reactor-core 经 `picc-spring-eureka → spring-cloud-starter-loadbalancer → reactor-core` 传递链可用；删 eureka（执行正确）后链路消失，SimpleTest 的 reactor 消费（全仓唯一）断链。

架构侧在 target-structure.md §1.2-2 已识别了主类 `@LoadBalanced` 的同类断链并处置，说明分析方法本身是对的——错的只是扫描范围：消费面 grep 用了 `**/src/main/**/*.java` 惯例，test 源码不在视野内。

## 根因

1. **源码集边界遗漏**：Maven 模块的源码集有 main/test 两个（广义还有 it/testFixtures），分别驱动 compile 与 testCompile 两个编译阶段。删传递依赖提供方的影响面是传递闭包的全部消费者，test 源码的 import 同样在闭包内。分析时把"生产代码"默认为"全部代码"，边界无意识收窄。
2. **grep 防线对此类问题结构性无感**：分片自检查的是"picc 标识符残留"，而本断链与 picc 无关（reactor 是无辜旁观的传递依赖），且属编译期断链而非文本残留——文本防线拦不住依赖图问题。
3. **暴露点滞后**：并行分片流程（BOM 先行、全量换坐标）决定了分片内无法独立编译跨片依赖，统一编译必然排在三片齐后——问题因此在最贵的时间点（收尾仲裁 + 返工 + 复跑）才暴露，本可在设计阶段零成本发现。

## 同类隐患排查

- 修复后按同一根因复查：全仓 reactor 消费仅 SimpleTest 一个文件（grep 实证）；test-server 其余测试依赖（junit/mockito/spring-boot-starter-test）均为直接 test 依赖，不随 eureka 消失，无同类点。
- 一般化排查动作（本次应做而未做）：对被删模块做删除前后 `mvn dependency:tree` diff——删前树含 reactor-core、删后不含，逐项确认每个消失坐标的消费者。

## 修复要点

ADR-6（target-structure.md v1.3）：选 A——删 `SimpleTest.test2()`（无断言的学习实验用例）+ 4 行 reactor import，不向 pom 添加依赖。与 ADR-2 主类 `@LoadBalanced` 处置同构：**eureka 删除造成的传递依赖消费者，移除消费点而非引入依赖维持原状**；否决 B（加 test-scope reactor-core）——会把"偶然编译通过"的实验固化为显式依赖。修复本身局部且无争议，教训价值在防范侧。

## 回归用例

统一验证 `mvn clean install -fae` 23/23 全绿（AC-3 含 testCompile 阶段）即本项回归防线；test-engineer 复跑 AC-3/AC-5 收口。

## 防范措施

1. **消费面 grep 覆盖全部源码集**：凡评估"删依赖/删模块"的传递影响，grep 范围用模块根 + 文件类型过滤（`--glob **/*.java`），不用 `src/main` 这类源码集过滤——main/test/it 是编译阶段概念，不是影响面概念。
2. **双视角互补**：grep 找"谁在 import 消失的包"（正向），`mvn dependency:tree` 删除前后 diff 找"什么坐标从 classpath 消失"（反向），两个视角都跑，缺一不可——grep 只能找到写了 import 的消费者，tree 能发现没人 import 但运行期需要的（资源、SPI、反射场景）。
3. **基线日志可对账**：删改前先跑一次全量构建留基线（本次靠 baseline-testserver-package.log 才能快速定性"重构前能编译"）。无基线，回归对比无锚点。
4. **断链前移到设计期**：并行分片流程下统一编译必然是收尾动作，编译期才暴露断链的回溯成本最高（仲裁 + 返工 + 复跑）。依赖影响分析（含 test 源码）必须在架构文档期完成，让这类问题出现在 §1.2 影响面清单里，而不是验证阶段的报错里。

## 复发计数（开局必看）

**本族（源码集/检索盲区）累计第 3 次命中，且已跨角色复发——包括"知道这条教训的人"：**

| # | 事件 | 盲区形态 | 教训条目 |
|---|---|---|---|
| 1 | reactor.cache 断链（ADR-6，2026-09-10） | 消费面 grep 用 `src/main` 过滤，漏 test 源码 | 本条目 |
| 2 | 花括号 glob 静默失配（2026-09-10） | 检索模式 `**/*.{yml,properties}` 不被工具支持导致"零命中"假象 | 姊妹条目 bug-glob-brace-pattern-silent-mismatch |
| 3 | 机构号 20291584 残留（F-5，2026-09-10） | 领导终验 grep 只扫 README、未覆盖 src/test——**发通报者自己的检索口径越权（声称"全仓"实为单文件）** | 本条目追加 |

**第 3 次复发的新增量**：盲区不只在"扫描者"，也在"声称已验证者"——任何"全仓/全部/零命中"表述必须与实际检索的文件集口径一致，口径（含 src/test 与否、文件类型、排除项）要随结论一并给出。开局派活前读本表：凡涉及"验证已清零"的通报，先问一句"文件集含 src/test 吗、用的什么模式"。
