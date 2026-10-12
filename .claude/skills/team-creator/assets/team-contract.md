<!-- generated-by: team-creator v2 -->
# 团队组成契约 — {{team_name}}

> 本文件由 team-creator 技能生成，**只记录本次团队的人员组成**（哪些角色、各几名、实例名）。
> 通用协作规则（领导职责、SendMessage 沟通协议、跨领域求证反驳、BUG 闭环）**不在本文件**——它们刻在 team-creator 技能的 SKILL.md "协作规则"章节里，每次触发技能自动生效，无需重复维护。
> 本文件的作用：① 第 -1 步复用检查的依据；② 新会话里"上次拉了哪些人"的存档。

## 1. 团队组成

| 角色 | agent-type | 实例名 | 数量 |
|---|---|---|---|
{{#each members}}
| {{label}} | `{{agent_type}}` | `{{instance_name}}` | {{count}} |
{{/each}}

**成员合计**：{{total_members}} 名（领导不计入）。领导由主会话担任。

## 2. OpenSpec 状态

**OpenSpec 启用状态**：{{openspec_status}}
{{openspec_note}}

{{#if openspec}}
所有成员实施时严格遵守 OpenSpec 规范（需求/变更先落 `openspec/` 规格再动代码；接口契约由架构师定义入规格；测试依据验收标准；可用 `/opsx:propose`、`/opsx:apply`、`/opsx:verify`、`/opsx:archive` 走变更流程）。
{{else}}
成员按常规工程规范执行；如需启用，可重新运行 team-creator 并勾选 OpenSpec。
{{/if}}

## 3. 任务派发清单（速查）

领导派发任务时，按角色对照：

{{#each members}}
- **{{label}}** (`{{instance_name}}`)：{{responsibility}}
{{/each}}

## 3.5 质量职责（防 bug / 不重复踩坑）

开发工程师与测试工程师除上表职责外，还须遵守"质量四项"——**复用优先、自测义务、缺陷闭环、经验沉淀**，完整定义见 team-creator 技能 `references/roster.md` 的"开发/测试工程师质量职责"章节。此外，开发工程师与 UI 设计师还须遵循偏好学习（见下文 3.6 节）。

- **BUG 闭环跑完整 7 步**：救火（发现→修→复测）+ 防火（根因→同类排查→用例固化→经验沉淀），详见 SKILL.md "协作规则 > BUG 修复闭环"。
- **经验沉淀落点**：根因与防范措施写入 `docs/lessons/`（项目级经验库）。团队开局与 BUG 闭环第 7 步都要用它——这是不重复踩坑的核心机制。若项目尚未初始化该目录，team-creator 第 4b 步会自动创建。

## 3.6 偏好学习（不重复纠正）

开发工程师与 UI 设计师除上表职责外，还须遵循"偏好学习双闭环"——**实现前查偏好、用户认可即沉淀**：

- **开局读偏好**：领导派活前读 `docs/preferences/README.md` 索引，把相关已认可偏好纳入派发指令。与读 `docs/lessons/` 并行——教训防犯错，偏好保一致。
- **实现前查偏好**：开发/UI 工程师每次实现/出稿前，查与本任务相关的偏好条目，主动遵循用户已认可的风格。
- **用户认可即沉淀**：用户纠正（"不要这样，应该……"）或认可（"对，就这样"）某风格时，领导或对应成员主动写一条 `docs/preferences/pref-<简述>.md` 并追加索引。只记录用户真实表达过的，不臆断用户审美。
- **与 `docs/lessons/` 的分工**：`docs/lessons/` 记"错"（bug 根因）防复发，`docs/preferences/` 记"对"（用户认可的风格）促复用。两者都是项目级知识库，方向相反，不要混。完整定义见 SKILL.md 领导行为第 1、9 条与 `references/roster.md` 的"开发/UI 职责"。若项目尚未初始化偏好库，team-creator 第 4c 步会自动创建。

## 4. 跨领域求证路由（按本团队实际角色裁剪）

> 完整反驳规则见 SKILL.md "协作规则 > 跨领域求证与反驳"。下表只保留本团队已拉起角色涉及的路径。

{{#each routes}}
| {{type}} | {{pair}} |
{{/each}}
