Table of Contents
=================

* [jevokit-spring-boot](#jevokit-spring-boot)
   * [security（安全加固）](#security安全加固)
      * [快速使用](#快速使用)
      * [xss](#xss)
      * [path-traversal](#path-traversal)
      * [sql-inject](#sql-inject)
      * [code-inject](#code-inject)
         * [CodeInjectUtil](#codeinjectutil)
      * [jasypt](#jasypt)
      * [api](#api)
   * [compliance（合规）](#compliance合规)
      * [audit-log（审计日志）](#audit-log审计日志)
      * [desensitization](#desensitization)
   * [data（数据）](#data数据)
      * [redis](#redis)
      * [nogen](#nogen)
      * [excel](#excel)
   * [integration（集成）](#integration集成)
      * [okhttp](#okhttp)
      * [rocketmq](#rocketmq)
      * [workflow](#workflow)

<!-- Created by https://github.com/ekalinin/github-markdown-toc -->

# jevokit-spring-boot

![Release](https://img.shields.io/badge/release-v3.5.0.0-SNAPSHOT-blue.svg)
![JDK](https://img.shields.io/badge/jdk-17+-green.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-6DB33F.svg)



版本支持说明：

jevokit-spring-boot | Spring Boot | Spring | JDK
|---|---|---|---|
| 3.5.x | 3.5.x | 3.5.x | 17+ |
| 2.7.x | 2.7.x | 5.3.x | 8 |

> 组件依赖从 Maven Central 获取

## security

> security包主要针对安全管控方面
 
> 涉及到web表单提交的，会使用StringPropertiesEditor代替默认的编辑器，可以通过注册链式处理器来实现自定义逻辑



```

public class XxStringPropertiesEditChain implements StringPropertiesSupportChain {
    @Override
    public String getTextEdit(String content) {
        return content;
    }

    @Override
    public String setTextEdit(String content) {
        return content;
    }
}
```
```
    @Bean
    public XxStringPropertiesEditChain xxStringPropertiesEditChain(){
        return new xxStringPropertiesEditChain();
    }
```
### 快速使用
```
<dependency>
  <groupId>io.github.jevonsnotes</groupId>
  <artifactId>jevokit-security-all-spring-boot-starter</artifactId>
  <version>${project.version}</version>
</dependency>
```
### xss
- mode 模式有清除、拒绝访问、转义，在不了解被拦截接口情况下建议使用拒绝访问或者转义模式，以便问题排查



```yaml
jevokit:
  xss:
    enabled: true
    mode: clear
    ignore-response: true
    urlPatterns: # 拦截路径
      - /**
    urlExcludePatterns: # 排除拦截路径
      - /example
    addition-tainted-patterns: #附加风险表达式
      - ((?i)javascript:)
```
### path-traversal
```yaml
jevokit:
  security:
    path-traversal:
      enabled: true
      mode: clear
      url-patterns:
        - /**
      url-exclude-patterns:
        - /test/xss/form/exclude
      addition-tainted-patterns: #附加风险表达式
        - \.\./
        - /etc/.*
```
### sql-inject
```yaml
jevokit:
  security:
    sql-inject:
      enabled: true
      mode: clear
      url-patterns:
        - /**
      url-exclude-patterns:
        - /test/xss/form/exclude
      addition-tainted-patterns: #附加风险表达式
        - ((?i)[ ]+(AND|OR)[ ]+)
```

### code-inject
> 代码注入

```yaml
jevokit:
  security:
    code-inject: #代码注入
      enabled: true
      negative: false # 是否反转匹配结果。默认false，当规则匹配时，认定为安全，CodeInjectUtil.isSafe返回true
      namespaces: #命名空间定义
        default: #默认命名空间
          - .* #安全规则匹配表达式，配合CodeInjectUtil.isSafe(...)使用，匹配则返回true
```

#### CodeInjectUtil
结合配置利用`CodeInjectUtil.isSafe(...)`判断动态内容是否安全

> 另有 `SecureRandomUtil`（jevokit-core 通用工具）：基于 hutool 和 SecureRandom 实现的随机类工具，用于解决 Random 类安全强度低的问题。

### jasypt
系统配置值加解密
#### 引入
```xml
        <dependency>
            <groupId>io.github.jevonsnotes</groupId>
            <artifactId>jevokit-jasypt-spring-boot-starter</artifactId>
            <version>${project.version}</version>
        </dependency>
```
#### 配置
通过指定加密器实现密文解密，不配置盐值则使用代码中的固定盐值
```yaml
jasypt:
  encryptor:
    property:
      resolver-bean: JevokitEncryptPropertyResolver
```

也可以指定盐值，支持属性名识别
```yaml
jasypt:
  encryptor:
    property:
      jep: xxxxxxx # 同jasypt.encryptor.password
      resolver-bean: JevokitEncryptPropertyResolver
```

配置识别优先级
```
jasypt.encryptor.password > jasypt.encryptor.jep > 代码固定值
```

快捷加解密工具
```java
JasyptUtil.encrypt() //加密
JasyptUtil.decrypt() //解密
```

### api

解决API安全访问场景。
- 支持请求头定制token实现简单访问控制；
- 支持MD5/SHA256+SM2+SM4自动混合加解密、加签验签、防重放。
- 规范请求体和返回体，统一处理异常
- 规范接口文档，统一对接模板

[接口文档规范模板](https://doc.weixin.qq.com/doc/w3_ABAAAAbBAD8CNXjwPXxFaRMSEsnAt?scode=ALwAHAe_AAs4OxCsYQABAAAAbBAD8)（内部文档，需替换为实际链接） 

```yaml
jevokit:
  api:
    client:
      - id: gd_default_default_v1 # 定义终端识别码
        secret-key: demo-sm4-secret # 密钥
        private-key: <demo-sm2-private-key>
        public-key: <demo-sm2-public-key>
        secret-key-sm4: demo-sm4-secret # sm4密钥，默认读取secret-key的值
        decrypt: true #启用请求解密，默认true，启用时需同步配置public-key
        encrypt: true # 启用返回加密，默认true，启用时需同步配置private-key
        verify: true # 启用验签默认true，默认true，启用时需同步配置secret-key、secret-key-sm4
        prevent-replay: true # 开启接口防重放，默认true
        allow-path: # 默认允许所有，存在个性配置时则进行访问路径鉴权
          - /xxapi/**
        header:
          - prefix: x-api # 默认 x-api
            signature-name: signature # 默认 signature
            timestamp-name: timestamp # 默认 timestamp
            nonce-name: nonce # 默认 nonce
            client-id-name: client-id # 默认 client-id
            sign-alg: MD5 # 默认MD5.可选 MD5/SHA256
```

使用@JSecureApi注解在接口方法上标注，则启用安全接口

```java
@Slf4j
@RestController
@RequestMapping("/secure")
public class SecureApi {

    @JSecureApi
    @RequestMapping("/test")
    @ResponseBody
    public JSecureResponse<TestVo> test(@RequestBody @Valid JSecureRequest<TestVo> testVo){
        TestVo target = testVo.getTarget();
        TestVo res = new TestVo();
        res.setName(target.getName());
        return JSecureResponse.ok(res);
    }
}

```

## compliance（合规）

### audit-log（审计日志）
#### 引入依赖
引入依赖

```
 <dependency>
                <groupId>io.github.jevonsnotes</groupId>
                <artifactId>jevokit-audit-log-spring-boot-starter</artifactId>
                <version>${project.version}</version>
            </dependency>
```

配置

```
  audit:
    log: # 审计日志
      enabled: true # 开启审计日志 默认false
      enabled-around: true #启用环绕审计，将会在请求处理前后调用save方法各一次
      ignore-non-user: false #忽略非用户操作日志
      content-limit:
        strategy: trunc # 报文长度限制策略，trunc为截取
        query-string: -1 # 长度限制。-1不限制
        request-body: -1 # 长度限制。-1不限制
        response-body: -1 # 长度限制。-1不限制
      persistent:
        mode: db # 存储方式，db数据库（自动建表）,log 日志文件
        db-migrate-period: day # 数据库转储间隔，year按年，month按月，day按天
      event-types:
        patterns:
          GET: # 请求方法
            QUERY: # 事件类型命名
              - /*/{id:[0-9,]+} # 事件匹配规则
          POST:
            QUERY:
              - /*/search
            CREATE:
              - /*/
            OTHERS:
              - /**
          PUT:
            UPDATE:
              - /**
          DELETE:
            DELETE:
              - /**/{id:[0-9,]+}
        name-mapper: # 事件类型与中文名映射
          QUERY: 查询
          CREATE: 创建
          DELETE: 删除
          OTHERS: 其他
```

默认输出样例：

```
[OTHRES] [00000000] [00000000] [192.0.2.20, 192.0.2.21] [127.0.0.1] [19020] [http] [POST] [application/json;charset=UTF-8] [/demo/order/detail] [200] [2022-06-29 19:33:36] [2022-06-29 19:33:36] [111] 
```

数据已封装在AuditLogDto对象，可通过实现`AuditLogProcessor`接口自定义存储审计日志逻辑。

#### 注解式审计
支持在`Controller`层的方法上使用@AuditLog注解标注审计(前提：`jevokit.audit.log.enabled=true`)

### desensitization
脱敏组件主要应用于客户敏感信息场景，原理是通过拦截方法返回对象，针对数据对象标有`@Desensitization`注解的`字符串类型`属性进行脱敏处理.

```
           <dependency>
                <groupId>io.github.jevonsnotes</groupId>
                <artifactId>jevokit-desensitization-spring-boot-starter</artifactId>
                <version>${project.version}</version>
            </dependency>
```
#### 配置参考

```
jevokit:
  desensitization:
    power-check-period: decrypt # 权限校验时机，decrypt 解密时；encrypt 加密时
    algorithm:
      view: mixed # 加密算法 basic 基础加密，使用*号代替，不支持反解密;aes 对称加密，支持反解密；mixed 混合加密，支持反解密；
      log: mixed
    level-power-key: # 权限功能代码与敏感等级绑定，并通过SensitizationPowerInterceptor传递权限代码
      lv0:
        - XXX_SPEC_CTRL_SENSITIZATION_LV0
      lv1:
        - XXX_SPEC_CTRL_SENSITIZATION_LV1
      lv2:
        - XXX_SPEC_CTRL_SENSITIZATION_LV2
      lv3:
        - XXX_SPEC_CTRL_SENSITIZATION_LV3
      lv4:
        - XXX_SPEC_CTRL_SENSITIZATION_LV4
      lv5:
        - XXX_SPEC_CTRL_SENSITIZATION_LV5
      lv6:
        - XXX_SPEC_CTRL_SENSITIZATION_LV6
      lv7:
        - XXX_SPEC_CTRL_SENSITIZATION_LV7
      lv8:
        - XXX_SPEC_CTRL_SENSITIZATION_LV8
      lv9:
        - XXX_SPEC_CTRL_SENSITIZATION_LV9
    prop-pattern: # 属性匹配表达式
      lv0: # 划分敏感级别
        name: # 名称
          - ".*DemoCostVo\\.fullName"
          - ".*DemoDetailVo\\.bankaccountnameagain"
        bank-card: # 银行卡
          - ".*DemoCostVo\\.bankCard"
          - ".*DemoBillVo\\.bankCard"
          - ".*DemoDetailVo\\.bankaccount"      
        bank-line-num: # 银行联行号
          - ".*DemoDetailVo\\.linenumber"
      lv9: # 用作最高级别敏感数据，用来兜底（注意配置顺序，最高级放最下面）
        name: # 名称
          - ".*DemoBillVo\\.fullName"
          - ".*DemoBillVo\\.personName"
          - ".*DemoDetailVo\\.hhouseholdName"
          - ".*DemoDetailVo\\.name"
        password: # 密码
          - "(?i).*password"
        id-card: # 身份证
          - ".*DemoBillVo\\.idCard"
          - ".*DemoDetailVo\\.id"
          - "(?i).*idCard"
        mobile: # 手机号
          - ".*DemoBillVo\\.mobile"
          - ".*DemoDetailVo\\.payeeTelePhone"
          - "(?i).*mobile"
        phone: # 电话
          - ".*DemoDetailVo\\.telePhone"
          - "(?i).*phone"
        email: # 电子邮件
          - "(?i).*mail"
        address: # 地址
          - ".*DemoDetailVo\\.homeAddress"
          - "(?i).*address"
        bank-card: # 银行卡
          - "(?i).*bankCard"
        bank-line-num: # 银行联行号
          - "(?i).*bankLineNum"
        license-plate: # 车牌号
          - "(?i).*licensePlate"

```
常用正则表达式参考
```
# 忽略大小写
(?i)XXX

# 不以XXX开头
(?!XXX).*

# 不以XXX结尾
.*(?<!XXX)
```

#### 数据传输对象脱敏
上面配置的是默认的脱敏逻辑，也可以在属性上注解式声明来实现自定义脱敏逻辑

```
public class TestVo {
   @DstProperty(type = DesType.NAME)
   private String name;

   @DstProperty(type = DesType.ID_CARD)
   private String idCard;

   @DstProperty(type = DesType.MOBILE)
   private String mobile;

   @DstProperty(type = DesType.EMAIL)
   private String email;

   @DstProperty(type = DesType.BANK_CARD)
   private String bankCard;

   @DstProperty(type = DesType.ADDRESS)
   private String address;

   @DstProperty(type = DesType.PHONE)
   private String phone;

   @DstProperty(type = DesType.BANK_LINE_NUM)
   private String comBankCard;
}
```

在托管于spring容器的方法上标注@EnableDesensitization启用自动脱敏

```
@RestController
@RequestMapping("/desensitization")
public class DesensitizationApi {

    @Desensitization
    @RequestMapping("/test")
    public TestVo test(@RequestBody TestVo testVo){
        return testVo;
    }

}
```

也可用 `DesHelper`显式调用脱敏

```
@RestController
@RequestMapping("/desensitization")
@RequiredArgsConstructor
public class DesensitizationApi {

    private final DesHelper desHelper;
    @RequestMapping("/test3")
    public TestVo test3(@RequestBody TestVo testVo){
        return desHelper.desensitize(testVo);//深度克隆
    }
}    
```
#### 日志脱敏
基于 logback filter 实现脱敏，自动识别日志入参（对象、XML 结构字符串、JSON 结构字符串），无需替换 appender。
在 `logback-spring.xml` 中为任意 appender 添加 `DesensitizedLogFilter` 即可：
```xml
<appender name="consoleLog" class="ch.qos.logback.core.ConsoleAppender">
    <filter class="io.github.jevonsnotes.jevokit.desensitization.support.logback.filter.DesensitizedLogFilter"/>
</appender>
<appender name="DST_LOG" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <File>${LOG_HOME}/desensitized.log</File>
    <filter class="io.github.jevonsnotes.jevokit.desensitization.support.logback.filter.DesensitizedLogFilter"/>
</appender>
```
注意：需要脱敏的对象需以参数方式使用日志输出方法，例如：
```
log.info("request log {}", xmlContent)
log.error("something excetion： {}", xmlContent, e)
```

## data（数据）

### redis
jevokit-spring-boot 基于 Spring Boot 3.5.x + JDK 17，使用 Redisson 3.49+，无需额外适配。

#### lock
> 缓解服务器压力，针对请求链长、易出现幂等问题的场景。

```
@RestController
@RequestMapping("/redis")
public class RedisApi {

    /**
     * 接口并发锁，在未请求完成之前，阻断并返回个性信息
     * @param testVo
     * @return
     */
    @Lock(namespace = "testLock",fallback = DefaultLockFallback.class,key = @Key(route = "name"))
    @RequestMapping("/testLock")
    public String testLock(@RequestBody TestVo testVo){
        return testVo.getName();
    }
}
```
#### cache

> 支持缓存失效时间指定或随机生成（避免击穿、雪崩问题）

使用样例

```

@Service
public class RedisService {
    /**
     * 用户缓存测试（随机失效时间 5-10分钟）
     * @param key
     * @return
     */
    @Cacheable(value = "testCache",keyGenerator = UserKeyGenerator.NAME,unless = RKeyConst.Unless.REMOTE_IF_NULL)
    public String userCache(String key){
        return UUID.fastUUID().toString(true);
    }

    /**
     * 用户缓存测试（指定失效时间）
     * @param key
     * @return
     */
    @Cacheable(value = "testCache#expire=1800",keyGenerator = UserKeyGenerator.NAME)
    public String userCacheWithExpire(String key){
        return UUID.fastUUID().toString(true);
    }

    /**
     * 全局缓存测试
     * @param key
     * @return
     */
    @Cacheable(value = "testCache",keyGenerator = GlobalKeyGenerator.NAME)
    public String globalCache(String key){
        return UUID.fastUUID().toString(true);
    }

    /**
     * 清除全局缓存
     */
    @CacheEvict(cacheNames = GlobalKeyGenerator.EVICT,allEntries = true)
    public void globalCacheEvict() {
    }
    /**
     * 清除用户缓存
     */
    @CacheEvict(cacheNames = UserKeyGenerator.EVICT,allEntries = true)
    public void userCacheEvict() {
    }
}


```
#### token
> 组件提供了token获取策略规范，以解决token刷新、次数限制、并发等问题。
>
> 通过实现TokenStrategy策略自定义业务逻辑，参见样例`DefaultTokenStrategy`。

### nogen
基于redis+db的分布式单号生成组件，目前支持mysql、pg库，实测约40000tps。

压测情况（6c 8g）：

10000 | 100000|1000000|10000000
---|---|---|---
200ms|2.3s|23s|3.5m

#### 引入依赖
```xml
        <dependency>
            <groupId>io.github.jevonsnotes</groupId>
            <artifactId>jevokit-nogen-spring-boot-starter</artifactId>
            <version>${project.version}</version>
        </dependency>
```

可以利用`BzNoHelper.getNo()`生成单号，也可以通过`API（io.github.jevonsnotes.jevokit.nogen.api.JevokitNoGenApi）`的形式生成。

#### API
- /nogen/view/tags 获取配置信息
- /nogen/getNo?bizTag=test 获取单号
- /nogen/getNos?bizTag=test&size=3000 批量获取单号
- /nogen/recycle?bizTag=test&bizNo=192235 回收单号（回收模式下可用）

更多参考：io.github.jevonsnotes.jevokit.nogen.api.JevokitNoGenApi

#### 数据源配置
配置数据库数据源，在启动服务时会自动初始化配置表`biz_no`，无需手动创建。

```yaml
jevokit:
  nogen:
    enable-api: false # 启用api，默认false 
    access-token: 123456 #配置访问API密钥，请求时带上accessToken请求头
spring:
  redis: # 配置redis用来缓存序列
    host: 192.0.2.30
    port: 7004
    password: xxx
    database: 0
    timeout: 120000
    cluster:
      nodes:
        - 192.0.2.30:7001
        - 192.0.2.30:7003
        - 192.0.2.30:7005

  datasource: # 配置DB用来持久化配置
    url: jdbc:mysql://192.0.2.31:3306/test
    username: xxx
    password: xxx
    hikari:
      maximum-pool-size: 50
      minimum-idle: 5
```

### excel
在controller层方法参数上使用@Excel注解可实现excel数据自动封装。

- 支持json数据、excel文件上传自动解析。
- 支持@BeanField声明字段顺序

```
            <dependency>
                <groupId>io.github.jevonsnotes</groupId>
                <artifactId>jevokit-excel-spring-boot-starter</artifactId>
                <version>${project.version}</version>
            </dependency>
```

在controller层入参声明

```
@RestController
@RequestMapping("excel")
public class ExcelApi {


    @RequestMapping("importExcel")
    public ApiResponse importExcel(
            @Excel(ignoreRows = 1) List<ExcelVo> file1,
            @Excel(ignoreRows = 1,sheet = 1) List<ExcelVo> file2){
        return ApiResponse.ok("success");
    }

}
```

## integration（集成）

### okhttp
用于解决需要正向代理的场景,本组件默认禁用重试机制,以避免超时请求导致的重复请求问题.
参考配置文件

```yaml
jevokit:
  ok:
    http:
      proxy-username: xxx # 代理用户名
      proxy-password: xxx # 代理用密码
      proxy-mode: always # 代理模式 ,always 永远使用代理,manual 手动调用
      proxy-ignores: #忽略指定地址,避免走代理
        - www.taobao.com
        - 192.0.2.*
        - 10.*
      error-mode: throwout # 当发生IO错误时的处理方式
      proxy-hosts: # 代理服务器端口和地址,当其中一个代理地址不可用时,自动切换下一个地址
        - 192.0.2.32:16666
        - 192.0.2.33:16666
```

### rocketmq

> RocketMQ 收发封装：`RocketMqHelper`、统一消息结构与配置装配。详细配置待补充，参见示例应用 `jevokit-sample`。

### workflow

> Camunda 7 工作流集成。详细配置待补充，参见示例应用 `jevokit-sample`。

