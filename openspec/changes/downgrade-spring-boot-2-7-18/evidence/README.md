# 证据目录：downgrade-spring-boot-2-7-18

## 20261012-*.log（Wave A 收口闸门过程证据，java-backend-eng-2 产出）

| 文件 | 产生命令 | 用途 |
|---|---|---|
| 20261012-unified-compile.log | `mvn -B clean test-compile -fae -Dmaven.compiler.failOnError=false`（根目录，JAVA_TOOL_OPTIONS=en） | 统一编译验证：53 条错误甄别（根因 2 条：jevokit-redis Jackson2JsonRedisSerializer 双参构造器；51 条级联）、110 处手工清单与 7.2 清单三轨对账基线 |
| 20261012-unified-tree.log | `mvn -B dependency:tree`（根目录） | 依赖树核销：EE9+ jakarta 归零（10 族 + glassfish jaxb 2.3.9 全 EE8/javax 线）、redisson -27/-35（-35 仅 workflow 已知中间态）、八大坐标零漂移 |
| 20261012-surefire-check.log | `mvn -B test -pl starter/security/jevokit-security-code-inject -am` | tasks 1.7：surefire 2.22.2 @{argLine} jacoco 注入 + UTF-8 参数族生效，136 测试零失败 |

## 时效性说明

本批为 **2026-10-12 闸门对照基线**（redis 2 处根错误修复前的快照，价值：错误甄别与三轨对账的原始依据）。
**非终验证据**——阶段 8 验收（tasks 8.13）按"末批改动后重跑出新鲜证据"规范重新产出，本批不替代。

## 收口时间线补记（架构复核 2026-10-12）

- **redis 根因修复后复跑**：redis 2 条 Jackson2JsonRedisSerializer 漏项修复 + sample 测试域 6 处存量修复后，全量编译复跑 **21/21 SUCCESS 零错误（约 1:14）**——实施侧报告，log 未存档（终验按 8.13 规范重新产出新鲜证据，此处仅记事实）。
- **tree log 中 -35 的中间态说明**：20261012-unified-tree.log 快照时点 workflow 模块 exclusion 尚未落位，故 workflow 树上见 `redisson-spring-data-35`（log 行 1536）属**已知中间态**；workflow pom 落位 exclusion 后已实证 -35 零在场、-27:3.50.0 在场、spring-data-redis 2.7.18 自洽（tasks 5.2）。redis/nogen/sample 三树 -27:3.50.0 在场（log 行 997/1106/1768）不受影响。
- **compile log 行号对照**：log 中根因行号 [114,67]/[171,75] 与 tasks 4.5 记载的 115-116/172-173 差 1 行，系修复时插入机制注释行所致，修复后行号为准。
