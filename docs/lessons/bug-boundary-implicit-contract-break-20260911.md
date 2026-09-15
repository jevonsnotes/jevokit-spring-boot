# 跨边界的隐式契约断裂（值不变/资源不死/编码一致，三合一）

- 根因类别：契约不一致
- 状态：已修复·已沉淀
- 相关模块：core/PageUtil（BUG-1）、others/OkHttpUtil（BUG-4/BUG-5）——B 档测试重构 bug 批次，2026-09-15 终验全过

## 现象 / 复现

三条缺陷，三种边界，同一个形状：

| 实例 | 边界类型 | 断裂的隐式假设 | 失败形态 |
|---|---|---|---|
| BUG-1 `PageUtil.consume` 异步路径 | 线程边界（提交 → 工作线程执行） | "闭包里读到的 `page.value` 还是提交时刻的值" | consumer 收到的页号错乱：实测 [1,2,3,4,4]（0 号丢失、4 号重复） |
| BUG-4 `OkHttpUtil.executeStream` | 方法返回边界（方法内 → 调用方） | "方法返回的流是活的" | try-with-resources 块结束即关 Response，调用方拿到死流，readAllBytes 直接抛 IOException |
| BUG-5 `OkHttpUtil.buildFormBody` | 编码边界（String → bytes） | "getBytes() 编出的字节属于目标字符集" | `StrUtil.str(value.getBytes(), charset)`：GBK 默认的 Windows 上往返恒等（缺陷隐形、测试照常绿），UTF-8 平台必乱码 |

## 根因

三件事是同一个错误：**边界处存在一个隐式假设（值不变 / 资源不死 / 编码一致），但它没有在边界处被显式化，于是每条边界各自找了一个不同的断裂方式。**

- BUG-1 的隐式假设藏在闭包捕获语义里：匿名类/lambda 对局部变量的 effectively-final 约束正是编译器对"跨线程值不变"的显式化保护，但 `IntHolder` 是**可变容器**，捕获的是容器引用而非值——保护被绕过，`page.value++` 与工作线程的读取之间无同步。TE-1 的锐化点必须收进本条：**提交动作（`CompletableFuture.supplyAsync`）自带 happens-before，工作线程读到的不是"撕裂值"而是"一致但错误的终值"**——主线程提交后立刻自增，等线程池调度到该任务时快照语义早已丢失。因此该缺陷**可被确定性测试杀死**（修复后断言页号集合不重不漏即可拦截回归），不需要概率性压测去"碰"——把它当竞态去 stress test 是对病灶的误判，确定性断言才是对的防线。
- BUG-4 的隐式假设藏在所有权心智里：try-with-resources 声明"Response 的生命周期 = 本块作用域"，但方法契约要求"返回的流跨出本块仍然可用"——所有权留在块内、资源引用跨出块外，两者矛盾。谁持有谁负责，块级作用域不能持有跨边界资源。
- BUG-5 的隐式假设藏在无参重载里：`value.getBytes()` 的无参重载静默取平台默认编码，调用方"以为"字节属于目标 charset——边界两侧各说各话。且该断裂形态依赖运行平台：默认编码恰好等于目标编码时恒等隐形（本机 GBK↔GBK 案例证明"测试绿"不等于"没有错"），平台一换即炸——**环境相关的隐形是最危险的一档，因为发现它的机器和埋下它的机器通常不是同一台**。

三条的共性：编译器都不报错（引用捕获合法、返回引用合法、无参重载合法），隐式假设没有任何机制为其背书——它只活在写代码那一刻的作者脑子里，边界一多（线程池、方法返回、编码转换）就没人再看见它。

## 同类隐患排查

按三种边界在全仓扫"跨边界交接点"：

- **线程边界**：`InheritTransactionSupplier` 匿名子类族——`PageUtil.consume` 是唯一在闭包内读共享可变容器的调用点，已修复；其余用法（捕获 effectively-final 变量）安全。
- **返回边界**：返回 `InputStream`/`Reader`/`Response` 型资源的公开方法——`OkHttpUtil.downloadAsStream`/`postDownloadAsStream`（已修，其余模块无同类形态；`getRedirectUrl` 在块内读完才返回 String，安全）。
- **编码边界**：`getBytes()`/`new String(bytes)` 无 charset 调用点——除 BUG-5 一处外，`buildUrlByQueryParams` 等均显式带 charset，干净。

## 修复要点

- BUG-1：提交前值拷贝——`final int pageIndex = page.value;`，匿名 supplier 读 final 快照；同步路径行为不变。修复取向：**把隐式的"值不变"变成显式的不可变拷贝**，而不是加锁（锁能防撕裂但防不了"读到错的终值"，治标）。
- BUG-4：所有权随资源走——新增 `ResponseBodyStreamWrapper`（FilterInputStream 持有 Response，`close()` 先关流再关 Response），调用方关流即归还连接；非成功/异常路径就地 close。保留真流式语义（对比全量缓冲的内存代价），公开 API 签名未动。
- BUG-5：显式指定到正确层——`new FormBody.Builder(charset)` 由 okhttp 在编码层原生按该 charset 百分号编码表单值，删除手写的 `getBytes()` 无参往返。注意字面改法 `value.getBytes(charset)` 会变成恒等死代码（getBytes(c) 再按 c 解码=原串）——修这类缺陷要修"意图的正确落点"，不是把错误表达式改成另一个合法表达式。

## 回归用例

- BUG-1：`PageUtilTest.consumeAsyncCollectsInPageOrder`——consumer 实际收到的页号断言 `containsExactlyInAnyOrder(0,1,2,3,4)`（集合不重不漏，确定性拦截 [1,2,3,4,4] 型回归）；有序契约挂在 `consumeAsync` 返回值上（futures 按提交序 join，确定序）。**不断言 worker 线程写入顺序**（调度乱序合法——本条曾因 containsExactly 过强被判 flaky，教训：确定性缺陷用确定性断言，别把合法乱序也锁死）。
- BUG-4：`OkHttpUtilTest.downloadAsStream_returnsReadableStreamAndClosesResponse`（返回流可完整读取）、`downloadAsStream_earlyCloseIsSafeAndIdempotent`（不读直接关、关两次均安全）、`postDownloadAsStream_readable`。
- BUG-5：`OkHttpUtilTest.post_formValuesEncodedWithGivenCharset`——GBK 表单（中文+emoji），MockWebServer 录制原始字节按 GBK 解码断言与 `URLEncoder.encode(value, "GBK")` 一致，跨平台稳定（断言不依赖运行机默认编码）。

## 防范措施

1. **跨线程提交的闭包，可变状态必须在提交前做 final 值拷贝**；共享容器（Holder/AtomicReference/数组）被闭包捕获即视为可疑点。回归断言用确定性集合语义（不重不漏），不依赖时序也不锁死合法乱序。
2. **跨方法返回的资源，所有权必须随返回值转移**：块级 try-with-resources 只管理"块内用完"的资源；要跨出边界的资源由包装对象接管关闭职责（包装流/包装句柄），并在 javadoc 写明"调用方负责 close"。
3. **跨编码边界禁用无参 getBytes()/new String()**：一律显式 charset；评审时可把 `getBytes()`（无参）直接列为必改项。字符集相关测试须在断言里显式指定编码，保证在任意默认编码的机器上结果一致。
4. 总纲：**边界交接（线程提交/方法返回/编解码）时逐项检查隐式假设并显式化**——值不变→final 拷贝，资源不死→所有权转移，编码一致→显式 charset。编译器不背书的假设，必须自己写成代码或测试。
