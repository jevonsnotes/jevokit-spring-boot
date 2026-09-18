# 测试报告编码不一致：Surefire XML 报告 UTF-8 声明与 JVM 平台默认编码冲突

- 根因类别：契约不一致
- 状态：已修复·已沉淀
- 相关模块：parent pom（maven-surefire-plugin argLine）、sample/jevokit-sample、starter/compliance/jevokit-desensitization

## 现象 / 复现

Windows 本机（JDK 17，file.encoding=GBK）运行测试后，surefire 生成的 TEST-*.xml 报告中文乱码：

| 报告位置 | 原始内容 | 实际显示 |
|---|---|---|
| DesensitizationE2ETest system-out | 张\*丰（脱敏日志） | 寮?\*涓? |
| DesensitizationE2ETest system-out | 张三丰（请求体 JSON） | 张三丰（正常） |
| DesUtilDesTest system-out | 脱敏模式匹配失败... | 模式匹配失败...（乱码片段） |

同一报告内：MockMvc 打印的请求体 `{"name":"张三丰"}` 显示正常（Jackson/JSON 按 UTF-8 编码），但 logback ConsoleAppender 通过 System.out 输出的中文日志行乱码——同一文件混合编码。

surefire 报告 properties 可见：`file.encoding=GBK`、`sun.jnu.encoding=GBK`、`native.encoding=GBK`。

## 根因

三重断裂叠加：

1. **JVM 默认字符集 = 平台编码**：JDK17 默认 `file.encoding` 随操作系统，Windows 为 GBK。
2. **logback ConsoleAppender 走 System.out 按平台默认编码输出**：中文日志行以 GBK 字节写入标准输出流。
3. **Surefire XML 报告声明 UTF-8 但原样转储 stdout 字节**：TEST-*.xml 文件头部声明 `encoding="UTF-8"`，但 surefire 直接拿 System.out 的原始 GBK 字节写入，导致 GBK 字节被按 UTF-8 解读 → 乱码。

MockMvc 的请求体 JSON 不受影响，因为它通过 Jackson（显式 UTF-8）写入，不经过 System.out 编码链路。

## 同类隐患排查

与历史教训 BUG-5（`getBytes()` 无参重载静默取平台默认编码跨平台乱码）同属**编码边界契约不一致家族**——边界两侧各说各话（默认编码 vs 显式声明编码），且断裂形态依赖运行平台。

全仓按以下模式排查同类隐患：

- `getBytes()` / `new String(bytes)` 无 charset 参数的调用点——已清理，存量零遗漏。
- 构建插件/测试框架的输出编码配置——父 pom properties 已有 `project.build.sourceEncoding=UTF-8` / `project.reporting.outputEncoding=UTF-8`，但这两项只影响 compiler/javadoc/source 插件，不覆盖 JVM 进程级的 `file.encoding`。

## 修复要点

在父 pom `pluginManagement` 的 maven-surefire-plugin 配置中追加 `argLine`：

```xml
<argLine>@{argLine} -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8</argLine>
```

关键设计决策：

- **`@{argLine}` 延迟替换**：jacoco prepare-agent 在初始化阶段设置 argLine 属性传 javaagent 给 surefire。直接写死 argLine 会覆盖 jacoco 的 javaagent 行，导致覆盖率采集静默失效。使用 `@{argLine}` 延迟替换保留 jacoco 内容。
- **properties 空默认值**：在父 pom properties 中新增 `<argLine></argLine>`，确保 jacoco 未激活时 `@{argLine}` 延迟替换不出字面量 JVM 启动错误。
- **stdout/stderr.encoding**：JDK19+ 新增属性，JDK17 忽略无害，属向前兼容双保险。
- **不修 ANSI 颜色码**：logback `%green()`/`%cyan()` 的 ANSI 颜色码以 `&amp#27;[32m` 形式出现在报告 CDATA 中，属于 XML 字符转义（ESC 被双转义），报告合法可读，不影响中文正确性。消除需改 logback pattern 或配置 `spring.output.ansi.enabled=never`，属独立议题。

## 回归用例

无需新增测试代码——该缺陷的回归拦截应在 CI 层做**报告编码完整性检查**：

**验证方式**（已作为复测用例固化）：

```bash
# 1. 重跑测试并清空旧报告
rm -rf */target/surefire-reports
mvn test -pl <module>

# 2. strict UTF-8 解码 + U+FFFD 计数
for f in */target/surefire-reports/TEST-*.xml; do
  iconv -f UTF-8 -t UTF-8 "$f" 2>/dev/null || echo "FAIL: $f not strict UTF-8"
  count=$(grep -c $'\xEF\xBF\xBD' "$f" || echo 0)
  [ "$count" -eq 0 ] || echo "FAIL: $f has $count U+FFFD replacement chars"
done

# 3. 确认中文正确显示（抽样）
grep '张\*丰' sample/jevokit-sample/target/surefire-reports/TEST-io.github.jevonsnotes.jevokit.sample.e2e.DesensitizationE2ETest.xml

# 4. 确认 jacoco 未破坏
ls <module>/target/jacoco.exec
mvn jacoco:report -pl <module>  # HTML 报告正常生成
```

## 防范措施

1. **构建基建必须显式设置编码**：父 pom properties 中 `project.build.sourceEncoding=UTF-8` / `project.reporting.outputEncoding=UTF-8` 是必要但不充分的条件——JVM 进程级 `file.encoding` 仍需在 surefire argLine 中强制。
2. **surefire argLine 须用 `@{argLine}` 延迟替换**：凡使用 jacoco prepare-agent 的项目，argLine 必须保留 `@{argLine}` 前缀，否则覆盖率静默失效。父 pom properties 须给 argLine 空默认值兜底。
3. **CI 回归检查**：测试报告生成后自动做 strict UTF-8 解码验证（iconv 校验 + U+FFFD 计数），确保报告编码一致性。
4. **总纲**：跨平台项目（尤其是 Windows CI）默认字符集依赖是隐蔽的编码缺陷根因家族，任何"平台默认编码"隐式假设必须在边界处显式化为编码声明——编译器/构建工具不背书的假设，必须自己写成配置。
