package springbootkit.core.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import cn.hutool.crypto.asymmetric.SM2;
import cn.hutool.crypto.symmetric.SM4;
import lombok.extern.slf4j.Slf4j;
import springbootkit.core.lang.CallBackP3R;
import springbootkit.core.lang.CallbackPR;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @Description TODO
 * @Date 2022/5/25 8:33
 * @Version 0.0.1
 */
@Slf4j
public class SecurityUtil {
    private static SecureRandom random;

    /** 配置键前缀：未注入密钥的算法，其无参方法抛出 IllegalStateException 并在消息中给出具体配置键 */
    private static final String CFG_PREFIX = "spkit.crypto.";

    private static volatile String rsaPublicKey;
    private static volatile String rsaPrivateKey;
    private static volatile String aesKey;
    private static volatile String sm4Key;
    private final static ThreadLocal<Set<Object>> FILTER_CACHE = new ThreadLocal<>();

    static {
        try {
            random = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            log.error("SecurityUtil random instance init error", e);
        }
    }

    /**
     * 注入加解密密钥，替代已移除的内置默认密钥（原内置密钥随源码泄露，视为不可信，禁止再用于新数据）。
     * 通常由装配层读取 spkit.crypto.* 配置后调用，也可在应用启动阶段自行调用；重复调用以最后一次为准。
     * 各参数独立生效：仅注入所需算法的密钥即可，未注入算法对应的无参方法将抛出 IllegalStateException。
     *
     * @param rsaPublicKey  RSA 公钥（Base64），对应配置 spkit.crypto.rsa-public-key
     * @param rsaPrivateKey RSA 私钥（Base64），对应配置 spkit.crypto.rsa-private-key
     * @param aesKey        AES 密钥（16/24/32 字节），对应配置 spkit.crypto.aes-key
     * @param sm4Key        SM4 密钥（16 字节），对应配置 spkit.crypto.sm4-key
     */
    public static void init(String rsaPublicKey, String rsaPrivateKey, String aesKey, String sm4Key) {
        if (StrUtil.isNotBlank(rsaPublicKey)) {
            SecurityUtil.rsaPublicKey = rsaPublicKey.trim();
        }
        if (StrUtil.isNotBlank(rsaPrivateKey)) {
            SecurityUtil.rsaPrivateKey = rsaPrivateKey.trim();
        }
        if (StrUtil.isNotBlank(aesKey)) {
            SecurityUtil.aesKey = aesKey.trim();
        }
        if (StrUtil.isNotBlank(sm4Key)) {
            SecurityUtil.sm4Key = sm4Key.trim();
        }
    }

    private static String requireKey(String key, String configKey) {
        if (StrUtil.isBlank(key)) {
            throw new IllegalStateException("crypto keys not configured: " + configKey);
        }
        return key;
    }

    /**
     * 获取aes密文（使用注入密钥，未注入抛 IllegalStateException）
     *
     * @param val 明文
     * @return 密文
     */
    public static String aesEncrypt(String val) {
        return aesEncrypt(val, requireKey(aesKey, CFG_PREFIX + "aes-key"));
    }

    public static String aesDecrypt(String val) {
        return aesDecrypt(val, requireKey(aesKey, CFG_PREFIX + "aes-key"));
    }

    /**
     * 获取aes密文（显式密钥，免配置）
     *
     * @param val 明文
     * @param key 密钥（16/24/32 字节）
     * @return 密文
     */
    public static String aesEncrypt(String val, String key) {
        return SecureUtil.aes(key.getBytes()).encryptBase64(val);
    }

    /**
     * 获取aes明文（显式密钥，免配置）
     *
     * @param val 密文
     * @param key 密钥（16/24/32 字节）
     * @return 明文
     */
    public static String aesDecrypt(String val, String key) {
        return SecureUtil.aes(key.getBytes()).decryptStr(val);
    }

