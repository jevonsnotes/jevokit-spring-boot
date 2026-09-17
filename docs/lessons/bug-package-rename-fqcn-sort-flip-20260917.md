# 包名迁移翻转 FQCN 字母序——@ConditionalOnBean 跨第三方 Bean 静默不装配

- 根因类别：其他（装配顺序的隐式依赖）
- 状态：已修复·已沉淀
- 相关模块：jevokit-audit-log、jevokit-rocketmq（jevokit 全家族包名迁移 2026-09-17）

## 现象 / 复现

包名迁移（`springbootkit.*` → `io.github.jevonsnotes.jevokit.*`）后闸门首跑，jevokit-audit-log 两用例失败：

1. `JevokitAuditLogConfigurationTest.wiresAuditBeansWithH2DaoByDefault`——断言 `AuditLogDao` 装配，报 "found no beans of that type"，而上下文**正常启动**（`hasNotFailed()` 通过，beanDefinitionCount=26）：模块其余 bean（处理器/过滤器/切面）全在，唯独 `@ConditionalOnBean(JdbcTemplate.class)` 守卫的 dao 静默消失；
2. `AuditLogJobGuardAdversarialTest.migrateJobStillWiredWhenDaoPresent`——同因连锁（job 以 `@ConditionalOnBean(AuditLogDao.class)` 守卫，dao 缺位则 job 一起缺）。

源码层面零改动这两个条件——纯包名变更即触发，且**无任何报错/告警日志**，属最险的静默失效类。

## 根因

Spring Boot 自动装配排序以 **FQCN 字母序为基底**（`AutoConfigurationSorter`：先字母序，再 `@AutoConfigureOrder`，再 before/after 消解）。`@ConditionalOnBean(X.class)` 求值的是"**此刻**容器里有没有 X 的 bean 定义"——时序敏感：

| 包名形态 | FQCN 字母序 vs 第三方（org.*） | 求值时序 | 结果 |
|---|---|---|---|
| `springbootkit.*`（s > o） | kit 装配排在 org.springframework/org.apache **后** | JdbcTemplate/RocketMQTemplate 已注册 | 条件成立，装配 ✓ |
| `io.github.*`（i < o） | kit 装配排在 org.* **前** | 目标 bean 尚未注册 | 条件不成立，**静默不装配** ✗ |

包名是一个"看不见的排序契约"：把包前缀从字母表后段挪到前段，等价于把全部 kit 自动装配的求值时机整体前移到 Spring/第三方装配之前——代码零变更，装配结果翻转。本仓库实测翻转的判据：旧包 `springbootkit`（s）与 `org`（o）比排在后，新包 `io.github`（i）排在前。

## 同类隐患排查（全仓 @ConditionalOnBean 结论表）

迁移修复时对全仓 8 处真实 `@ConditionalOnBean` 注解逐一判定（另有 3 处为 javadoc 提及/注释代码，非活注解）：

| 位置 | 条件目标 | 判定 | 理由 |
|---|---|---|---|
| audit-log `JevokitAuditLogConfiguration#auditLogDao` | `JdbcTemplate` | **受影响·已修复** | 跨包前缀依赖 Spring 官方 JdbcTemplateAutoConfiguration |
| rocketmq `JevokitRocketMqConfiguration#rocketMqHelper` | `RocketMQTemplate` | **受影响·预防修复** | 依赖 org.apache 官方 RocketMQAutoConfiguration；默认测试集不跑 MQ，闸门抓不住，靠排查命中 |
| audit-log `#jevokitAuditLogAutoInitDB` / `#auditLogAutoMigrateJob`（2 处） | `AuditLogDao` | 免疫 | 同一配置类内 dao 的 @Bean 声明在前，同类声明序求值时 dao 定义已就位 |
| xss `XssStringPropertiesEditChain` / `FormXssHandler` / `ResponseXssHandler` / `XmlXssHandler`（4 处类级） | `XssCleaner` | 免疫 | XssCleaner 与四个消费者同为 `XssConfiguration` 类内 @Bean 方法注册；类级条件对 @Bean 方法不生效，注册序即声明序 |
| （正例参照）redis `JevokitRedisConfiguration` | —— | 本就锚定 | 已有显式 `@AutoConfigureBefore(RedissonAutoConfigurationV2)`，顺序不靠字母序 |

判定法则：**条件目标与消费者是否在同一配置类/同一家族包内**决定免疫；条件目标来自第三方自动装配（org.* 等）即为时序敏感点。

## 修复要点

受影响 2 处补显式次序锚定，不依赖字母序这个隐式契约：

- `JevokitAuditLogConfiguration` + `@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)`
- `JevokitRocketMqConfiguration` + `@AutoConfigureAfter(RocketMQAutoConfiguration.class)`

锚定后顺序由注解声明决定，未来再动包名/坐标也不会翻转。修复后全量 510 用例零失败。

## 回归用例

`JevokitAuditLogConfigurationTest`（装配条件单测，WebApplicationContextRunner + H2）即本隐患的常驻防线——它显式注册 `JdbcTemplateAutoConfiguration` 与 kit 装配类，条件求值时序与生产 .imports 机制同构，dao 缺位立即红。rocketmq 侧默认测试集不可达（@Tag("mq") 排除），其防线在全量 `-DexcludedGroups=` 场景。

## 防范措施

1. **包名/坐标迁移必排查条件装配**：凡动包名（或引入改变 FQCN 排序的坐标变更），全量 grep `@ConditionalOnBean` / `@ConditionalOnMissingBean`，对"条件目标为第三方自动装配 Bean"的每一处加 `@AutoConfigureAfter` 锚定——免疫的（同配置类内依赖）记录判定理由即可；
2. **装配条件单测是唯一防线**：上下文能启动、其余 bean 都在，唯独条件 bean 静默消失——只有"逐 bean 断言存在性"的 runner 用例能抓住；默认测试集覆盖不到的模块（如 MQ 依赖）要做预防性排查，不能等 E2E；
3. `AutoConfigurations.of(...)` 测试注册同样走 sorter（含字母序），故此类测试与生产机制同构、可作防线；反过来，**测试里"恰好按字母序传入"掩盖时序缺陷**的风险同理存在——跨第三方 Bean 的条件断言用例应显式打乱注册顺序验证过一次；
4. 附：本次闸门 2 首跑 `-pl sample/jevokit-sample` 失败一笔，是 [bug-rename-stale-local-repo-jar-20260910.md](bug-rename-stale-local-repo-jar-20260910.md) 的原地复现（本地仓库仍为旧包 jar，全仓 `mvn install` 刷新后转绿）——改名系收尾"刷新本地仓库"动作再次被证实不可省略。

## 关联（改名系教训族谱）

- [bug-rename-stale-local-repo-jar-20260910.md](bug-rename-stale-local-repo-jar-20260910.md)——**派生副本陈旧**：本地仓库旧 jar 混入单模块构建（本次原地复现）；
- [bug-rename-refactor-checklist-projection-loss-20260910.md](bug-rename-refactor-checklist-projection-loss-20260910.md)——**清单漏改**：改名重构的操作层投影损失；
- [bug-eureka-transitive-dep-consumer-sweep-20260910.md](bug-eureka-transitive-dep-consumer-sweep-20260910.md)——**消费面盲区**：变更后只看被改方不看消费方；本条同病——改的是包名，断的是"字母序"这个隐式消费契约。
