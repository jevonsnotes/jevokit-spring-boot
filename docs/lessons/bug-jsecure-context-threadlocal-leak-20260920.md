# JSecureContext 三个 ThreadLocal 请求结束后不清理——线程池复用串请求

- 根因类别：异常路径未处理（清理点放在几乎不可达的分支 + 拦截器空实现，javadoc 与实现不符）
- 状态：已修复·已沉淀
- 相关模块：`jevokit-spring-boot/starter/security/jevokit-api`（interceptor / advice / context）

## 现象 / 复现

静态阅读发现清理点"存在"，但行为实验（`JSecureContextThreadLocalLeakTest`，@WebMvcTest 切片）定论三连：

1. **成功请求后残留**：成功的 JSecure 请求结束后，`JS_REQUEST_THREAD_LOCAL` / `CLIENT_THREAD_LOCAL` / `LOG_PREFIX` 三个 ThreadLocal 均非空（含解密明文与终端私钥滞留）。
2. **线程复用串终端**：同线程先处理终端 A 的合规请求、再处理终端 B 的正确签名请求，B 被"请求头验签失败"（code=999）拒绝——`getCurrentClient()` 命中缓存直接返回 A 的配置，用 A 的密钥验 B 的签名。
3. **残留 jsr 吞异常**：残留的请求报文使 `EncryptResponseAdviceAdapter#handler` 的 `jsr == null` 守卫失效——非 JSecure 端点的 RuntimeException 被吞成协议 FAIL 响应，且 transNo 沿用上一个 JSecure 请求的值（交易号串号）。

复现方式：MockMvc 的 `perform()` 在调用线程内同步执行整个请求处理，同一测试方法内连续两次 perform 即天然等价于容器线程池同线程复用，无需真实线程池。

## 根因

写入与清理的责任分离出现了"人人都以为对方清理"的空洞：

- `JevokitApiAccessInterceptor` 的 `afterCompletion` / `afterConcurrentHandlingStarted` 是**空实现**，注释却写着"无需清理（本拦截器不持有请求级状态）"——它确实不持有，但它是全链路唯一"必经且最后"的清理点，javadoc 与实现脱节掩盖了缺口；
- `EncryptResponseAdviceAdapter#beforeBodyWrite` 成功分支直接 `return resp`，`JSecureContext.remove()` 只挂在 body 非 `JSecureResponse` 的分支（本适配器 `supports()` 已限定只处理 JSecureResponse 返回，该分支几乎不可达）——典型的"清理点放在几乎不可达的分支"；
- 异常路径由 `handler()` 的 finally 清理兜住了一半场景，使泄漏只在"成功请求 + 线程复用"下爆发，更隐蔽。

即：清理职责被寄托在 advice 的正常分支（非必经）而非拦截器的生命周期回调（必经），异常路径之外的"成功路径"反而成了漏网之鱼。

## 同类隐患排查

全仓 grep `static.*ThreadLocal|ThreadLocal<`（2026-09-20）：

| 位置 | 清理情况 | 结论 |
|---|---|---|
| `JSecureContext`（jevokit-api） | 本次修复：拦截器 afterCompletion + afterConcurrentHandlingStarted | 已闭环 |
| `ConfigHolder` TL_PT/TL_XSS/TL_SQL_INJECT（jevokit-autoconfigure） | PathTraversal/XssClean/SqlInject 三个拦截器均在 afterCompletion + afterConcurrentHandlingStarted 清理 | 无隐患 |
| `DesUtil.PROCESS_TYPE`（jevokit-desensitization） | 两个 wrap 方法内 try/finally 成对 set/remove，自包含 | 无隐患 |
| `SecurityUtil.FILTER_CACHE`（jevokit-core） | add/remove 在 try/finally 栈式成对，集合清空时顺带 remove ThreadLocal | 无隐患 |
| `AuditLogUtils` SRC_SERVLET_PATH/EVENTS（jevokit-audit-log） | 已修复（2026-09-20）：`AuditLogFilter#doFilter` 的 `chain.doFilter` 包入 try/finally，finally 执行 afterHandle（其内部 finally 必调 `clear()`），下游抛受检/非受检异常时事件链与源路径均不再残留，异常原样透传；回归测试 `jevokit-audit-log/src/test/.../filter/AuditLogFilterThreadLocalLeakTest.java`（修复前 3 红 / 修复后全绿） | 已闭环 |
| `AuditLogAspect`（jevokit-audit-log） | 已核查（2026-09-20）：`doAround` 中 `jp.proceed()` 已包 try/finally，finally 调 afterHandle（其内部 finally 必调 `clear()`），异常路径已覆盖，无需修改 | 已闭环（核查通过） |

