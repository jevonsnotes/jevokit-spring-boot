package io.github.jevonsnotes.jevokit.jasypt.util;

import com.ulisesbocchio.jasyptspringboot.detector.DefaultPropertyDetector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import io.github.jevonsnotes.jevokit.jasypt.encryptor.JevokitStringEncryptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JasyptUtil 单测（B 档测试重构 #5 + BUG-6 修复验证）：ENC() 检测/解包语义、加解密往返、
 * 单实例锁定（二次构造显式失败）。组合真实组件（JevokitStringEncryptor +
 * DefaultPropertyDetector），不启动容器。
 *
 * <p>注意：JasyptUtil 是「构造即锁定」的静态门面（BUG-6 修复后），整个测试类只能构造
 * 一次——首个实例在 {@code @BeforeAll} 统一构造，各用例共享；锁定语义用例只断言
 * 「二次构造抛错 + 全局引用未变」。
 */
class JasyptUtilTest {

    private static final String PLAIN = "datasource-password-demo";
    private static final String PRIMARY_PASSWORD = "pw-test";

    private static JasyptUtil jasyptUtil;

    @BeforeAll
    static void initLockedFacade() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("jasypt.encryptor.password", PRIMARY_PASSWORD);
        jasyptUtil = new JasyptUtil(new JevokitStringEncryptor(env), new DefaultPropertyDetector());
    }

    @Test
    @DisplayName("encrypt：产出的密文可被 detector 识别为 ENC 形态")
    void encrypt_outputRecognizedAsEncrypted() {
        String cipher = JasyptUtil.encrypt(PLAIN);
        String wrapped = "ENC(" + cipher + ")";

        assertThat(new DefaultPropertyDetector().isEncrypted(wrapped))
                .as("加密产物包装 ENC() 后应被识别为密文属性").isTrue();
    }

    @Test
    @DisplayName("decrypt：接受 ENC() 包装形态并自动解包")
    void decrypt_unwrapsEncForm() {
        String wrapped = "ENC(" + JasyptUtil.encrypt(PLAIN) + ")";

        assertThat(JasyptUtil.decrypt(wrapped))
                .as("ENC() 包装的密文应被解包后解密").isEqualTo(PLAIN);
    }

    @Test
    @DisplayName("decrypt：也接受裸密文形态")
    void decrypt_acceptsBareCipher() {
        String bare = JasyptUtil.encrypt(PLAIN);

        assertThat(JasyptUtil.decrypt(bare))
                .as("裸密文应直接解密").isEqualTo(PLAIN);
    }

    @Test
    @DisplayName("BUG-6 修复后：二次构造显式抛 IllegalStateException，不再静默偷换全局引用")
    void secondConstruction_failsExplicitly() {
        // 首个实例（@BeforeAll 已构造并锁定）加密的密文
        String cipher = JasyptUtil.encrypt(PLAIN);

        MockEnvironment otherEnv = new MockEnvironment();
        otherEnv.setProperty("jasypt.encryptor.password", "pw-other");

        // 二次构造应显式失败，且失败后全局引用仍是首个实例——密文依旧可解
        assertThatThrownBy(() -> new JasyptUtil(
                new JevokitStringEncryptor(otherEnv), new DefaultPropertyDetector()))
                .as("静态门面禁止重复构造覆盖（历史缺陷：后构造者静默偷换全局引用）")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("禁止重复构造");

        assertThat(JasyptUtil.decrypt("ENC(" + cipher + ")"))
                .as("偷换失败后全局引用应仍是首个实例，原口令密文依旧可解")
                .isEqualTo(PLAIN);
    }
}
