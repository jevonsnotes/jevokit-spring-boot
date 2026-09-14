# BOM import 不传 pluginManagement：无版本插件漂移到 Central metadata 的 4.2.0-M1

- 根因类别：契约不一致（依赖链与插件链两条解析链对"基线版本"的契约各自成立，无统一锚点）
- 状态：已修复·已沉淀
- 相关模块：spring-boot-kit/pom.xml（根 pom）、spring-boot-kit-dependencies/pom.xml（BOM）、server/spring-boot-kit-test-server（fat jar 产物）

## 现象 / 复现

去 picc 化重构统一验证阶段（2026-09-10），eng-3 发现 test-server fat jar 的 MANIFEST 显示 `Spring-Boot-Version: 4.2.0-M1`，而依赖基线是 spring-boot-starter-parent 3.5.16（BOM 的 Maven parent）。clean-room 23/23 项测试在此状态下通过。

复现路径：根 pom 无 Maven parent（对 BOM 的 parent 声明历史上被注释）→ BOM 以 import scope 引入 → 插件无版本声明 → 构建时 repackage → 检查 fat jar MANIFEST。

## 根因

Maven 对插件与依赖是**两条独立的版本解析链**：

1. **依赖链**：BOM 的 dependencyManagement（import scope 有效传递）→ Spring Boot 依赖锚定 3.5.16，正确；
2. **插件链**：spring-boot-starter-parent:3.5.16 里管住 `spring-boot-maven-plugin=3.5.16` 的 **pluginManagement 只通过 Maven parent 继承传递，BOM import scope 不传递 pluginManagement**——它只合并 dependencyManagement。聚合根 pom 又无 Maven parent，于是 `spring-boot-maven-plugin` 全链无版本锚点；
3. 无版本插件的回退规则：解析构建仓库 metadata 的 **`<release>`**。实测 Maven Central 该插件 maven-metadata.xml：`<latest>4.2.0-M1</latest>`、`<release>4.2.0-M1</release>`——Spring 里程碑版发布到 Central 且被标记为 release。插件解析为 4.2.0-M1，repackage 时把插件自身的 Boot 版本写入 MANIFEST。

误判陷阱：`<release>` 不等于 GA——Central metadata 的 release 标记会被里程碑版本占据，"metadata 解析=拿稳定版"的直觉在此失效。

## 同类隐患排查

- 根 pom 与 test-server pom 的所有无版本插件逐一核对。**初判与修正（test-engineer 终验证伪初判）**：初判认为 maven-compiler-plugin 等由 Maven 默认版本管住、属可接受的 minor 漂移，仅 spring-boot-maven-plugin 需钉——**证伪**：compiler 插件无版本时回落的是 Maven **内置绑定版本 3.1**（远旧于任何现代预期），`<parameters>true</parameters>` 在其上被静默忽略，字节码无 MethodParameters 属性，Boot 3.2+（Spring 6.1 起仅认 -parameters 产物）隐式参数名绑定全部失效、演示端点运行期 500。已补钉 3.13.0（取根 pom 原注释预留版本）进同一 pluginManagement；
- **教训：无版本插件的回落版本是 Maven 内置绑定版，往往落后主线数个大版本，"可接受的漂移"必须实证功能影响（javap 验证字节码属性），不能凭"核心插件有默认值"的直觉放行**；
- versions-maven-plugin / maven-deploy-plugin 核定为无版本对齐硬约束（deploy 本就 skip），未同批钉死（避免无关变更）；
- BOM 自身不受影响（BOM 有 starter-parent 作 Maven parent，pluginManagement 正常继承）；
- 本地 ~/.m2 只缓存该插件 2.7.18/3.5.6——开发机缓存滞后掩盖过此问题，换机/清缓存即暴露，印证"不可复现构建"定性。

## 修复要点

根 pom（聚合工程唯一有分量的 pluginManagement 锚点）`<build><pluginManagement>` 钉定 `spring-boot-maven-plugin` `<version>3.5.16</version>`，注释写明对齐依据（与 BOM 父级 starter-parent 3.5.16 一致）与根因（import 不传 pluginManagement + 无 Maven parent）。单点修复后，根 pom 自身声明与 test-server 的无版本声明自动锚定。

未选方案：给聚合根 pom 补 starter-parent 作 Maven parent——改动面大（继承整包 pluginManagement/properties，可能引入无关行为变化），且 BOM 与聚合工程的 parent 结构是既有架构决策，不为修一个插件破坏。

## 回归用例

test-engineer 终验覆盖：install -fae + test-server fat jar 重建后检查 MANIFEST `Spring-Boot-Version` 应为 3.5.16（不再是 4.2.0-M1），且 23 项 clean-room 用例保持全绿。检查命令：解包 fat jar `META-INF/MANIFEST.MF` 或 `unzip -p target/*.jar META-INF/MANIFEST.MF | grep Spring-Boot-Version`。

compiler 插件回归（D-5）：重建后 `javap -p -v` 任意 Controller 类方法，LocalVariableTable 之外应含 `MethodParameters` 属性；演示端点（@RequestParam 无显式 name 的绑定场景）应返回 200 而非 500。

## 防范措施

1. **BOM import ≠ Maven parent**：凡以 import scope 引入 BOM 的聚合工程，必须自查"需要版本对齐的构建插件"（尤其 spring-boot-maven-plugin）是否在根 pom pluginManagement 有锚点——新工程脚手架应默认钉定；
2. **插件无版本声明 = 接受 metadata 漂移**：CI 可加一道卡点——构建后校验 `mvn help:evaluate -Dexpression=...` 或直接校验 fat jar MANIFEST 的 Spring-Boot-Version 与依赖基线一致，不一致即失败；
3. **里程碑版会被 Central 标为 `<release>`**：评估"metadata 最新版"策略时，必须意识到里程碑/预发布可能占据 release 标记，不可默认其稳定性；
4. 存量缺陷识别技巧：此类问题在"单一开发机缓存恰好正确"时隐形，clean 环境（新机/清 ~/.m2/CI）首次构建即暴露——clean-room 验证应保留。