## 修复要点

- **清理职责放回"必经且最后"的生命周期点**：`JevokitApiAccessInterceptor#afterCompletion` 调 `JSecureContext.remove()`（请求结束/异常均必经）；`afterConcurrentHandlingStarted` 同样清理（异步 dispatch 起始时原线程归还线程池，异步完成后的重新分发由该次 afterCompletion 覆盖）。
- 删除 `EncryptResponseAdviceAdapter#beforeBodyWrite` 里几乎不可达分支中的 `remove()`（语义误导，让人误以为成功路径有清理）；`handler()` finally 的 `remove()` 保留（异常路径提前清理无害，时序上早于拦截器）。
- `handler()` 的 `jsr == null` 守卫与 `getCurrentClient()` 缓存逻辑**不动**：拦截器兜底清理后，缓存生命周期自然收敛为单请求内，守卫恢复有效。

教训：**advice / 异常 handler 的 finally 不是清理的合法挂载点**——前者不保证被调用（supports 不匹配、body 类型不符即跳过），后者只在异常时调用。两者都覆盖不了"成功请求"这条最常见路径。ThreadLocal 上下文的清理必须挂在 afterCompletion（同步兜底）+ afterConcurrentHandlingStarted（异步起始）这对拦截器回调上。

## 回归用例

- `jevokit-spring-boot/starter/security/jevokit-api/src/test/java/io/github/jevonsnotes/jevokit/api/JSecureContextThreadLocalLeakTest.java`
  - 用例 1（怀疑 A）：成功请求后三个 ThreadLocal 应为 null；
  - 用例 2（怀疑 B）：同线程 A 后 B，B 应以自己密钥成功且 servedClientId=B；
  - 用例 3（怀疑 C）：成功 JSecure 请求后非 JSecure 端点异常应正常传播（含手动清理的因果对照）。
- `jevokit-spring-boot/starter/compliance/jevokit-audit-log/src/test/java/io/github/jevonsnotes/jevokit/audit/log/filter/AuditLogFilterThreadLocalLeakTest.java`（同类隐患 audit-log 条目闭环的回归测试）
  - 用例 1/2：下游抛 ServletException / RuntimeException，异常透传且 EVENTS/SRC_SERVLET_PATH 清理为空；
  - 用例 3：同线程复用，异常请求残留的事件不得被下一请求继承（globalKey 不得相同）；
  - 用例 4：正常路径回归——around 关闭仍恰好落一次审计且清理（修复不引入重复清理）。

## 防范措施

- **落地即写"必经清理点"回归测试**：新增请求级 ThreadLocal 上下文类时，同步写一条"成功请求结束后上下文为空 + 同线程第二请求不受第一请求影响"的回归测试（MockMvc 同步执行即可模拟线程复用），不要只靠静态阅读确认"有 remove 调用"。
- 清理代码的位置自检：grep 上下文类的 `remove()` 调用点，必须命中拦截器 `afterCompletion` / `afterConcurrentHandlingStarted`（或 Filter 的 try/finally 包住 `chain.doFilter`）；只出现在 advice 分支、handler finally、业务代码里的都是嫌疑点。
- 注释与实现不符（"无需清理"配空实现）是缺口信号：见到此类注释，先验证"谁清理"的答案是否真实存在。
- 同类上下文类的模板可参照 XssClean/SqlInject/PathTraversal 三拦截器（afterCompletion + afterConcurrentHandlingStarted 双清理）与本次修复后的 JevokitApiAccessInterceptor。
