# 发布 Maven Central 操作手册（jevokit-spring-boot）

> 面向 `io.github.jevonsnotes` 命名空间首发（版本 `3.5.0.0`）。发布主体：jevokit-parent 下 18 个库模块 + sample（不发布）+ 独立 BOM（jevokit-dependencies）。sample 应用不发布（`maven.deploy.skip=true`）。

## 前置条件（每次发布前逐项核对）

1. **GPG 密钥**：keyId `71080C224E332E21`（rsa4096，jevonsnotes \<jevonsnotes@163.com\>），已上传 keyserver。
   - 本机密钥环位置：`C:\Users\picc\.gnupg`（Windows Git 自带 gpg 2.4.7，2026-09-16 已从 cygwin 侧合并私钥/公钥/trustdb；cygwin 侧原始目录 `D:\install\cygwin64\home\picc\.gnupg` 保留，备份 `~/.gnupg.bak-20260916`）
   - 验证：`gpg --list-secret-keys --keyid-format long` 能看到该 key 即可
2. **settings.xml**（`C:\Users\picc\.m2\settings.xml`）已配两段：
   - `<server><id>central</id>` —— Sonatype Central 的 user token（username/password）
   - `<server><id>gpg.passphrase</id>` —— 私钥口令（passphrase）
3. **公钥可检索**：`https://keys.openpgp.org` 或 keyserver.ubuntu.com 搜 `71080C224E332E21` 可查到（Central 校验签名时在线取公钥）

## 两步发布序（顺序不能反）

**为什么**：jevokit-parent 的 `<dependencyManagement>` 以 `import` 方式引用 BOM；BOM 的 parent 是 spring-boot-starter-parent（Boot 官方）。若 parent 先 publish，Central 端解析 parent 的 BOM import 会指向尚不存在的坐标，校验失败。

```bash
# 第一步：BOM（独立 reactor，先 install 到本地再发布）
cd jevokit-spring-boot-dependencies
mvn -Prelease clean install            # 干跑校验：pom 签名产出即可
mvn -Prelease clean deploy             # 真发布（发布到 Central 门户，需在 portal 手动或自动 publish）

# 第二步：parent + 全部库模块（在 jevokit-spring-boot 聚合根）
cd ../jevokit-spring-boot
mvn -Prelease clean deploy
```

注意：
- `-Prelease` 才激活 gpg 签名 + central-publishing 插件；日常构建（无 profile）零签名零发布开销
- sample 模块 `maven.deploy.skip=true`，deploy 时自动跳过（central-publishing 同样尊重该跳过）
- 首次发布若 Central 校验慢，去 https://central.sonatype.com 的 Publishing Portal 看构件状态

## 版本翻转（每次发版）

```bash
# 1. SNAPSHOT → 正式版（两个 reactor 都要翻）
mvn versions:set -DnewVersion=3.5.0.0 -DgenerateBackupPoms=false    # 在 jevokit-spring-boot/ 与 jevokit-spring-boot-dependencies/ 各执行一次
# 2. 走上面"两步发布序"完成 deploy
# 3. 发完立即翻回下一轮开发版（避免主干长期停正式版号）
mvn versions:set -DnewVersion=3.5.0.1-SNAPSHOT -DgenerateBackupPoms=false   # 同样两处
```

## 发布后动作

1. **GitHub tag**：
```bash
git tag -a v3.5.0.0 -m "Release 3.5.0.0"
git push origin v3.5.0.0
```
2. **README 坐标徽章**：在 `jevokit-spring-boot/README.md` 顶部加（示例）：
```markdown
[![Maven Central](https://img.shields.io/maven-central/v/io.github.jevonsnotes/jevokit-core.svg)](https://central.sonatype.com/artifact/io.github.jevonsnotes/jevokit-core)
```
   并把 README 中 `<dependency>` 示例的 `<version>` 从占位改为实际版本或 `3.5.0.0`。

## 构建产物核对单（发布前干跑即验）

每次正式 deploy 前，先在本地跑一遍核对（产物数以 3.5.0.0-SNAPSHOT 期为基准：19 模块（18 库 + sample）+ parent + BOM）：

| 项 | 预期 | 核对命令 |
|---|---|---|
| sources jar | 18（security-all 无源码属正常） | `find . -path "*/target/*-sources.jar" \| wc -l` |
| javadoc jar | 18（security-all 无源码自动跳过，不再产出空壳） | `find . -path "*/target/*-javadoc.jar" \| wc -l` |
| jar 签名 | 55（18 主 + 18 sources + 18 javadoc + sample 主） | `find . -path "*/target/*.jar.asc" \| wc -l` |
| pom 签名 | 20（19 模块 + parent） | `find . -path "*/target/*.pom.asc" \| wc -l` |
| 抽验签名 | Good signature | `gpg --verify <x>.jar.asc <x>.jar` |

BOM 独立核对：`mvn -Prelease clean verify` 后 `target/*.pom + *.pom.asc` 各 1，`gpg --verify` 通过。

## 已知事实（不必当问题处理）

- `security-all` 是纯聚合模块（无 Java 源码）：sources/javadoc 均自动跳过（聚合器扁平化后无空壳产物）
- `rocketmq` 模块 javadoc jar 83 文件（注释不规整但 doclint=none 只拦语法错误，生成成功）
- javadoc `Null charset name` 陷阱：javadoc 插件不配 charset/encoding 参数（读 `project.build.sourceEncoding` 属性，已在 parent properties 统一声明 UTF-8）；插件里显式配 charset 在 fork 执行时反而触发 NPE
- gpg 2.4.7（Git for Windows）+ 本机 keyboxd 曾不兼容 cygwin 侧密钥——已通过密钥环合并解决，如再遇 `No secret key` 先 `gpg --list-secret-keys` 确认密钥在哪个 homedir
