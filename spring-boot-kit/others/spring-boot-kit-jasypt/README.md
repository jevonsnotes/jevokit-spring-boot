通过指定加密器实现密文解密，不配置盐值则使用代码中的固定盐值
```yaml
jasypt:
  encryptor:
    property:
      resolver-bean: springBootKitEncryptPropertyResolver
```

也可以指定盐值，支持属性名识别
```yaml
jasypt:
  encryptor:
    property:
      jep: xxxxxxx # 同jasypt.encryptor.password
      resolver-bean: springBootKitEncryptPropertyResolver
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