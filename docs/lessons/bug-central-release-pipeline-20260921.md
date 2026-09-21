# 发布踩坑：Maven Central 首发五连障（2026-09-20/21）

- **根因类别**：契约不一致（发布管线配置与 Central Portal 实际要求不符）+ 未复用已有能力（发布手册已写两步序，执行时少等了半步）
- **日期**：2026-09-20 ~ 2026-09-21
- **影响**：jevokit 3.5.0.0 首发耗时两天、五次失败重试；每次失败都上传一个不完整/失败的 deployment 需手工清理

## 现象与根因链（按暴露顺序）

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | deploy 输出 `Skipping artifact deployment`，BUILD SUCCESS 但什么都没发 | maven-deploy-plugin 被主 build 配 `<skip>true</skip>`（私服时代遗留），而 central-publishing 插件缺 `<extensions>true</extensions>`——不接管 deploy 生命周期，publish 目标根本不绑定，**静默跳过** | central-publishing 补 `<extensions>true</extensions>`（BOM + 主 pom 两处） |
| 2 | 401 `{"error":{"message":"Invalid token"}}` | settings.xml 里 central server 的 token 是过期旧值；手工复制新 token 时又只复制到掩码片段（`ATcryW` 6 字符） | Portal 重新 Generate User Token，完整 username/password 写入 settings.xml |
| 3 | 主 bundle 校验 FAILED（无错误详情） | **两步发布序少走了半步**：BOM 上传校验通过后没等 Portal 手动 Publish，直接传主 bundle。主 reactor 的 parent import 了 BOM 坐标，Central 在线解析时 BOM 不在仓库 → 整包判失败 | BOM 先 Publish（Portal 手动点），确认 repo1 可解析后再传主 bundle |
| 4 | 主 bundle 校验 FAILED，Portal 显示 `Project name is missing`（rocketmq 模块） | 20 个模块只有 jevokit-rocketmq 的 pom 漏 `<name>`——B 档重构批量补元数据时漏了这个（当时是脚本扫的，rocketmq 模块结构特殊被跳过） | 补 `<name>jevokit-rocketmq-spring-boot-starter</name>` |
| 5 | （伴随发现）clean 全量编译失败：`HtmlSafeList#isSafeAttribute` 覆写无法缩小可见性 | **jsoup 1.15.3→1.23.2 升级时该方法从 protected 提升为 public**。当时"测试全绿"是假象：主 reactor 解析的是本地仓库**旧 BOM 快照**（jsoup 1.15.3 实际未生效），BOM install 为正式版 3.5.0.0 后依赖坐标变化才编译失败 | 覆写 `protected` → `public` |

## 关键教训

1. **静默跳过比报错可怕**：缺 `extensions=true` 时 BUILD SUCCESS 但零上传——发布类插件必须核对"目标真的绑定到生命周期了"（看日志里有没有 `publish (injected-central-publishing)` 字样）。
2. **"测试全绿"可能是旧依赖的假象**：BOM 升级依赖版本后，本地仓库的旧 SNAPSHOT 会让 reactor 继续用旧版本——**升级 BOM 后必须先 `mvn install` BOM 再 clean 全量构建**，否则兼容性验证是空转。jsoup 兼容性检查当时就栽在这。
3. **Central 的校验错误只在 Portal 页面可见**：CLI 只有 `failed while publishing` 一句话。失败后第一时间去 https://central.sonatype.com/publishing/deployments 点开看具体错误清单，别瞎猜（本次第 4 障猜了 BOM/元数据/签名三个方向，全是错的）。
4. **两步发布序是"两步半"**：BOM 上传 → 校验通过 → **Portal Publish → repo1 可解析** → 才能传主 bundle。手册写了顺序要求但没强调"Publish 完成后才能下一步"，本次补进 release-central.md。
5. **手工复制凭据不可靠**：Portal 的 token 掩码显示，手动选中复制必漏。让用户用页面复制按钮拿完整值。

## 防范措施

- 发布手册（docs/release-central.md）已固化顺序：BOM Publish 且 repo1 HTTP 200 后才可传主 bundle
- 升级 BOM 依赖后的验证流程：先 install BOM → 主 reactor `clean` 全量编译（非增量）
- 发布失败先查 Portal deployment 错误清单，不盲试
- 模块 pom 元数据齐全性：`for f in $(find . -name pom.xml); do grep -L "<name>" $f; done` 一条命令可全检

## 相关

- 发布配置修复提交：32c1f8d（extensions=true、插件 0.8.0、rocketmq 补 name、HtmlSafeList 可见性）
- 翻版提交：5ad136f（3.5.0.1-SNAPSHOT + README 徽章）
- 最终成功 deployment：26c39012（parent+18 模块）+ BOM（04827a8b）
