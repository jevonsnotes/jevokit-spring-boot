# 提交信息不加 AI 署名（无 Co-Authored-By 行）

- 分类：沟通/流程
- 状态：已认可
- 适用范围：本仓库所有 git 提交（团队成员的全部 commit）

## 偏好描述

提交信息（commit message）**不加任何 AI/Claude 署名行**——不写 `Co-Authored-By: Claude ...`、`🤖 Generated with ...` 之类的尾注。提交信息只描述变更本身。

## 认可场景

2026-09-11，B 档测试重构整批推送成功后，用户原话："提交信息里面不要署名 Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"。

## 反例（什么算违背）

- commit message 尾部出现任何 Co-Authored-By: Claude / Anthropic / AI 生成标识
- PR 描述带 🤖 Generated with 尾注

## 参考实现

2026-09-11 之后的提交信息形态：纯变更描述（标题 + 正文要点），无署名尾行。
