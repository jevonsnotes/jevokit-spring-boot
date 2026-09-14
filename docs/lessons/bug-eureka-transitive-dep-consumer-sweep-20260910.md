# 删除模块的传递依赖消费断链：字样级 grep 的盲区

- 根因类别：边界条件遗漏
- 状态：已修复·已沉淀
- 相关模块：`server/spring-boot-kit-test-server/src/test/.../SimpleTest.java`（原 `server/picc-spring-test-server/`）；被删模块 `others/picc-spring-eureka`；ADR-6（target-structure.md v1.3）

## 现象 / 复现

去 PICC 化重构三片并行期间，eng-3 片（others + test-server）按协调规则 3 完成片内自检：`grep -ri "picc|eureka|tongweb"`（排除 target/）零命中（仅剩 §7 例外的密钥值），报完成。统一验证阶段 `mvn clean install` 时 test-server **test-compile 失败**：`SimpleTest.test2()` 使用 `reactor.cache.CacheFlux` / `reactor.core.publisher.Flux/Mono/Signal`，而这些类此前**经 picc-spring-eureka 模块的传递依赖**进入 test-server 的 classpath——eureka 整目录删除后 reactor 无来源，断链。

复现路径：删除任何"传递依赖提供方"模块后，只要下游源码（本例是 test 源码）直接 import 了传递进来的第三方类，编译即断；而此类 import 语句中**不含任何被删模块的业务字样**。

## 根因

**删除动作的验证手段与删除动作的影响面不匹配。** 自检口径是"字样级 grep"（picc/eureka/tongweb），它能防住"改名/引用清理遗漏"（直接引用残留），但防不住"无字样的传递依赖消费断链"（间接影响）：reactor.cache 与 eureka 之间没有文本级关联，字样 grep 的匹配边界天然覆盖不到它。删除操作的影响面 = 直接引用 + 传递依赖消费面，而字样 grep 只覆盖前者。

归类说明：本条**不是**"未复用已有能力"（没有重复造轮子），是删除动作的间接影响超出了验证手段的字面匹配边界，归**边界条件遗漏**。与 [bug-transitive-dep-test-source-miss-20260910.md](bug-transitive-dep-test-source-miss-20260910.md)（架构师沉淀：删传递依赖提供方时只扫 main 漏扫 test）**同源互补**：那条讲"扫的源码集不全"，本条讲"扫的匹配维度不全"——两者合起来才是完整的消费面排查方法。

## 同类隐患排查

按"传递依赖消费者"维度对全仓反查（grep 被删生态的**库包名**而非业务字样）：

- `reactor.` → 命中 2 处：test-server 主类 `@LoadBalanced`（spring-cloud-commons，架构师在 §1.2-2 静态分析阶段已预判并列入删除清单）、`SimpleTest.test2()`（漏网，本条）
- `com.netflix.` / jersey 等 eureka client 专属包 → 无直接消费
- jettison（eureka 唯一消费者）→ 架构师 §1.3-1 已按孤儿托管随删
- 结论：主路径断链被静态分析预判拦下，**test 源码中的学习实验代码**成为唯一漏网点

## 修复要点

ADR-6 决策（不加依赖，学习实验不固化为显式依赖）：

1. 删除 `SimpleTest.test2()` 方法（reactor.cache 实验代码）
2. 删 4 行 reactor import + 随删 test2 独占的 3 行 `java.util.function` 死 import（唯一消费者即 test2，避免留下新死代码；与架构师微裁定一致）
3. 保留其余 3 个无关用例（test/testPagination/testInnerJson）不动

修复后 clean-room `install -fae` 23/23 SUCCESS（test-server 含 testCompile 8.996s），AC-5 冒烟通过。

## 回归用例

机械修复不新增用例。防线是验收标准 9.2：统一验证必须跑 `mvn clean install` **含 test-server 测试编译**（install 而非 package，-fae 不掩盖分片失败）——本条断链正是被这道防线拦截的实证。

## 防范措施

1. **删模块/依赖后的消费面排查必须加"传递依赖消费者"维度**：grep 目标生态的库包名（`reactor.`、`com.netflix.` 等），而非仅业务字样（picc/eureka/tongweb）。字样 grep 只防改名遗漏，不防依赖断链，派发文档中应标注自检口径的适用边界。
2. **main 与 test 源码集都要扫**（与架构师条目互补，缺一不可）。
3. **编译是最终防线**：并行改造期不做整仓编译没问题，但收尾的 `install -fae` 全绿是不可跳过的验收项。
4. 交付物中的实验/学习代码（demo、TODO 注释标记的试错代码）在重构清单盘点时应单独标记，它们往往引用非常规依赖且不在功能测试保护伞内。
