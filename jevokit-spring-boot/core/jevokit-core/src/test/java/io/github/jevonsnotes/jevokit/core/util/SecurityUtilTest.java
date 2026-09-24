package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.asymmetric.RSA;
import cn.hutool.crypto.asymmetric.SM2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SecurityUtil} 单元测试：AES/SM4/RSA/SM2 加解密与签验签、对象字符串过滤。
 *
 * <p>密钥管理：SecurityUtil 为静态有状态（{@code init} 注入后无法通过公开 API 清除），
 * 本类通过反射在每条用例前后重置四个密钥字段，保证用例间独立、乱序可跑；
 * 各用例自行持钥注入，不依赖他人残留状态。
 */
@DisplayName("SecurityUtil 加解密与签验签")
class SecurityUtilTest {

    /** 16 字节 AES 密钥（测试自持，勿复用历史泄露密钥） */
    private static final String AES_KEY = "k8tG-0123456789a";
    /** 16 字节 SM4 密钥 */
    private static final String SM4_KEY = "sm4-0123456789ab";
    /** 中文 + 空白混合明文，验证字节级还原 */
    private static final String PLAIN = " hello 世界!@# ";

    private static final String RSA_PUBLIC_KEY;
    private static final String RSA_PRIVATE_KEY;
    private static final String SM2_PUBLIC_KEY;
    private static final String SM2_PRIVATE_KEY;

    static {
        RSA rsa = new RSA();
        RSA_PUBLIC_KEY = rsa.getPublicKeyBase64();
        RSA_PRIVATE_KEY = rsa.getPrivateKeyBase64();
        SM2 sm2 = new SM2();
        SM2_PUBLIC_KEY = sm2.getPublicKeyBase64();
        SM2_PRIVATE_KEY = sm2.getPrivateKeyBase64();
    }

    @BeforeEach
    void resetKeys() {
        setStaticField("rsaPublicKey", null);
        setStaticField("rsaPrivateKey", null);
        setStaticField("aesKey", null);
        setStaticField("sm4Key", null);
    }

    @AfterEach
    void cleanUp() {
        resetKeys();
    }

    // ------------------------------------------------------------------
    // AES
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("AES")
    class Aes {

        @Test
        @DisplayName("显式密钥加解密往返还原")
        void aesRoundTripWithExplicitKey() {
            // when
            String cipher = SecurityUtil.aesEncrypt(PLAIN, AES_KEY);
            String recovered = SecurityUtil.aesDecrypt(cipher, AES_KEY);
            // then
            assertThat(cipher).as("密文不应等于明文").isNotEqualTo(PLAIN);
            assertThat(recovered).as("AES 往返应还原明文").isEqualTo(PLAIN);
        }

        @Test
        @DisplayName("注入密钥后无参方法可用，且 init 对密钥做 trim")
        void aesNoArgUsesInjectedKey() {
            // given：注入带首尾空白的密钥，init 内部会 trim
            SecurityUtil.init(null, null, "  " + AES_KEY + "  ", null);
            // when
            String cipher = SecurityUtil.aesEncrypt(PLAIN);
            String recovered = SecurityUtil.aesDecrypt(cipher);
            // then
            assertThat(recovered).as("init 注入密钥后无参加解密应可用").isEqualTo(PLAIN);
        }

        @Test
        @DisplayName("未注入密钥时无参方法抛 IllegalStateException 并指明配置键")
        void aesNoArgWithoutInjectionThrows() {
            // given：密钥字段已被 @BeforeEach 重置为未注入状态
            // then
            assertThatThrownBy(() -> SecurityUtil.aesEncrypt(PLAIN))
                    .as("未注入 aes-key 时加密应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jevokit.crypto.aes-key");
            assertThatThrownBy(() -> SecurityUtil.aesDecrypt("any"))
                    .as("未注入 aes-key 时解密应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jevokit.crypto.aes-key");
        }

