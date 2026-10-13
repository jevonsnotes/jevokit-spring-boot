# 20261013 证据留档脱敏说明：本机登录用户名字样清理

## 背景

按用户偏好（docs/preferences/pref-no-origin-project-name.md：所有提交文稿不得出现前身定制项目相关内容），本 evidence 目录 6 份 2026-10-12 运行/测试真机留档日志中，Spring Boot 启动横幅（"started by <用户名>"）与 surefire 命令行回显（`C:\Users\<用户名>\...` 临时目录）含操作系统的登录用户名字样，该用户名与前身定制项目名同形，随 openspec/changes/ 入库前统一脱敏。

## 处置

- 范围（6 份，合计 25 处）：
  20261012-acceptance-ac6-fatjar-boot.log（1）、20261012-acceptance-ac6-final-boot.log（1）、
  20261012-acceptance-sample-repro.log（4）、20261012-acceptance-test-final.log（10）、
  20261012-acceptance-test-final2.log（3）、20261012-acceptance-test.log（6）
- 规则：前身项目名同形的本机用户名字样 → `operator`（仅此字样全局替换；大小写变体经核全为小写形态，无遗漏）
- 保持不变：时间戳、PID、版本号、模块路径、用例计数、结论性行为等全部技术事实零改动。

## 效力说明

被替换字样为构建执行机器的登录用户名，不参与任何 AC 断言与验证结论（AC-1~8、G4~G6 的判定对象为编译结果、依赖树、测试计数、MANIFEST 字段等），日志留档的验证效力不受影响。
