# Spring Boot Kit

[![Release](https://img.shields.io/badge/release-v3.5.0.0-SNAPSHOT-blue.svg)](https://github.com/jevonsnotes/jevokit-spring-boot)
[![Java Version](https://img.shields.io/badge/jdk-17+-green.svg)]()

Spring Boot 套件，涵盖安全硬ening、数据脱敏、审计日志、分布式锁、幂等单号生成、加密配置等场景。基于 Spring Boot 3.5.x 自动配置。

## 版本说明

| 模块 | Spring Boot | Spring | JDK |
| --- | --- | --- | --- |
| 3.5.x | 3.5.x | - | 17+ |
| 2.7.x | 2.7.x | 5.3.x | 8 |
| 2.5.x | 2.5.x | - | 8 |
| 2.1.x | 2.1.x | - | 8 |
| 1.5.x | 1.5.x | 4.3.x | 7 |

> 组件依赖从 Maven Central 获取

## 快速开始

```xml
<dependency>
    <groupId>io.github.jevonsnotes</groupId>
    <artifactId>jevokit-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

或者按需引入各模块 starter：

```xml
<!-- 安全 XSS -->
<dependency>
    <groupId>io.github.jevonsnotes</groupId>
    <artifactId>jevokit-security-xss-spring-boot-starter</artifactId>
</dependency>

<!-- 审计日志 -->
<dependency>
    <groupId>io.github.jevonsnotes</groupId>
    <artifactId>jevokit-audit-log-spring-boot-starter</artifactId>
</dependency>

<!-- 数据脱敏 -->
<dependency>
    <groupId>io.github.jevonsnotes</groupId>
    <artifactId>jevokit-desensitization-spring-boot-starter</artifactId>
</dependency>

<!-- Redis（分布式锁、缓存、Token） -->
<dependency>
    <groupId>io.github.jevonsnotes</groupId>
    <artifactId>jevokit-redis-spring-boot-starter</artifactId>
</dependency>

<!-- 分布式单号生成 -->
<dependency>
    <groupId>io.github.jevonsnotes</groupId>
    <artifactId>jevokit-nogen-spring-boot-starter</artifactId>
</dependency>

<!-- Excel 导入 -->
<dependency>
    <groupId>io.github.jevonsnotes</groupId>
    <artifactId>jevokit-excel-spring-boot-starter</artifactId>
</dependency>

<!-- Jasypt 加解密 -->
<dependency>
    <groupId>io.github.jevonsnotes</groupId>
    <artifactId>jevokit-jasypt-spring-boot-starter</artifactId>
</dependency>

<!-- OkHttp 代理 -->
<dependency>
    <groupId>io.github.jevonsnotes</groupId>
    <artifactId>jevokit-okhttp-spring-boot-starter</artifactId>
</dependency>

<!-- API 安全访问 -->
<dependency>
    <groupId>io.github.jevonsnotes</groupId>
    <artifactId>jevokit-api-spring-boot-starter</artifactId>
</dependency>

<!-- RocketMQ 集成 -->
<dependency>
    <groupId>io.github.jevonsnotes</groupId>
    <artifactId>jevokit-rocketmq-spring-boot-starter</artifactId>
</dependency>

## 模块一览

### security（安全）

| Starter | 说明 |
| --- | --- |
| `jevokit-security-xss` | XSS 防护（清除/拒绝/转义模式） |
| `jevokit-security-path-traversal` | 路径遍历防护 |
| `jevokit-security-sql-inject` | SQL 注入防护 |
| `jevokit-security-code-inject` | 代码注入检测 |
| `jevokit-security-web-fixed` | Spring Web 固定防护 |
| `jevokit-security-all` | 安全全家桶（聚合上述全部） |
| `jevokit-api` | API 安全访问（加解密、加签验签、防重放） |
| `jevokit-jasypt` | 系统配置值加解密 |

### compliance（合规）

| Starter | 说明 |
| --- | --- |
| `jevokit-audit-log` | 审计日志（注解/环绕，支持 DB/文件存储） |
| `jevokit-desensitization` | 数据脱敏（注解/配置，支持 logback 日志脱敏） |

### data（数据）

| Starter | 说明 |
| --- | --- |
| `jevokit-redis` | Redis 缓存、分布式锁、Token 策略 |
| `jevokit-nogen` | 基于 Redis+DB 的分布式单号生成 |
| `jevokit-excel` | Excel 数据自动封装（@Excel 注解） |

### integration（集成）

| Starter | 说明 |
| --- | --- |
| `jevokit-okhttp` | OkHttp 正向代理 |
| `jevokit-rocketmq` | RocketMQ 集成 |
| `jevokit-workflow` | 工作流引擎 |

### core（核心）

| Module | 说明 |
| --- | --- |
| `jevokit-core` | 核心注解与工具（@AuditLog、@Excel、@BeanField 等） |
| `jevokit-autoconfigure` | Spring Boot 自动配置 |

## 使用示例

### XSS 防护

```yaml
spkit:
  xss:
    enabled: true
    mode: clear
    ignore-response: true
    urlPatterns:
      - /**
    urlExcludePatterns:
      - /example
```

### 审计日志

```yaml
audit:
  log:
    enabled: true
    enabled-around: true
    persistent:
      mode: db
    event-types:
      patterns:
        GET:
          QUERY:
            - /*/{id:[0-9,]+}
        POST:
          CREATE:
            - /*/
```

注解式审计：

```java
@AuditLog
@RequestMapping("/test")
public String test() { ... }
```

### 数据脱敏

```java
@Desensitization
@RequestMapping("/test")
public TestVo test(@RequestBody TestVo testVo) {
    return testVo;
}
```

### Redis 分布式锁

```java
@Lock(namespace = "testLock", fallback = DefaultLockFallback.class, key = @Key(route = "name"))
@RequestMapping("/testLock")
public String testLock(@RequestBody TestVo testVo) {
    return testVo.getName();
}
```

### 分布式单号

```java
String no = BzNoHelper.getNo();
// 或通过 API：JevokitNoGenApi.getNo("bizTag")
```

### API 安全访问

```java
@JSecureApi
@RequestMapping("/test")
public JSecureResponse<TestVo> test(@RequestBody JSecureRequest<TestVo> req) {
    return JSecureResponse.ok(req.getTarget());
}
```

## 文档

完整文档见 [GitHub Wiki](https://github.com/jevonsnotes/jevokit-spring-boot/wiki) 或右侧目录导航。

## License

Apache License 2.0
