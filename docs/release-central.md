# 发布 Maven Central 操作手册（jevokit-spring-boot）

> 面向 `io.github.jevonsnotes` 命名空间首发（版本 `3.5.0.0`）。发布主体：单 reactor（jevokit-spring-boot/，jevokit-parent）下的 18 个库模块 + BOM 薄壳（jevokit-dependencies）+ sample（不发布）。sample 应用不发布：release profile 的 central-publishing `excludeArtifacts` 按 artifactId 常驻排除（0.8.0 原生参数；它不读取 `maven.deploy.skip`，该属性仅抑制 maven-deploy-plugin，详见"单 bundle 发布序"注意项）。

## 前置条件（每次发布前逐项核对）

1. **GPG 密钥**：keyId `71080C224E332E21`（rsa4096，jevonsnotes \<jevonsnotes@163.com\>），已上传 keyserver。
   - 本机密钥环位置：`~/.gnupg`（Windows Git 自带 gpg 2.4.7，2026-09-16 已从 cygwin 侧合并私钥/公钥/trustdb；备份 `~/.gnupg.bak-20260916`）
   - 验证：`gpg --list-secret-keys --keyid-format long` 能看到该 key 即可
2. **settings.xml**（`~/.m2/settings.xml`）已配两段：
   - `<server><id>central</id>` —— Sonatype Central 的 user token（username/password）
   - `<server><id>gpg.passphrase</id>` —— 私钥口令（passphrase）
3. **公钥可检索**：`https://keys.openpgp.org` 或 keyserver.ubuntu.com 搜 `71080C224E332E21` 可查到（Central 校验签名时在线取公钥）

## 单 bundle 发布序（BOM 合并改造后）

**为什么**：BOM 已并入主 reactor（`jevokit-dependencies` 是 jevokit-parent 下的薄壳 module，不再有独立 reactor）。整个 reactor 一次 deploy 产出一个 deployment，不存在"BOM 先行、主包后至"的顺序依赖——原先两步半的根本原因是 parent 的 BOM import 指向尚未发布的坐标，合并后依赖全部在 bundle 内自洽。

```bash
cd jevokit-spring-boot
mvn -Prelease clean install -DskipTests   # 干跑校验：签名产物齐全即可（只 install 不 deploy）
mvn -Prelease clean deploy -DskipTests   # 真发布（sample 由 excludeArtifacts 自动排除出 bundle，无需 -pl；-DskipTests 为既有有意形态，测试覆盖由日常构建承担）
```

deploy 结束后去 Portal 对该 deployment 点一次 **Publish** 即可（现在只有一个 deployment，无先后依赖）。

注意：
- `-Prelease` 才激活 gpg 签名 + central-publishing 插件；日常构建（无 profile）零签名零发布开销
- central-publishing 插件必须配 `<extensions>true</extensions>`（jevokit-parent 已配，BOM 壳经继承同样生效）：缺了它 publish 目标不绑定 deploy 生命周期，**BUILD SUCCESS 但静默零上传**
- 日志必须出现 `publish (injected-central-publishing)` 字样，才算真的走了发布；deploy 结束时应有 `Uploaded bundle successfully ... deploymentId: ...`
- 校验失败时 CLI 只有 `failed while publishing` 一句话，**具体错误清单只在 Portal deployment 页面**（如 `Project name is missing`），先看页面再对症修，别盲试
- sample 由 release profile 的 `excludeArtifacts`（jevokit-parent 已配）按 artifactId 常驻排除出 bundle：0.8.0 原生参数，release/SNAPSHOT 双路径过滤，sources/javadoc/pom 随主构件同 artifactId 一并剔除（`maven.deploy.skip` 0.8.0 不读取，该属性仅抑制 maven-deploy-plugin）。命令无需 -pl；`-pl '!sample/jevokit-sample'` 可保留作双保险（与参数兼容不冲突），保留它会同时跳过 sample 的编译与测试
- 失败重试会产生多个 deployment：不完整/失败的记得在 Portal Drop，只 Publish 最终完整的那个
- 首次发布若 Central 校验慢，去 https://central.sonatype.com 的 Publishing Portal 看构件状态

## 版本翻转（每次发版）