    public static String sm4cbcEncrypt(String val) {
        return sm4cbcEncrypt(val, requireKey(sm4Key, CFG_PREFIX + "sm4-key"));
    }
    /**
     * sm4cbc 加密 （偏向盐长度16）
     * @param val 明文
     * @param key 密钥
     * @return 密文
     */
    public static String sm4cbcEncrypt(String val,String key) {
        byte[] iv = random.generateSeed(16);
        SM4 alg = new SM4(Mode.CBC, Padding.PKCS5Padding, key.getBytes(), iv);
        byte[] encByte = alg.encrypt(val);
        return Base64.encode(ArrayUtil.addAll(iv, encByte));
    }

    /**
     * sm4cbc解密，使用注入密钥（spkit.crypto.sm4-key），未注入抛 IllegalStateException
     * @param val 密文
     * @return 明文
     */
    public static String sm4cbcDecrypt(String val) {
        return sm4cbcDecrypt(val, requireKey(sm4Key, CFG_PREFIX + "sm4-key"));
    }

    /**
     * sm4cbc 解密 （偏向盐长度16）
     * @param val 密文
     * @param key 密钥
     * @return 明文
     */
    public static String sm4cbcDecrypt(String val,String key) {
        if(StrUtil.isBlank(val)) return val;
        byte[] decode = Base64.decode(val);
        byte[] iv = ArrayUtil.sub(decode, 0, 16);
        SM4 alg = new SM4(Mode.CBC, Padding.PKCS5Padding, key.getBytes(), iv);
        byte[] contentRes = ArrayUtil.sub(decode, 16, decode.length);

        return alg.decryptStr(contentRes);
    }

    /**
     * 获取rsa密文（使用注入私钥加密，保持既有方向；未注入抛 IllegalStateException）
     *
     * @param val 明文
     * @return 密文
     */
    public static String rsaEncrypt(String val) {
        return new RSA(requireKey(rsaPrivateKey, CFG_PREFIX + "rsa-private-key"), null)
                .encryptBase64(val, KeyType.PrivateKey);
    }

    /**
     * 获取rsa明文（使用注入公钥解密，保持既有方向；未注入抛 IllegalStateException）
     *
     * @param val 密文
     * @return 明文
     */
    public static String rsaDecrypt(String val) {
        return new RSA(null, requireKey(rsaPublicKey, CFG_PREFIX + "rsa-public-key"))
                .decryptStr(val, KeyType.PublicKey);
    }

    /**
     * 获取rsa明文（使用注入公钥解密，保持既有方向；未注入抛 IllegalStateException）
     *
     * @param val 密文
     * @return 明文
     */
    public static byte[] rsaDecrypt(byte[] val) {
        return new RSA(null, requireKey(rsaPublicKey, CFG_PREFIX + "rsa-public-key"))
                .decrypt(Base64.isBase64(val) ? Base64.decode(val) : val, KeyType.PublicKey);
    }

    /**
     * 获取rsa密文（显式公钥加密，免配置，标准公钥加密方向）
     *
     * @param val    明文
     * @param pubKey 公钥（Base64）
     * @return 密文
     */
    public static String rsaEncrypt(String val, String pubKey) {
        return new RSA(null, pubKey).encryptBase64(val, KeyType.PublicKey);
    }

    /**
     * 获取rsa明文（显式私钥解密，免配置，标准私钥解密方向）
     *
     * @param val    密文
     * @param priKey 私钥（Base64）
     * @return 明文
     */
    public static String rsaDecrypt(String val, String priKey) {
        return new RSA(priKey, null).decryptStr(val, KeyType.PrivateKey);
    }

    /**
     * sm2验签
     * @param val 明文字符串
     * @param signature 签名base64
     * @param pubKey 公钥
     * @return 验签结果
     */
    public static boolean sm2Base64Verify(String val,String signature,String pubKey) {
        if(val == null) return false;
        SM2 sm2 = new SM2(null,pubKey);
        return sm2.verify(val.getBytes(),Base64.decode(signature));
    }

