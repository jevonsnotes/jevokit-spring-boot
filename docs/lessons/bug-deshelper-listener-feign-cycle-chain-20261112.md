# 三层连环遮挡 bug：desHelper listener 身份环 + guard 类型双命中（jevokit 3.5.2.2/3.5.3.0 两连回归）——2026-11-12

- **根因类别**：契约不一致（bean 身份/注入方式与生态组件的隐式交互）
- **日期**：2026-11-11 ~ 11-12
- **影响**：使用方（gd-yjs，Boot 3.5.16 + spring-cloud-openfeign + actuator + springdoc）从 3.5.2.1 升级后启动失败，三连发版（3.5.3.1→3.5.3.2→3.5.3.3）才根治

## 现象 / 复现

- 3.5.3.0/3.5.3.1：容器循环依赖（环含 desHelper → 3.5.3.1 消除 desHelper 节点但业务环仍在）
- 3.5.3.2（环修复后）：`NoUniqueBeanDefinitionException`（requestMappingHandlerMapping + controllerEndpointHandlerMapping 双命中）
- 最小工程复现（Boot 3.5.16 + openfeign 4.3.3 + jevokit-desensitization）：3.5.2.1 启动成功 / 3.5.2.2 环 / 3.5.3.1 环 / 修复版启动成功——**版本对照矩阵是定位主力**

## 根因（三层）

1. **层一（3.5.2.2 引入）**：`DesHelper implements ApplicationListener<ContextRefreshedEvent>`。接口身份使其进入多播器 listener 名单；spring-cloud 每个 Feign client 的子上下文 refresh 会向主上下文传播该事件，事件按**类型**匹配（不认来源）→ 回调被提前触发 → 回调内按类型拉起业务权限拦截器 → 业务链穿过正在创建的 Feign client → 环
2. **层二（3.5.3.0 引入，被层一遮挡）**：`JSecureUploadGuard` 按具体类型 `RequestMappingHandlerMapping` 注入 provider，actuator 环境下 `controllerEndpointHandlerMapping`（子类）双命中 → NoUnique。层一修复前启动走不到这里，修好层一它才暴露
3. **层三（排查方法教训）**：第二层 bug 的 NoUnique 报错（Boot FailureAnalyzer）不显示依赖方，一度被归因到 springdoc+actuator 的已知兼容问题——**使用方"3.5.2.1 正常启动"才是决定性证据**（组件没变、只有 jevokit 版本变 → 必然是 jevokit 3.5.3.0 新增的组件）

## 关键教训

1. **一个 bug 可能挡着下一个**：修好第一个问题后出现"新"报错 ≠ 修坏了，先查它是否一直被前一个遮挡（启动链越靠后的问题越晚暴露）
2. **"低版本正常"是最高效的定位证据**：同工程切换 jevokit 版本对照（3.5.2.1↔3.5.3.x），变量唯一化，直接锁定引入版本（git diff 两个 tag 间该模块的变更）
3. **`implements ApplicationListener` 与 `@EventListener` 不等价**：前者进 listener 名单（事件可强制拉起未创建的 bean），后者注册晚（全体单例就绪后）、不会连坐创建链——**回调会拉起业务链的 bean 一律禁用接口形态**（DesHelper 类头已固化注释）
4. **按具体类型注入 Spring MVC 基础设施 bean 要防子类生态**：`RequestMappingHandlerMapping` 在 actuator/springdoc 等场景有多实现候选，**按标准 bean 名取**（`requestMappingHandlerMapping`）而非按类型
5. **复现工程没复现 ≠ 结论错**：repro 缺少触发条件（actuator 第二映射的注册条件、暴露配置等）时别急着推翻假设，先用"版本对照 + 使用方证据"定方向

## 防范措施

- kit 组件需要 MVC 基础设施 bean 时：按 bean 名（`BeanFactory.getBean(name, type)`），不按类型注入
- 需要"容器就绪"回调的组件：`@EventListener(ContextRefreshedEvent.class)` 方法形态，禁止 implements 接口
- 重大改造（如 3.5.2.2 死锁修复）的发布说明中提醒使用方：升级跨度大时逐版试启动（3.5.2.1→3.5.2.2→…），别跳版
- 回归：DesHelperCycleBreakTest（Feign 环形态）、JSecureUploadGuardTest#actuatorLikeSecondHandlerMappingIgnored（双映射）

## 相关

- 修复链：3.5.3.1（Provider 延迟，只摘环中节点，不彻底）→ 3.5.3.2（@EventListener 根因修复）→ 3.5.3.3（guard 按 bean 名）
- 复现工程：F:/tmp/feign-cycle-repro（Boot 3.5.16 + openfeign + desensitization，`-Djevokit.version` 切版本对照）
