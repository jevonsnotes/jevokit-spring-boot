# 对外配置键要在 application.yml 给演示默认值 + "生产要改"注释

- 分类：代码风格
- 状态：已认可
- 适用范围：spring-boot-kit 各 starter 模块新增对外配置键时，落演示配置于 `spring-boot-kit/sample/jevokit-sample/src/main/resources/application.yml`（kit 的示例应用）

## 偏好描述

新增对外配置键时，不能只写绑定类（@ConfigurationProperties）——必须在示例应用的 application.yml 里给出**可用的演示默认值**，并加注释说明：
1. 段首注明"演示默认值，生产环境必须替换"（密钥类敏感值尤其必须标）
2. 每个键注明格式要求（如 Base64 / 16 字节 / 可选长度）

目的：拿到 kit 的人打开 application.yml 就知道每个配置怎么写、能不能跑，不需要翻源码猜。

## 认可场景

2026-09-10，SecurityUtil 加解密密钥（`springbootkit.crypto.*`，同日改名 `spkit.crypto.*`）配置来源讨论中，用户原话："可以在application.yml给默认值，不然人家拿过来也不知道怎么用，注释生产要改就行"。

## 反例（什么算违背）

- 新增配置键只写绑定类/README，示例 yml 不落演示配置
- 演示配置给 `<your-key-here>` 式占位符（用户要的是拿来就能跑的默认值，不是占位符）
- 密钥类演示值不加"生产必须替换"注释
- 误解为"往库里恢复内置密钥"——**库代码保持无内置密钥**（原内置密钥已因泄露移除、视为不可信）；演示默认值只落在示例应用的配置文件里，两者不冲突

## 参考实现

`spring-boot-kit/sample/jevokit-sample/src/main/resources/application.yml` 的 `crypto:` 段（2026-09-10 任务落地）。