        @Test
        @DisplayName("init 传空白不会注入密钥，各算法独立生效")
        void initBlankValueIgnored() {
            // given：仅注入 sm4，其余传空白
            SecurityUtil.init(" ", " ", " ", SM4_KEY);
            // then：aes 仍未注入
            assertThatThrownBy(() -> SecurityUtil.aesEncrypt(PLAIN))
                    .as("空白参数不应注入密钥")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jevokit.crypto.aes-key");
            // and：sm4 已注入可用
            String cipher = SecurityUtil.sm4cbcEncrypt(PLAIN);
            assertThat(SecurityUtil.sm4cbcDecrypt(cipher)).as("sm4 应已注入可用").isEqualTo(PLAIN);
        }

        @Test
        @DisplayName("篡改密文后解密应失败或还原不出原文")
        void aesTamperedCipherDetected() {
            // given
            String cipher = SecurityUtil.aesEncrypt(PLAIN, AES_KEY);
            String tampered = tamperFirstChar(cipher);
            // when / then
            assertNotRecoverable(() -> SecurityUtil.aesDecrypt(tampered, AES_KEY), "篡改后的 AES 密文");
        }

        @Test
        @DisplayName("错误密钥解密应失败或还原不出原文")
        void aesWrongKeyRejected() {
            // given
            String cipher = SecurityUtil.aesEncrypt(PLAIN, AES_KEY);
            String wrongKey = AES_KEY.substring(1) + "x";
            // when / then
            assertNotRecoverable(() -> SecurityUtil.aesDecrypt(cipher, wrongKey), "错误密钥下的 AES 密文");
        }
    }

    // ------------------------------------------------------------------
    // SM4 (CBC)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("SM4-CBC")
    class Sm4 {

        @Test
        @DisplayName("显式密钥加解密往返还原，且随机 IV 使两次密文不同")
        void sm4RoundTripWithExplicitKey() {
            // when
            String cipher1 = SecurityUtil.sm4cbcEncrypt(PLAIN, SM4_KEY);
            String cipher2 = SecurityUtil.sm4cbcEncrypt(PLAIN, SM4_KEY);
            String recovered = SecurityUtil.sm4cbcDecrypt(cipher1, SM4_KEY);
            // then
            assertThat(recovered).as("SM4-CBC 往返应还原明文").isEqualTo(PLAIN);
            assertThat(cipher1).as("随机 IV 下两次密文应不同").isNotEqualTo(cipher2);
        }

        @Test
        @DisplayName("注入密钥后无参方法往返还原；空白密文直通返回")
        void sm4NoArgAndBlankInput() {
            // given
            SecurityUtil.init(null, null, null, SM4_KEY);
            // when
            String cipher = SecurityUtil.sm4cbcEncrypt(PLAIN);
            // then
            assertThat(SecurityUtil.sm4cbcDecrypt(cipher)).as("注入密钥后无参往返应还原").isEqualTo(PLAIN);
            assertThat(SecurityUtil.sm4cbcDecrypt("")).as("空白密文应直通返回空白").isEmpty();
            assertThat(SecurityUtil.sm4cbcDecrypt(null)).as("null 密文应直通返回 null").isNull();
        }

        @Test
        @DisplayName("未注入密钥时无参方法抛 IllegalStateException 并指明配置键")
        void sm4NoArgWithoutInjectionThrows() {
            assertThatThrownBy(() -> SecurityUtil.sm4cbcEncrypt(PLAIN))
                    .as("未注入 sm4-key 时加密应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jevokit.crypto.sm4-key");
            assertThatThrownBy(() -> SecurityUtil.sm4cbcDecrypt("any"))
                    .as("未注入 sm4-key 时解密应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jevokit.crypto.sm4-key");
        }

