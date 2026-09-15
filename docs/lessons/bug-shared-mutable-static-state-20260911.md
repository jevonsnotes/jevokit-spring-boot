# 共享可变静态状态的两个放大轴：跨实例静默偷换（JasyptUtil）与时间轴漏写（DesUtil 惰性缓存）

- 根因类别：性能·并发（静态可变字段 = 跨实例/跨线程共享状态的放大器；本条为该族的两轴案例合订）
- 状态：已修复·已沉淀（BUG-6 eng-2 修复 + 对抗复测通过；BUG-10 修复 + TE-1 并发实证通过）
- 相关模块：spring-boot-kit/others/spring-boot-kit-jasypt/src/main/java/springbootkit/jasypt/util/JasyptUtil.java（BUG-6，跨实例轴）、spring-boot-kit/others/spring-boot-kit-desensitization/src/main/java/springbootkit/desensitization/util/DesUtil.java:33,50（BUG-10，时间轴；现状已是修复后形态）、测试锚点 spring-boot-kit/others/spring-boot-kit-jasypt/src/test/java/springbootkit/jasypt/util/JasyptUtilTest.java

## 现象 / 复现

两案的显形路径都极具代表性——**共享状态被改/漏改，都不在调用点报错**：

1. **BUG-6（跨实例轴，本工程师一手记录）**：#5 补测中"错口令密文应解密失败"的用例首版**莫名不抛**——排查发现测试方法内构造了第二个 JasyptUtil（不同口令）后，先实例的 `encrypt(...)` 实际走的是**后构造者**的加密器（静态字段已被覆盖，密文与新口令同源自解）。一个普通单测意外成了静态偷换行为的活体探测器：用例"错"得越 inexplicable，越要怀疑状态被看不见的手换过。
2. **BUG-10（时间轴，转述修复与 TE-1 复测记录）**：DesUtil 的脱敏器惰性缓存并发下**永远 miss**——半截双检锁"检查/加锁/重建"三步齐全，唯独漏写回 `put`，重建结果每次丢弃。功能完全正确（每次现场重建），性能劣化静默发生，无任何报错面。

## 根因

**一个根、两条轴**：`static` 可变字段是隐式全局共享，把普通实例状态放大到两个正交的方向——

- **跨实例轴（空间维，BUG-6）**：构造器副作用写 static——同一 JVM 里任何位置 `new` 一次就重写全局引用，"谁最后构造谁说了算"。生产靠"恰好只有一个 Spring bean"的惯例侥幸成立；多上下文（测试）、多实例（误用）即触发静默偷换。与 BUG-1 的 IntHolder 同族（共享可变状态），但 IntHolder 是成员级的时间轴局部版，本条是类级全局版——族谱全貌见 eng-1 同批次沉淀的 L2 条目（共享可变状态专题，互链）。
- **时间轴（线程维，BUG-10）**：手写双检锁的"检查"与"发布"不原子——本例烂在最阴的形态：不是发布了半初始化对象，而是**压根没发布**，缓存退化为纯开销。即便补上 put，无 volatile 的 DCL 仍有半初始化发布风险；手写 DCL 的每一步都是自证清白的机会成本。

两轴的静默性同源：受害者（调用方）与加害点（static 赋值/漏赋值）之间没有任何显式因果链——BUG-6 靠单测的交叉污染显形，BUG-10 靠并发加压显形，**没有一个靠日志或异常直接暴露**。

## 同类隐患排查

- BUG-1（IntHolder 成员级共享可变状态）：eng-1 修复，L2 条目详述——本条为其"静态全局"姊妹篇。
- hutool `SpringUtil` 类初始化观察（接口 static 字段直接 `SpringUtil.getBean`，最小上下文必须先注册 SpringUtil）：已在 #6 终验记档——相关但不同族（类初始化顺序依赖，而非可变共享被改写），不并入本条。
- 全仓 `static` 可变字段 + 构造器赋值/懒加载手写锁模式：按"跨实例轴/时间轴"两特征扫（本批次沉淀时无新增第三例）。

## 修复要点

1. **BUG-6：单实例锁定 + 显式拒绝**（要"结构上不可能偷换"，不是"文档上别偷换"）：static 可变字段改为 `volatile JasyptUtil instance`；构造器 `synchronized(JasyptUtil.class)` 内检查已锁定则抛 `IllegalStateException`（消息指明"仅支持单实例；如需多加密器请直接持有 StringEncryptor"），否则初始化并锁定。静态 encrypt/decrypt 门面签名不变，单 bean 生产场景零 breaking（全仓生产构造点仅装配类一处，grep 实证）。
2. **BUG-10：整体替换为 `ConcurrentHashMap.computeIfAbsent`**（DesUtil:33/:50 现状）——标准库原子的"查-算-存"一步到位，替换而非修补半截 DCL。**惰性缓存没有手写双检锁的存在理由。**

## 回归用例

1. `JasyptUtilTest#secondConstruction_failsExplicitly`——除断言二次构造抛 `IllegalStateException` 外，**关键补刀是"拒绝之后全局引用不变"**：抛错后再用首实例口令的密文解密，必须依旧成功。只断言异常不够——异常抛了但引用已被半改同样是坏实现；"拒止生效"要以拒止后的行为不变为准。终验对抗复测另补"拒绝后再以新口令构造新密文可往返"（确认拒绝没有误伤合法的多加密器用法）与"有 DB/正常路径不被过度抑制"两个方向。
2. DesUtil 并发回归（TE-1 实证：**16 线程 × 300 轮**加压）——**并发修复的存在理由就是并发测试**：单线程断言（哪怕 isSameAs 缓存命中）测不出"漏 put"——单线程下含检查的半截实现行为完全正确，竞争窗口根本没打开。并发用例不是锦上添花，是这类修复唯一的合格证。

## 防范措施

1. **工具类门面优先无状态**；必须持共享引用时，用"单实例锁定 + 二次构造显式抛错"，禁止构造器静默写 static（写 static 的构造器 = 给所有后续实例埋雷）。
2. **惰性缓存一律 `computeIfAbsent`**，禁手写双检锁——包括"看起来没并发"的场景（今天没并发 ≠ 明天没并发，且半截 DCL 恰好在单线程下最像对的）。
3. **测试视角**：并发/竞态类修复的回归用例必须是并发用例，单线程断言只证功能面；反过来，单测出现"莫名不抛/莫名通过"时，先怀疑共享静态状态被偷换（BUG-6 的发现路径即此）。
4. 评审特征速查：见到 `static` 非 final 字段 + 构造器赋值 → 跨实例轴质询；见到 `if (!cache.containsKey(...))` + `synchronized` 手写组合 → 时间轴质询。
