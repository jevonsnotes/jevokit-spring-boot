# commit 信息除专业术语和代码外，要用中文

- 分类：沟通/流程
- 状态：已认可
- 适用范围：本仓库所有 git commit 的 message

## 偏好描述

commit message 的主体描述用中文，专业术语（如 `javadoc`、`@Override`、`verify -Prelease`、`@ConditionalOnBean`）和代码标识符（如 `jevokit-core`、`io.github.jevonsnotes.jevokit.*`）保持原文。

结构建议：
- 标题行：中文概括（可含代码标识符）
- 正文：中文说明改动内容、原因、验证方式
- 专业术语/代码片段保持原文

## 认可场景

2026-09-17，注释战收尾提交后，用户指出 commit 信息应该用中文，原话："commit信息除专业术语和代码外，要用中文"。

## 反例（什么算违背）

- commit message 全英文（如 "Method javadoc completion round: all visibility levels"）
- 过度中文化专业术语（如把 `@Override` 写成"重写注解"）

## 参考实现

```
方法 javadoc 补齐轮：全可见性覆盖（约 186 个方法）

三路并行，按模块分区：
- core + autoconfigure（+55）：SecurityUtil 的 FILTER_CACHE 四件套
  防循环引用理由、异常构造器家族、request wrapper 内部方法
- security + compliance（+48）：DesensitizationAspect.desensitize
  注解解析优先级链（字段级 > 方法级兜底 > 属性名规则兜底）、
  desensitizeLogAfterClone 递归规则、processContentLimit -1/0/ABORT 语义
- data + integration + sample（+83）：redis TTL 解析/到期预判/键序列化、
  LockAspect 私有提键链、okhttp execute 族错误分派、workflow 部署视图懒加载 NPE 防护

注：private 方法 javadoc 服务于源码阅读和 IDE 悬浮（标准 javadoc 只渲染 public/protected）——public 渲染已抽验

恢复工程：agent 经历 4 次 API 瞬断靠增量落盘 + 幂等替换零损失；
盲插恢复 bug 把 17 处 @Override 落到错误位置（编译期暴露），
以 git HEAD 为唯一事实源修复（22 处归位已字节级验证）

闸门：7 模块 clean test 全绿（sample 17/17 含防线双测）/
okhttp + workflow + redis verify -Prelease SUCCESS /
HTML 渲染抽验 / diff 纯度：零逻辑改动（注解重排对字节相同）
```
