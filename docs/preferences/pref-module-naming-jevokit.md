# 模块坐标命名规范：jevokit- 家族令牌 + 标准形后缀

- 分类：代码风格
- 状态：已认可
- 适用范围：本仓库全部新增 Maven 模块的 artifactId 命名

## 偏好描述

新增模块按其性质二选一：

1. **自启动功能模块（有 AutoConfiguration 的）**：`jevokit-<module>-spring-boot-starter` 标准形——例：`jevokit-security-xss-spring-boot-starter`、`jevokit-audit-log-spring-boot-starter`
2. **底层库（被 starter 依赖、不含自动装配入口的）**：`jevokit-<name>` 裸形——例：`jevokit-core`、`jevokit-autoconfigure`

配套规则：
- 聚合"一引全装"壳也走标准形：`jevokit-security-all-spring-boot-starter`
- 模块目录名 = artifactId 去掉 `-spring-boot-starter` 尾缀（目录不重复 starter 字样）
- 家族令牌 `jevokit` 于 2026-09-16 经 Central 检索实测零占用后由用户终定（经历 kit-/spkit-/jevokit- 三轮择枝）
- 注意：Maven 坐标令牌（jevokit-）与配置前缀（spkit.*）、Java 包名（springbootkit.*）三者**有意不同名**——坐标层品牌 / 用户配置层品牌 / 代码层命名空间各司其职

## 认可场景

2026-09-16，首发 Maven Central 前的结构决策：用户在 A（维持族名）/B（族名+后缀）/C（生态标准形）三向中选定 C，又在撞名事实（rocketmq/jasypt-spring-boot-starter 已被官方/事实标准件占用）约束下于 kit-/spkit-/jevokit- 三个家族令牌中终定 jevokit-，原话："改成 jevokit-"。

## 反例（什么算违背）

- 新自启动模块起裸名（`jevokit-xxx` 无 starter 后缀）——检索不到 starter 惯例命中
- 新底层库硬加 starter 后缀（撒谎：不是 starter 却标 starter）
- 坐标里用回 spring-boot-kit- 旧前缀或裸泛词（core/configuration/redis 等单段名——Central 有 1456 个 core）
- 起与知名构件同名的坐标（rocketmq-spring-boot-starter 等——撞名=误导）

## 参考实现

2026-09-16 重组落地的 20 个坐标（jevokit-parent/jevokit-dependencies/jevokit-core/jevokit-autoconfigure + 16 个 starter + jevokit-sample），见仓库目录树。