        @Test
        @DisplayName("getSm4Key 统一入口：未注入抛 ISE 指明配置键；注入后返回密钥（与其他无参 API 语义一致）")
        void sm4KeyEntryRequiresInjection() {
            // given：密钥字段已被 @BeforeEach 重置为未注入状态
            assertThatThrownBy(() -> SecurityUtil.getSm4Key())
                    .as("未注入 sm4-key 时统一密钥入口应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jevokit.crypto.sm4-key");
            // when：注入后再取
            SecurityUtil.init(null, null, null, SM4_KEY);
            // then
            assertThat(SecurityUtil.getSm4Key()).as("注入后应返回注入的密钥").isEqualTo(SM4_KEY);
        }

        @Test
        @DisplayName("篡改密文后解密应失败或还原不出原文")
        void sm4TamperedCipherDetected() {
            // given
            String cipher = SecurityUtil.sm4cbcEncrypt(PLAIN, SM4_KEY);
            // 篡改密文段（跳过前 16 字节 IV）中间一个字节
            byte[] bytes = Base64.decode(cipher);
            bytes[bytes.length / 2] ^= 0x01;
            String tampered = Base64.encode(bytes);
            // when / then
            assertNotRecoverable(() -> SecurityUtil.sm4cbcDecrypt(tampered, SM4_KEY), "篡改后的 SM4 密文");
        }

        @Test
        @DisplayName("错误密钥解密应失败或还原不出原文")
        void sm4WrongKeyRejected() {
            // given
            String cipher = SecurityUtil.sm4cbcEncrypt(PLAIN, SM4_KEY);
            String wrongKey = SM4_KEY.substring(1) + "z";
            // when / then
            assertNotRecoverable(() -> SecurityUtil.sm4cbcDecrypt(cipher, wrongKey), "错误密钥下的 SM4 密文");
        }
    }

    // ------------------------------------------------------------------
    // RSA（注入方向：私钥加密 / 公钥解密；显式方向：公钥加密 / 私钥解密）
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("RSA")
    class Rsa {

        @Test
        @DisplayName("注入密钥后无参方法往返还原（私钥加密方向）")
        void rsaNoArgRoundTrip() {
            // given
            SecurityUtil.init(RSA_PUBLIC_KEY, RSA_PRIVATE_KEY, null, null);
            // when
            String cipher = SecurityUtil.rsaEncrypt(PLAIN);
            String recovered = SecurityUtil.rsaDecrypt(cipher);
            // then
            assertThat(recovered).as("RSA 无参往返应还原明文").isEqualTo(PLAIN);
        }

        @Test
        @DisplayName("rsaDecrypt(byte[]) 对 Base64 形态密文往返还原")
        void rsaByteDecryptRoundTrip() {
            // given
            SecurityUtil.init(RSA_PUBLIC_KEY, RSA_PRIVATE_KEY, null, null);
            String cipher = SecurityUtil.rsaEncrypt(PLAIN);
            // when：以 byte[] 传入 Base64 密文
            byte[] recovered = SecurityUtil.rsaDecrypt(cipher.getBytes(StandardCharsets.US_ASCII));
            // then
            assertThat(new String(recovered, StandardCharsets.UTF_8))
                    .as("byte[] 重载应还原明文").isEqualTo(PLAIN);
        }

        @Test
        @DisplayName("未注入密钥时无参方法抛 IllegalStateException 并指明配置键")
        void rsaNoArgWithoutInjectionThrows() {
            // given：仅注入公钥
            SecurityUtil.init(RSA_PUBLIC_KEY, null, null, null);
            // then：加密需要私钥
            assertThatThrownBy(() -> SecurityUtil.rsaEncrypt(PLAIN))
                    .as("未注入 rsa-private-key 时加密应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jevokit.crypto.rsa-private-key");

            // given：重置后仅注入私钥（公钥残留需一并清除）
            setStaticField("rsaPublicKey", null);
            setStaticField("rsaPrivateKey", null);
            SecurityUtil.init(null, RSA_PRIVATE_KEY, null, null);
            // then：解密需要公钥
            assertThatThrownBy(() -> SecurityUtil.rsaDecrypt("any"))
                    .as("未注入 rsa-public-key 时解密应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jevokit.crypto.rsa-public-key");
        }