```bash
# 1. SNAPSHOT → 正式版（单 reactor 一处即可，BOM 壳随 parent 联动）
cd jevokit-spring-boot
mvn versions:set -DnewVersion=3.5.0.0 -DgenerateBackupPoms=false
# 2. 走上面"单 bundle 发布序"完成 deploy
# 3. 发完立即翻回下一轮开发版（避免主干长期停正式版号）
mvn versions:set -DnewVersion=3.5.0.1-SNAPSHOT -DgenerateBackupPoms=false
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

## 发布前干跑演练（每版必做）

正式 deploy 前用 central-publishing 0.8.0 自带的干跑开关走一遍全链路。`-DskipPublishing=true`：照常组 bundle，打印 `Skipping Central ... Publishing at user's request.` 后直接返回，**零上传**：

```bash
cd jevokit-spring-boot
mvn clean deploy -Prelease -DskipPublishing=true -DskipTests
```

为什么必须是 deploy 形态：gpg:sign 绑定在 **verify** 阶段，`clean package` 到不了签名（实测 package 后 `.asc` 为 0）；deploy 干跑一层覆盖 打包 → javadoc → 签名 → bundle 组装 → 跳过上传 全链路。

演练核对要点：

1. **签名产物齐全性**：按下文核对单逐项对数（`.jar.asc` / `.pom.asc` / sources / javadoc），抽验 `gpg --verify` 出 Good signature。
2. **五连障清单**（docs/lessons/bug-central-release-pipeline-20260921.md）逐条过：
   - ① 插件没静默跳过：日志出现 `central-publishing-maven-plugin:0.8.0:publish (injected-central-publishing)` 字样（每个 reactor 模块各一次，含 parent/BOM 壳）
   - ② token 失效：干跑不触网络，仅实际发布时验证
   - ③ 两步序少半步：BOM 已并入单 reactor，单 deployment 无先后依赖（结构性消解）
   - ④ 模块缺 name：`for f in $(find . -name pom.xml); do grep -L "<name>" $f; done` 零输出
   - ⑤ 旧 BOM 假绿：干跑本身是 clean 全量构建，BOM 为 reactor 内模块，天然免疫
3. **bundle 无 sample**：核对 bundle 暂存区（`target/central-deferred`）无 `jevokit-sample` 目录、其余 20 模块齐全——`excludeArtifacts` 常驻排除生效即无需 -pl（`-pl` 可选双保险，见"单 bundle 发布序"注意项）。

## 构建产物核对单（发布前干跑即验）

每次正式 deploy 前，先在本地跑一遍核对（产物数基准：单 reactor 21 模块 = 18 库 + sample + BOM 薄壳 + parent；BOM 壳与 parent 为 pom 型构件，仅 pom + 签名，无 jar 附加件）：

| 项 | 预期 | 核对命令 |
|---|---|---|
| sources jar | 18（security-all 无源码属正常） | `find . -path "*/target/*-sources.jar" \| wc -l` |
| javadoc jar | 18（security-all 无源码自动跳过，不再产出空壳） | `find . -path "*/target/*-javadoc.jar" \| wc -l` |
| jar 签名 | 55（18 库主 + 18 sources + 18 javadoc + sample 主） | `find . -path "*/target/*.jar.asc" \| wc -l` |
| pom 签名 | 21（20 模块 + parent，BOM 壳含在模块数内） | `find . -path "*/target/*.pom.asc" \| wc -l` |
| BOM 壳签名 | pom 与 .pom.asc 各 1（在 jevokit-dependencies/target/） | `ls jevokit-dependencies/target/*.pom*` |
| 抽验签名 | Good signature | `gpg --verify <x>.jar.asc <x>.jar` |

## 已知事实（不必当问题处理）

- `security-all` 是纯聚合模块（无 Java 源码）：sources/javadoc 均自动跳过（聚合器扁平化后无空壳产物）
- `rocketmq` 模块 javadoc jar 83 文件（注释不规整但 doclint=none 只拦语法错误，生成成功）
- javadoc `Null charset name` 陷阱：javadoc 插件不配 charset/encoding 参数（读 `project.build.sourceEncoding` 属性，已在 parent properties 统一声明 UTF-8）；插件里显式配 charset 在 fork 执行时反而触发 NPE
- gpg 2.4.7（Git for Windows）+ 本机 keyboxd 曾不兼容 cygwin 侧密钥——已通过密钥环合并解决，如再遇 `No secret key` 先 `gpg --list-secret-keys` 确认密钥在哪个 homedir
