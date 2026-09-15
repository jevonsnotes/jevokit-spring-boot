# 姊妹/三胞胎方法防御不对称族谱（同一缺陷维度各修一半）

- 根因类别：重复实现走偏
- 状态：已修复·已沉淀
- 相关模块：core/SecurityUtil、others/OkHttpUtil、security 三拦截器（xss/sql-inject/path-traversal）、audit dao/job（B 档测试重构 bug 批次，2026-09-15 终验全过）

## 现象 / 复现

本批次（BUG-1~12）中五条缺陷属同一形状：**复制粘贴出的姊妹（或三胞胎）方法，在同一个缺陷维度上各修了一半**——防御分支、语义分支或守卫注解只落在其中一侧，另一侧裸奔或走叉。逐条一行（详情见各 BUG 修复回执中的根因草稿）：

| 实例 | 姊妹结构 | 不对称点 | 发现人 |
|---|---|---|---|
| BUG-2 `SecurityUtil.processMap` / `processCollection` | 近乎复制的两个私有方法 | `"Unmodifiable"` 字样防御只有 collection 侧有；catch 面两边都只枚举了 UnsupportedOperationException，newInstance 抛的 UtilException 双双逃逸 | eng-1 |
| BUG-3 `OkHttpUtil.executeBody` / `execute` | GET/POST 两条执行路径 | `isSuccessful` 分支只有 execute（POST）有：GET 把 4xx/5xx 错误体当正常串返回，POST 返回空串——语义分叉而非对称缺失 | eng-2 固化，eng-1 修 |
| BUG-8 三拦截器注解遮蔽 ×3 | XssClean/SqlInject/PathTraversal 三套 Interceptor + XssClean/SIClean/PtClean 三注解（三胞胎×3） | 注解遮蔽（方法级被类级/继承链遮蔽）的处理三处各写一套、行为不一致 | eng-2 |
| BUG-9 SI 链元数据源错位 | `SqlInjectStringPropertiesEditChain` 与姊妹链（XSS/PT 链） | 元数据取源三链各写一份，SI 链取错源（Meta/Holder 配置源错位） | eng-2 |
| BUG-7 audit dao/job 守卫粒度不对齐 | `AuditLogDao` 与 `AuditLogAutoMigrateJob` | 同一份守卫逻辑两处各写，粒度（空/边界判定）不对齐，一侧放行另一侧拦截 | eng-2 |

共性失败形态：调用方在姊妹路径之间切换（GET↔POST、Map 字段↔Collection 字段、注解打在类↔方法）时踩到行为分叉；单测若只覆盖其中一侧（惯例上确实只测"主路径"），另一侧的缺陷隐形。

## 根因

字面级相似 ≠ 语义等价。复制粘贴创建姊妹方法时，两份代码在诞生瞬间是等价的，维护者据此建立"两边一样"的心智模型；但后续修复/增强只在阅读到的那一份上落（改 A 忘 B），单边演化悄然发生。等价心智模型 + 单边修改 = 系统性防御不对称，且每次不对称都落在**同一个缺陷维度**（防御、语义、守卫），因为它们本来就是同一次"要加防御"的意图，落点却只有一处。

两个加重因子（本族反复出现）：

1. **防御枚举具体形态而非失败层级**：如 `"Unmodifiable"` 字样匹配（枚举"不可变"的一种命名形态，防不住 `ListN`/`List12`）、catch `UnsupportedOperationException`（枚举变更失败的一种异常类型，接不住 `UtilException`）。枚举式防御天生漏新形态，姊妹两侧再各枚举一半，漏上加漏。
2. **姊妹方法无共享抽象**：三胞胎拦截器、三条 edit 链都是逐份复制的产物，没有"这一族共有的防御必须挂在共享基类/模板方法上"的结构约束，于是每次防御都靠人工记得"还有两份"。

与"重复实现走偏"经典形态（改一处忘另一处的纯遗漏）的区别：本族是**防御性意图的单边落地**——不是忘了改另一份业务逻辑，而是"这次要加保护"的修复动作本身只覆盖了一半，防御残留物（一侧有检查一侧没有）比纯遗漏更难被读到（读者会把有检查的一侧当成两边都有）。

## 同类隐患排查

批次内按"姊妹结构清单 × 缺陷维度"做了族谱扫描：core utils 的 execute 族（execute/executeBody/executeByte/executeStream）、SecurityUtil 的 processMap/processCollection、security 三拦截器与三链、audit dao/job 对——共定位五处（上表），全部修复并通过 TE-1 对抗复测（2026-09-15 终验 12/12 全闭环）。

残留观察（未列入缺陷，供后续重构决策）：`OkHttpUtil.executeByte`/`executeStream`（download 族）对非 2xx 仍返回 null，与字符串路径修复后的 error-mode 语义（quiet 返体/throwout 抛）存在粒度差——download 族返回类型（byte[]/InputStream）本身无"错误体"承载位，属契约设计问题而非防御遗漏，已单独报领导定性。

## 修复要点

- BUG-2：`processMap`/`processCollection` 统一为两段独立防线——临时容器构造失败（RuntimeException 级）告警跳过、回写失败（RuntimeException 级）告警跳过；删除字样检查；**回调执行段留在防线外**，用户回调异常仍正常上抛不被吞。
- BUG-3：`executeBody` 改为一行委托 `execute(request, null)`，姊妹方法直接消灭（共享单一实现）；`execute` 内非 2xx 按 error-mode 分流（throwout 抛 `OkHttpStatusRuntimeException` 带 code/url/body，quiet/log 返回错误体）。
- BUG-8/9/7：由发现人 eng-2 按同思路修复（遮蔽判定/元数据源/守卫粒度各自对齐或上提共享），根因与修法见各自回执。

修复的共同取向：**能合并就合并（单一实现），不能合并就把防御封在失败自然层级（结构操作失败=RuntimeException 级），并挂在共享抽象上**。

## 回归用例

- BUG-2：`SecurityUtilTest`（core 模块）filterStr 组——`immutableMapAndFactoryCollectionsAreSkippedNotCrash`（混合字段：不可变字段跳过+其余正常过滤）、`topLevelImmutableCollectionIsSkipped`、`unmodifiableListFieldIsSkippedSilently`。
- BUG-3：`OkHttpUtilTest`（okhttp 模块）——`errorStatus_insensitiveQuietReturnsErrorBody`（GET/POST 双路径对称断言）、`errorStatus_insensitiveThrowoutThrows`、`errorStatus_logModesReturnErrorBody`。
- BUG-7/8/9：由发现人随修复落在本模块测试，终验全过。

## 防范措施

1. **姊妹方法改动必须 diff 对照另一侧**：改到任何一侧的防御/语义分支时，diff 必须展示姊妹侧并回答"这里要不要同步"——review 清单项，不是口头提醒。
2. **结构性防御封"失败自然层级"而非枚举具体形态**：结构性操作（反射实例化、容器回写、类型转换）的防御 catch 应到 RuntimeException 级并降级告警，不做字样匹配、不枚举单一异常子类；枚举式防御每多一个具体形态就多一个隐形旁路。
3. **新增姊妹方法时防御用共享抽象而非复制**：三胞胎结构（拦截器/链/dao 对）的共性防御落在共享基类、模板方法或组合出的单一实现上；确需复制时在两份代码头部互相点名（"姊妹实现：X，改我必改它"），把隐性亲属关系显式化。