        @Test
        @DisplayName("显式密钥（标准方向：公钥加密/私钥解密）往返还原")
        void rsaExplicitKeyRoundTrip() {
            // when
            String cipher = SecurityUtil.rsaEncrypt(PLAIN, RSA_PUBLIC_KEY);
            String recovered = SecurityUtil.rsaDecrypt(cipher, RSA_PRIVATE_KEY);
            // then
            assertThat(recovered).as("RSA 显式密钥往返应还原明文").isEqualTo(PLAIN);
        }

        @Test
        @DisplayName("篡改密文后解密应失败或还原不出原文")
        void rsaTamperedCipherDetected() {
            // given
            String cipher = SecurityUtil.rsaEncrypt(PLAIN, RSA_PUBLIC_KEY);
            String tampered = tamperFirstChar(cipher);
            // when / then
            assertNotRecoverable(() -> SecurityUtil.rsaDecrypt(tampered, RSA_PRIVATE_KEY), "篡改后的 RSA 密文");
        }

        @Test
        @DisplayName("非本密钥对加密的密文无法用本私钥解密")
        void rsaWrongKeyRejected() {
            // given：用另一对密钥加密
            RSA other = new RSA();
            String cipher = SecurityUtil.rsaEncrypt(PLAIN, other.getPublicKeyBase64());
            // when / then：本对私钥解密应失败或解不出原文
            assertNotRecoverable(() -> SecurityUtil.rsaDecrypt(cipher, RSA_PRIVATE_KEY), "他钥加密的 RSA 密文");
        }
    }

    // ------------------------------------------------------------------
    // SM2 签验签
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("SM2 签验签")
    class Sm2 {

        @Test
        @DisplayName("签名后验签通过；篡改签名验签失败；篡改原文验签失败")
        void sm2SignAndVerify() {
            // when
            String signature = SecurityUtil.sm2Base64Sign(PLAIN, SM2_PRIVATE_KEY);
            // then
            assertThat(SecurityUtil.sm2Base64Verify(PLAIN, signature, SM2_PUBLIC_KEY))
                    .as("未篡改时应验签通过").isTrue();

            // and：篡改签名首字符（改变 r 值高位，保持 Base64 合法）
            String tamperedSig = tamperFirstChar(signature);
            assertThat(SecurityUtil.sm2Base64Verify(PLAIN, tamperedSig, SM2_PUBLIC_KEY))
                    .as("篡改签名后验签应失败").isFalse();

            // and：篡改原文
            assertThat(SecurityUtil.sm2Base64Verify(PLAIN + "x", signature, SM2_PUBLIC_KEY))
                    .as("篡改原文后验签应失败").isFalse();
        }

        @Test
        @DisplayName("val 为 null 时直接返回 false（不抛异常）")
        void sm2VerifyNullValReturnsFalse() {
            assertThat(SecurityUtil.sm2Base64Verify(null, "sig", SM2_PUBLIC_KEY))
                    .as("null 明文验签应返回 false").isFalse();
        }

        @Test
        @DisplayName("非本密钥对的公钥验签失败")
        void sm2VerifyWithWrongPublicKey() {
            // given
            String signature = SecurityUtil.sm2Base64Sign(PLAIN, SM2_PRIVATE_KEY);
            String otherPublicKey = new SM2().getPublicKeyBase64();
            // then
            assertThat(SecurityUtil.sm2Base64Verify(PLAIN, signature, otherPublicKey))
                    .as("他钥公钥验签应失败").isFalse();
        }
    }

    // ------------------------------------------------------------------
    // filterStr / cleanObjForStr
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("对象字符串过滤 filterStr")
    class FilterStr {

        /** 被过滤的样本对象：覆盖普通字段、静态字段、嵌套对象、集合、Map、Object[] */
        static class Sample {
            static final String CONSTANT = "keep-static";
            static int counter = 7;
            String name = "name-raw";
            Integer count = 3;
            Inner inner = new Inner();
            List<String> tags = new ArrayList<>(List.of("tag-raw", "tag2-raw"));
            Map<String, String> env = new HashMap<>(Map.of("k-raw", "v-raw"));
            Object[] arr = new Object[]{"elem-raw", 42};
        }

