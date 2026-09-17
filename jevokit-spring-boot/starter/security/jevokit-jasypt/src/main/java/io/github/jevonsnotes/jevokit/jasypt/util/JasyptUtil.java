package io.github.jevonsnotes.jevokit.jasypt.util;

import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;

/**
 * jasypt 加解密工具门面（静态 encrypt/decrypt）。
 *
 * <p>实现约束：静态门面必须持有一个加密器实例——历史上以 static 可变字段 + 构造器赋值实现，
 * 多实例场景后构造者静默覆盖先构造者的全局引用（跨实例共享可变状态，BUG-6）。现改为
 * 「构造即锁定」：首个实例初始化全局引用后，后续构造显式抛 {@link IllegalStateException}，
 * 偷换在结构上不可能静默发生。单 Spring bean 场景（kit 装配类只注册一个 JasyptUtil）
 * 用法完全不变。
 *
 */
@Slf4j
public class JasyptUtil {
    private static volatile JasyptUtil instance;
    private final StringEncryptor encryptor;
    private final EncryptablePropertyDetector detector;

    public JasyptUtil(StringEncryptor encryptor, EncryptablePropertyDetector detector){
        synchronized (JasyptUtil.class) {
            if (instance != null) {
                throw new IllegalStateException(String.format(
                        "JasyptUtil 已由实例 [%s] 初始化，禁止重复构造覆盖全局引用"
                                + "（静态门面仅支持单实例；如需多加密器请直接持有 StringEncryptor）",
                        instance.getClass().getName()));
            }
            this.encryptor = encryptor;
            this.detector = detector;
            instance = this;
        }
        log.info("JasyptUtil initialized and locked as the singleton facade instance");
    }

    /**
     * 加密明文（静态门面，实例由容器构造锁定）。
     *
     * @param val 明文
     * @return jasypt 密文（base64 形态，与 ENC() 包装格式配套）
     */
    public static String encrypt(String val){
        return instance.encryptor.encrypt(val);
    }

    /**
     * 解密密文。
     *
     * @param val 密文；若已带 ENC() 包装则先去包装再解密（与属性解密口径一致）
     * @return 明文
     */
    public static String decrypt(String val){
        if(instance.detector.isEncrypted(val)){
            val =  instance.detector.unwrapEncryptedValue(val);
        }
        return instance.encryptor.decrypt(val);
    }

}