    /**
     * sm2签名
     * @param val 明文字符串
     * @param priKey 私钥
     * @return 签名base64
     */
    public static String sm2Base64Sign(String val,String priKey) {
        SM2 sm2 = new SM2(priKey,null);
        byte[] sign = sm2.sign(val.getBytes());

        return Base64.encode(sign);
    }

    /**
     * 清除对象的字符串类型属性
     *
     * @param o
     * @param callback
     * @deprecated replace with {@link #filterStr(Object, CallBackP3R)}
     */
    @Deprecated
    public static Object cleanObjForStr(Object o, CallbackPR<String, String> callback) {
        return filterStr(o, (o1, field, s) -> callback.call(s));
    }


    /**
     * 对象字符串过滤
     *
     * @param o
     * @param callback
     */
    public static Object filterStr(Object o, CallBackP3R<String, Object, Field, String> callback) {
        if (o != null) {
            Class<?> currentClass = o.getClass();
            //常见类直接处理
            if (String.class.isAssignableFrom(currentClass)) {
                return callback.call(o, null, (String) o);
            } else if (ClassUtil.isSimpleValueType(currentClass)) {
                return o;
            } else if (Collection.class.isAssignableFrom(currentClass)) {
                processCollection(o, null, callback);
            } else if (Map.class.isAssignableFrom(currentClass)) {
                processMap(o, null, callback);
            } else if (!hasFiltered(o)) {
                try {
                    // 缓存数据对象，避免存在嵌套关系场景造成死循环
                    addFilterCache(o);
                    while (!(currentClass == null || currentClass == Object.class)) {
                        Field[] declaredFields = currentClass.getDeclaredFields();
                        for (Field field : declaredFields) {

                            int modifiers = field.getModifiers();
                            //跳过final和static属性
                            if (!Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers)) {

                                if (ClassUtil.isSimpleValueType(field.getType())) {
                                    if (String.class.isAssignableFrom(field.getType())) {
                                        ReflectUtil.setFieldValue(o, field, callback.call(o, field, (String) ReflectUtil.getFieldValue(o, field)));
                                    }
                                } else if (field.getType().isArray()) {
                                    Object fieldValue = ReflectUtil.getFieldValue(o, field);
                                    // object类型的才处理
                                    if(fieldValue != null && Object.class.equals(fieldValue.getClass().getComponentType())) {
                                        Object[] value = (Object[]) fieldValue;
                                        if (ArrayUtil.isNotEmpty(value)) {
                                            for (int i = 0; i < value.length; i++) {
                                                if (value[i] != null) {
                                                    if (String.class.isAssignableFrom(value[i].getClass())) {
                                                        value[i] = callback.call(value[i], field, (String) value[i]);
                                                    } else {
                                                        value[i] = filterStr(value[i], callback);
                                                    }
                                                }
                                            }
                                        }
                                    }


                                } else if (Collection.class.isAssignableFrom(field.getType())) {
                                    processCollection(o, field, callback);
                                } else if (Map.class.isAssignableFrom(field.getType())) {
                                    processMap(o, field, callback);
                                } else {

                                    // 开始处理数据对象
                                    ReflectUtil.setFieldValue(o, field, filterStr(ReflectUtil.getFieldValue(o, field), callback));
                                }
                            }
                        }
                        currentClass = currentClass.getSuperclass();
                    }
                } finally {
                    removeFilterCache(o);
                }
            }

        }
        return o;
    }

    private static void removeFilterCache(Object o) {
        Set<Object> objects = getFilterCache();
        if (CollectionUtil.isNotEmpty(objects)) {
            objects.remove(o);
            if (objects.isEmpty()) {
                FILTER_CACHE.remove();
            }
        }
    }

    private static void addFilterCache(Object o) {
        getFilterCache().add(o);
    }

    private static Set<Object> getFilterCache() {
        Set<Object> objects = FILTER_CACHE.get();
        if (objects == null) {
            FILTER_CACHE.set(objects = new HashSet<>());
        }
        return objects;
    }

    private static boolean hasFiltered(Object o) {
        Set<Object> filterCache = getFilterCache();
        return filterCache.contains(o);
    }


    @Deprecated
    private static <T> void processMap(T src, Field field, CallbackPR<String, String> callback) {
        processMap(src, field, (T, field1, s) -> callback.call(s));
    }


    /**
     * 处理map类型
     *
     * @param src
     * @param field
     * @param callback
     * @param <T>
     */
    private static <T> void processMap(T src, Field field, CallBackP3R<String, Object, Field, String> callback) {
        try {
            Map<Object, Object> values = (Map) (field == null ? src : ReflectUtil.getFieldValue(src, field));
            if (values != null) {
                // 不可变容器统一降级：unmodifiable*、Map.of 系等无法实例化同类临时容器或不可回写，
                // 告警并跳过该字段，不让 UtilException 逃逸中断整棵对象的过滤（与集合侧语义对齐）
                Map<Object, Object> temp;
                try {
                    temp = ReflectUtil.newInstance(values.getClass());
                } catch (RuntimeException e) {
                    log.warn("Skip map cleaning: cannot create temp container of [{}]: {}",
                            values.getClass().getName(), e.getMessage());
                    return;
                }
                for (Map.Entry<Object, Object> next : values.entrySet()) {
                    Object key = next.getKey();
                    Object value = next.getValue();
                    if (key != null) {
                        if (String.class.isAssignableFrom(key.getClass())) {
                            key = callback.call(key, field, (String) key);
                        } else {
                            key = filterStr(key, callback);
                        }
                    }
                    if (value != null) {
                        if (String.class.isAssignableFrom(value.getClass())) {
                            value = callback.call(value, field, (String) value);
                        } else {
                            value = filterStr(value, callback);
                        }
                    }
                    temp.put(key, value);
                }
                try {
                    values.clear();
                    values.putAll(temp);
                } catch (RuntimeException e) {
                    log.warn("Skip map cleaning: map [{}] is not modifiable", values.getClass().getName());
                }
            }
        } catch (UnsupportedOperationException e) {
            log.warn("Can not do clean for map cause of unsupportedOperation : {} ", e.getMessage());
        }
    }

    @Deprecated
    private static <T> void processCollection(T src, Field field, CallbackPR<String, String> callback) {
        processCollection(src, field, (t, field1, s) -> callback.call(s));
    }

    /**
     * 处理集合类型
     *
     * @param src
     * @param field
     * @param callback
     * @param <T>
     */
    private static <T> void processCollection(T src, Field field, CallBackP3R<String, Object, Field, String> callback) {
        try {
            Collection values = (Collection) (field == null ? src : ReflectUtil.getFieldValue(src, field));
            if (values != null) {
                // 不可变集合统一降级（原 "Unmodifiable" 字样检查防不住 List.of 的 ListN/List12）：
                // 无法实例化同类临时容器或不可回写时告警并跳过，不让 UtilException 逃逸中断整棵对象的过滤
                Collection<Object> temp;
                try {
                    temp = ReflectUtil.newInstance(values.getClass());
                } catch (RuntimeException e) {
                    log.warn("Skip collection cleaning: cannot create temp container of [{}]: {}",
                            values.getClass().getName(), e.getMessage());
                    return;
                }
                for (Object c : values) {
                    if (String.class.isAssignableFrom(c.getClass())) {
                        temp.add(callback.call(c, field, (String) c));
                    } else {
                        temp.add(filterStr(c, callback));
                    }
                }
                try {
                    values.clear();
                    values.addAll(temp);
                } catch (RuntimeException e) {
                    log.warn("Skip collection cleaning: collection [{}] is not modifiable", values.getClass().getName());
                }
            }
        } catch (UnsupportedOperationException e) {
            log.warn("Can not do clean for collection cause of unsupportedOperation : {} ", e.getMessage());
        }
    }

}