        static class Inner {
            String secret = "secret-raw";
        }

        static class Node {
            String value;
            Node self;
        }

        /** 回调：把 "-raw" 后缀替换为 "-clean" */
        private static String mask(String s) {
            return s == null ? null : s.replace("-raw", "-clean");
        }

        @Test
        @DisplayName("深度过滤对象内所有字符串字段，静态字段跳过，非字符串简单类型不动")
        void deepFilterObject() {
            // given
            Sample sample = new Sample();
            // when
            SecurityUtil.filterStr(sample, (o, field, s) -> mask(s));
            // then
            assertThat(sample.name).as("顶层字符串字段应被过滤").isEqualTo("name-clean");
            assertThat(sample.count).as("非字符串简单类型字段应原样保留").isEqualTo(3);
            assertThat(sample.inner.secret).as("嵌套对象字段应递归过滤").isEqualTo("secret-clean");
            assertThat(sample.tags).as("集合元素应被过滤").containsExactly("tag-clean", "tag2-clean");
            assertThat(sample.env).as("Map 键值都应被过滤").containsEntry("k-clean", "v-clean");
            assertThat(sample.arr[0]).as("Object[] 字符串元素应被过滤").isEqualTo("elem-clean");
            assertThat(sample.arr[1]).as("Object[] 非字符串元素应原样保留").isEqualTo(42);
            assertThat(Sample.CONSTANT).as("static final 字段不应被改动").isEqualTo("keep-static");
            assertThat(Sample.counter).as("static 字段不应被改动").isEqualTo(7);
        }

        @Test
        @DisplayName("null 与简单类型直通：String 走回调，其余原样返回")
        void passthroughSimpleTypes() {
            // when / then
            assertThat(SecurityUtil.filterStr(null, (o, f, s) -> mask(s))).as("null 应直通返回 null").isNull();
            assertThat((String) SecurityUtil.filterStr("s-raw", (o, f, s) -> mask(s)))
                    .as("String 入参应直接走回调").isEqualTo("s-clean");
            assertThat(SecurityUtil.filterStr(123, (o, f, s) -> mask(s)))
                    .as("简单类型应原样返回").isEqualTo(123);
        }

        @Test
        @DisplayName("已废弃入口 cleanObjForStr 委托 filterStr，行为一致")
        void deprecatedCleanObjForStrDelegates() {
            assertThat(SecurityUtil.cleanObjForStr("d-raw", FilterStr::mask))
                    .as("cleanObjForStr 应等价于 filterStr 的字符串回调").isEqualTo("d-clean");
        }

        @Test
        @DisplayName("自引用对象不死循环（过滤缓存生效）")
        void selfReferenceTerminates() {
            // given
            Node node = new Node();
            node.self = node;
            node.value = "v-raw";
            // when
            SecurityUtil.filterStr(node, (o, f, s) -> mask(s));
            // then：能正常返回且字段被过滤即证明未死循环
            assertThat(node.value).as("自引用场景应正常完成过滤").isEqualTo("v-clean");
        }

        @Test
        @DisplayName("Collections.unmodifiableList 字段：告警跳过，不抛异常")
        void unmodifiableListFieldIsSkippedSilently() {
            // given：不可变容器走统一降级路径（无法构造同类临时容器 → warn + 跳过）
            ListHolder holder = new ListHolder();
            // when
            SecurityUtil.filterStr(holder, (o, f, s) -> mask(s));
            // then：不抛异常即通过；内容保持原样
            assertThat(holder.frozen).as("不可变 List 保持原样").containsExactly("keep-raw");
        }

        static class ListHolder {
            List<String> frozen = java.util.Collections.unmodifiableList(new ArrayList<>(List.of("keep-raw")));
        }

