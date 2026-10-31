# 安全组件边界缺陷六连（jevokit-api 上传+门面）——2026-10-31

- **根因类别**：契约不一致（客户端异常类型不统一）+ 边界条件遗漏（外部输入未做字符集白名单直入协议字段）
- **日期**：2026-10-31
- **影响**：实现自测全绿但边界测试实证 6 缺陷（12 红用例），含 2 个 wire 级走私实锤

## 缺陷与根因（测试工程师实证，后端修复）

| # | 缺陷 | 根因分类 | 教训 |
|---|---|---|---|
| 1 | 非法响应体裸抛 fastjson JSONException，消息回显整个响应体 | 契约不一致 | 客户端 SDK 的所有出口异常必须统一类型；第三方库异常（JSON/HTTP）每个边界都要包装；错误消息回显原始输入=信息泄露面 |
| 2 | 默认载体非 200 不感知（hutool 404 还捏造响应文本） | 异常路径未处理 | "传输层失败"与"协议层失败"必须可区分；选型 HTTP 客户端时先审它对非 2xx 的默认行为 |
| 3 | 终端未登记抛裸 RuntimeException | 契约不一致 | fail-fast 做了但异常类型漏统一——一个入口改异常类型时全链路 grep 排查同类 |
| 4 | fileId 含 `"` 在 wire 走私出第二个 filename 属性 | 边界条件遗漏 | **会进入协议字段（头/边界分隔符）的外部输入必须字符集白名单**，转义不可靠；服务端有兜底≠客户端报文可以损坏（中间代理/WAF 解析差异） |
| 5 | fileId 含 CRLF 伪造 part 头行 | 同上 | 同上，双道收紧：客户端入口 fail-fast + 服务端元数据层同口径（防自定义载体绕过） |
| 6 | 重复 meta part 静默 first-wins | 边界条件遗漏 | 安全组件对歧义报文应拒绝而非静默取默认；Spring @RequestPart 的 first-wins 是便利行为不是安全行为 |

另采纳 2 商榷：transNo 不设自动生成（文档与服务端 @NotBlank 矛盾曾致"不设"成死路径）；无扩展名文件未配白名单时放行（必填校验与白名单需求的耦合误伤）。

## 关键教训

1. **"实现完自测绿"≠边界安全**：本次 47 例防线用例覆盖了攻击面正例，但客户端异常路径/字符集/重复字段这类边界全靠系统性边界测试补强才暴露——测试左移的"应然断言"（红即缺陷）比"迁就现状"的断言有效得多
2. **wire 级验证的价值**：走私/CRLF 缺陷在 MockMvc 层不可见，JDK HttpServer 桩捕获实发字节才实锤——涉及协议格式的功能，测试至少要有一层"真字节"断言
3. **测试只写不修**：测试工程师产出缺陷清单（复现/预期/实际/严重级），修复路由给后端，红转绿即回归标准——职责分离让"应然"不被现状绑架

## 防范措施

- 客户端 SDK 新增出口时 checklist：异常类型统一？非 2xx？非法响应体？错误消息不回显原始输入？
- 外部输入进协议字段（multipart filename、header）前过字符集白名单，双端收紧
- 歧义报文（重复 part、缺 part）默认拒绝语义
- 回归：12 红用例已固化在 JSecureClientEdgeTest/JSecureClientNon200CarrierTest/SecureUploadEdgeTest/SecureUploadFilenameWireTest

## 相关

- 修复与 113 用例全绿验证：JSecureApiUtil（handleResponse 包装/assertHttpSuccess/getClient）、UploadChannel+JSecureUploadVerifier（fileId 白名单双端）、verifyNoDuplicateMetaPart
- 行为收紧通告：中文/`/`/超长 fileId 由受理变拒绝（README 已注明迁移）
