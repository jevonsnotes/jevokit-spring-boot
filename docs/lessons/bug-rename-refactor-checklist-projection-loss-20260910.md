# 机械改名重构中"表格映射成立但清单文字漏列"的引用漏改

- 根因类别：重复实现走偏（同一标识符多处定义点，改了一处忘了另一处）
- 状态：已修复·已沉淀
- 相关模块：spring-boot-kit/pom.xml、common/spring-boot-kit-configuration/pom.xml

## 现象 / 复现

大规模重命名重构（2026-09-10），eng-1 分片执行 BOM + 根 pom + common 改名。逐文件清单执行完毕后自检 `grep -ri "eureka|tongweb" --exclude-dir=target`，命中 2 处漏改：

1. 根 pom module 行——清单文字只写"modules 两行路径改新 + 删 tongweb 行"（指 common 两行），但坐标映射表明确 test-server 目录要改名，module 引用是改名的连锁点，清单文字未逐行列出；
2. configuration pom 对 core 的 `<dependency>` 坐标——清单只写"parent 引用改新"，模块间依赖坐标同样受映射表约束但未在清单点出。

## 根因

改名任务的执行依据有两层：**映射表（规则层）** 与 **逐文件清单（操作层）**。清单由人从映射表推导生成，推导时按"文件 × 显眼改动点"展开，容易漏掉文件内不显眼的连锁引用（module 路径、兄弟模块依赖坐标）。执行者若按清单逐条打勾而不回看规则层，漏改就会存活到自检。

本质：**规则层与操作层双源，操作层是规则层的投影，投影必然有损**。

## 同类隐患排查

按"映射表中的旧标识符"而非"清单动作"对分片全量 grep 复核，同类点：

- 根 pom 注释段内旧坐标（已机械改，无风险）；
- README 内联坐标/根键/FQCN/bean 名（多源散布，sed 批量处理后 grep 收敛，见下）。

## 修复要点

1. 两处漏改按 §3.1 映射表补改：module 行改 `server/spring-boot-kit-test-server`，依赖坐标改 `spring-boot-kit/spring-boot-kit-core`；
2. 修复后重跑自检 grep 至零命中（值级例外白名单除外）。

## 回归用例

自检 grep 本身即回归防线：`grep -ri "eureka|tongweb" --exclude-dir=target <分片文件集>` 零命中（含白名单比对）。本次该防线实际拦截了 2 处漏改。

## 防范措施

1. **机械改名任务的验收以规则层（映射表）为准，清单只做导航**：执行完清单后，必须再用映射表中的每个旧标识符（旧前缀、旧包名、旧坐标……）对分片做全量 grep 复核，而不是只信清单打勾；
2. **清单生成侧**：凡"目录改名/坐标改名"类动作，清单应自动携带其引用面（`<module>` 行、`<dependency>` 坐标、.imports FQCN、yml bean 字面量），本次 target-structure.md §3.1 已列"pom 内部引用同步点"，但文字粒度不齐——架构文档应把引用面写成封闭枚举；
3. **执行侧**：sed 批量替换（按规则层正则）优先于逐处 Edit，替换后 grep 收敛，天然免疫清单漏列；本次 Java 源码层用 sed 批量（零漏改），pom 层用逐处 Edit（漏 2 处），对比印证。