        @Test
        @DisplayName("BUG-2 修复后行为：不可变 Map/工厂集合字段不再崩溃，跳过且其余字段正常过滤")
        void immutableMapAndFactoryCollectionsAreSkippedNotCrash() {
            // given：同一对象内混布普通字段与三类不可变容器字段
            // 修复前现状：unmodifiableMap（processMap 无防御）与 Map.of/List.of（绕过字样检查）
            // 均在 ReflectUtil.newInstance 处抛 UtilException 逃逸；修复后统一降级为告警跳过
            MixedHolder holder = new MixedHolder();
            // when
            SecurityUtil.filterStr(holder, (o, f, s) -> mask(s));
            // then：不崩 + 不可变字段保持原样 + 其余字段正常过滤
            assertThat(holder.name).as("普通字符串字段应正常被过滤").isEqualTo("name-clean");
            assertThat(holder.mutable).as("可变集合字段应正常被过滤").containsExactly("m-clean");
            assertThat(holder.unmodifiableMap).as("unmodifiableMap 字段应跳过保持原样")
                    .containsEntry("k-raw", "v-raw");
            assertThat(holder.factoryMap).as("Map.of 字段应跳过保持原样").containsEntry("mk-raw", "mv-raw");
            assertThat(holder.factoryList).as("List.of 字段应跳过保持原样").containsExactly("fl-raw");
        }

        static class MixedHolder {
            String name = "name-raw";
            List<String> mutable = new ArrayList<>(List.of("m-raw"));
            Map<String, String> unmodifiableMap =
                    java.util.Collections.unmodifiableMap(new HashMap<>(Map.of("k-raw", "v-raw")));
            Map<String, String> factoryMap = Map.of("mk-raw", "mv-raw");
            List<String> factoryList = List.of("fl-raw");
        }

        @Test
        @DisplayName("顶层不可变集合入参同样降级跳过，不抛异常")
        void topLevelImmutableCollectionIsSkipped() {
            // given / when / then：修复前 List.of 顶层入参抛 UtilException，现应为安全直通
            assertThat(SecurityUtil.filterStr(List.of("a-raw"), (o, f, s) -> mask(s)))
                    .as("顶层不可变集合应原样返回").isEqualTo(List.of("a-raw"));
        }

        @Test
        @DisplayName("顶层集合/Map 入参被原地清洗")
        void topLevelCollectionAndMap() {
            // given
            List<String> list = new ArrayList<>(List.of("a-raw", "b-raw"));
            Map<String, String> map = new HashMap<>(Map.of("k-raw", "v-raw"));
            // when
            SecurityUtil.filterStr(list, (o, f, s) -> mask(s));
            SecurityUtil.filterStr(map, (o, f, s) -> mask(s));
            // then
            assertThat(list).as("顶层集合应被原地清洗").containsExactly("a-clean", "b-clean");
            assertThat(map).as("顶层 Map 应被原地清洗").containsEntry("k-clean", "v-clean");
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * 替换 Base64 串第一个字符（首字符必为数据位；末字符可能是 '=' 填充，替换会被解码器忽略），
     * 用于篡改密文/签名且保持 Base64 合法性。
     */
    private static String tamperFirstChar(String base64) {
        char first = base64.charAt(0);
        char replacement = first == 'A' ? 'B' : 'A';
        return replacement + base64.substring(1);
    }

    /** 解密类负路径断言：要么抛异常，要么解出的不是原文（两者任一即证明篡改/错钥被感知） */
    private static void assertNotRecoverable(Supplier<String> decryption, String description) {
        String recovered;
        try {
            recovered = decryption.get();
        } catch (Exception expected) {
            // 以异常形式失败——可直接采信
            return;
        }
        assertThat(recovered)
                .as(description + " 不应还原出原文")
                .isNotEqualTo(PLAIN);
    }

    /** 反射重置 SecurityUtil 的静态密钥字段，隔离用例间状态 */
    private static void setStaticField(String fieldName, Object value) {
        try {
            Field field = SecurityUtil.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("重置 SecurityUtil 静态字段失败：" + fieldName, e);
        }
    }
}
