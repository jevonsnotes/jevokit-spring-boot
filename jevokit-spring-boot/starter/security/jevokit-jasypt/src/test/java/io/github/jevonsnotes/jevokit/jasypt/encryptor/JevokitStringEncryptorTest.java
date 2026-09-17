package io.github.jevonsnotes.jevokit.jasypt.encryptor;

import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JevokitStringEncryptor 口令解析链单测（B 档测试重构 #5）。
 * 覆盖：password &gt; jep 优先级、缺失/空白口令 fail-fast、宽松键名回退（camel/symbol 三段式）。
 * 直接以 MockEnvironment 构造，不启动 Spring 容器。
 */
class JevokitStringEncryptorTest {

    private static final String PLAIN = "kit-secret-plain";

    /** kv 成对构造 MockEnvironment（当前 spring-test 的 setProperty 非链式） */
    private MockEnvironment env(String... kv) {
        MockEnvironment environment = new MockEnvironment();
        for (int i = 0; i < kv.length; i += 2) {
            environment.setProperty(kv[i], kv[i + 1]);
        }
        return environment;
    }

    private JevokitStringEncryptor encryptor(Environment environment) {
        return new JevokitStringEncryptor(environment);
    }

    @Test
    @DisplayName("口令解析：jasypt.encryptor.password 存在时可加解密往返")
    void passwordProperty_roundTrip() {
        JevokitStringEncryptor encryptor = encryptor(env("jasypt.encryptor.password", "pw-primary"));
        String cipher = encryptor.encrypt(PLAIN);

        assertThat(cipher).as("加密产物应为非空串").isNotBlank();
        assertThat(encryptor.decrypt(cipher)).as("同一加密器应可往返解密").isEqualTo(PLAIN);
    }

    @Test
    @DisplayName("口令解析：password 缺失时回退 jasypt.encryptor.jep")
    void jepFallback_whenPasswordAbsent() {
        JevokitStringEncryptor encryptor = encryptor(env("jasypt.encryptor.jep", "pw-jep"));

        assertThat(encryptor.decrypt(encryptor.encrypt(PLAIN)))
                .as("仅有 jep 时应以其为口令完成往返").isEqualTo(PLAIN);
    }

    @Test
    @DisplayName("口令解析：password 与 jep 同时存在时 password 优先")
    void passwordTakesPrecedenceOverJep() {
        // 以 password 口令加密 → (password+jep) 加密器可解；以 jep 口令加密的密文 → 解不开
        String cipherByPassword = encryptor(env("jasypt.encryptor.password", "pw-primary")).encrypt(PLAIN);
        String cipherByJep = encryptor(env("jasypt.encryptor.jep", "pw-jep")).encrypt(PLAIN);
        JevokitStringEncryptor both = encryptor(env(
                "jasypt.encryptor.password", "pw-primary",
                "jasypt.encryptor.jep", "pw-jep"));

        assertThat(both.decrypt(cipherByPassword))
                .as("password 优先：其密文应可解").isEqualTo(PLAIN);
        assertThatThrownBy(() -> both.decrypt(cipherByJep))
                .as("jep 口令密文不应被 password 口令解开")
                .isInstanceOf(EncryptionOperationNotPossibleException.class);
    }

    @Test
    @DisplayName("fail-fast：口令缺失时首次加/解密抛 IllegalStateException 并提示两个键")
    void missingPassword_failsFastWithHint() {
        JevokitStringEncryptor encryptor = encryptor(new StandardEnvironment());

        assertThatThrownBy(() -> encryptor.encrypt("anything"))
                .as("未配置任何口令应 fail-fast")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jasypt.encryptor.password")
                .hasMessageContaining("jasypt.encryptor.jep");
    }

    @Test
    @DisplayName("fail-fast：口令为空白串（如 ${JASYPT_KEY:} 未注入）同样 fail-fast")
    void blankPassword_failsFast() {
        JevokitStringEncryptor encryptor = encryptor(env("jasypt.encryptor.password", " "));

        assertThatThrownBy(() -> encryptor.decrypt("any cipher"))
                .as("空白口令等价于未配置，应 fail-fast 而非静默使用空口令")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("宽松键名：symbol-case 的 salt/iv 生成器可覆盖（零盐+无IV→密文全确定）")
    void relaxedKey_symbolCaseSaltGenerator() {
        // 键名回退链：精确 camel → toCamelCase → toSymbolCase；此处以中划线形式同时覆盖两个键。
        // 代码默认 ivGenerator=RandomIvGenerator 会在密文前追加随机 IV 前缀（长度=分组大小），
        // 叠加 ZeroSaltGenerator 后仅剩 IV 前缀随机——补 NoIvGenerator 后密文完全确定
        JevokitStringEncryptor encryptor = encryptor(env(
                "jasypt.encryptor.password", "pw",
                "jasypt.encryptor.salt-generator-classname", "org.jasypt.salt.ZeroSaltGenerator",
                "jasypt.encryptor.iv-generator-classname", "org.jasypt.iv.NoIvGenerator"));
        String cipher1 = encryptor.encrypt(PLAIN);
        String cipher2 = encryptor.encrypt(PLAIN);

        assertThat(cipher1).as("零盐+无IV下同明文两次加密应产生相同密文").isEqualTo(cipher2);
        assertThat(encryptor.decrypt(cipher1)).as("该配置下仍应可解密").isEqualTo(PLAIN);
    }

    @Test
    @DisplayName("宽松键名：默认随机盐下同明文两次密文不同（对照固定盐用例）")
    void defaultRandomSalt_producesDifferentCiphers() {
        JevokitStringEncryptor encryptor = encryptor(env("jasypt.encryptor.password", "pw"));

        assertThat(encryptor.encrypt(PLAIN))
                .as("默认 RandomSaltGenerator 下两次密文应不同")
                .isNotEqualTo(encryptor.encrypt(PLAIN));
    }

    @Test
    @DisplayName("宽松键名：symbol-case 的 string-output-type=hexadecimal 生效")
    void relaxedKey_symbolCaseStringOutputType() {
        JevokitStringEncryptor encryptor = encryptor(env(
                "jasypt.encryptor.password", "pw",
                "jasypt.encryptor.string-output-type", "hexadecimal"));
        String cipher = encryptor.encrypt(PLAIN);

        assertThat(cipher).as("hexadecimal 输出应为纯十六进制串").matches("[0-9a-fA-F]+");
        assertThat(encryptor.decrypt(cipher)).as("十六进制输出仍应可解密").isEqualTo(PLAIN);
    }
}
