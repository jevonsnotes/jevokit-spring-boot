package io.github.jevonsnotes.jevokit.jasypt.util;

import com.ulisesbocchio.jasyptspringboot.detector.DefaultPropertyDetector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import io.github.jevonsnotes.jevokit.jasypt.encryptor.JevokitStringEncryptor;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BUG-6 对抗性复测（终验 #6）：独立于开发 JasyptUtilTest 的增量——
 *
 * <p>开发用例已证「二次构造抛错 + 旧密文可解」（功能面）；本类补三刀：
 * <ol>
 *   <li><b>直接证明</b>：反射读取静态 {@code instance} 字段，断言拒绝二次构造前后
 *       <b>同一引用</b>（功能等价 ≠ 引用未变，反射才是"全局引用不变"的直接证据）；</li>
 *   <li><b>新密文往返</b>：拒绝之后新产出的密文也能解——门面完全可用，而非仅旧密文幸存；</li>
 *   <li><b>顺序无关且不侵入</b>：门面是 JVM 级单例，本类若先于 JasyptUtilTest 运行则自锁、
 *       并在 @AfterAll 反射复位（谁锁定谁复位）——两类互不干扰，任何执行顺序下均成立。</li>
 * </ol>
 * 真实上下文单 bean 无回归：sample 模块默认套件（含 jasypt 装配的完整上下文）全绿佐证。
 */
@DisplayName("BUG-6 复测：二次构造拒绝后全局引用不变（对抗性）")
class JasyptUtilFacadeLockAdversarialTest {

    private static final String PLAIN = "adversarial-plain";
    /** 本类是否自行锁定了门面（先于 JasyptUtilTest 运行时）——锁定者负责复位 */
    private static boolean selfLocked = false;

    private static Field instanceField() throws Exception {
        Field field = JasyptUtil.class.getDeclaredField("instance");
        field.setAccessible(true);
        return field;
    }

    /** 顺序无关：若门面尚未锁定（本类先于 JasyptUtilTest 运行），自锁之 */
    private static Object ensureLockedAndGet() throws Exception {
        Field field = instanceField();
        if (field.get(null) == null) {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("jasypt.encryptor.password", "pw-adversarial-first");
            new JasyptUtil(new JevokitStringEncryptor(env), new DefaultPropertyDetector());
            selfLocked = true;
        }
        return field.get(null);
    }

    @AfterAll
    static void restorePristineStateIfSelfLocked() throws Exception {
        // 若本类是锁定者，复位静态门面——保证 JasyptUtilTest 的 @BeforeAll 构造（无条件 new）仍可执行
        if (selfLocked) {
            instanceField().set(null, null);
            selfLocked = false;
        }
    }

    @Test
    @DisplayName("二次构造拒绝后：instance 反射同引用 + 门面新密文往返依旧可用")
    void rejectedSecondConstructionLeavesGlobalReferenceUntouched() throws Exception {
        // given：门面已锁定（无论被谁锁定），快照全局引用
        Object instanceBefore = ensureLockedAndGet();
        assertThat(instanceBefore).as("门面应处于已锁定状态").isNotNull();
        // and：锁定实例正常出一份密文（拒绝前对照）
        String cipherBefore = JasyptUtil.encrypt(PLAIN);

        // when：持不同口令的加密器试图二次构造
        MockEnvironment otherEnv = new MockEnvironment();
        otherEnv.setProperty("jasypt.encryptor.password", "pw-adversarial-usurper");
        assertThatThrownBy(() -> new JasyptUtil(
                new JevokitStringEncryptor(otherEnv), new DefaultPropertyDetector()))
                .as("二次构造必须显式拒绝（历史缺陷：静默偷换全局引用）")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("禁止重复构造");

        // then 1：全局引用与拒绝前是同一对象（直接证明，非功能推断）
        assertThat(instanceField().get(null))
                .as("拒绝二次构造后全局引用必须保持不变")
                .isSameAs(instanceBefore);

        // then 2：旧密文可解（eng 已测，保留作链路完整性）
        assertThat(JasyptUtil.decrypt("ENC(" + cipherBefore + ")"))
                .as("拒绝后旧密文应依旧可解").isEqualTo(PLAIN);

        // then 3：新密文往返也可用——门面完全可用，而非仅旧数据幸存
        assertThatCode(() -> {
            String freshCipher = JasyptUtil.encrypt(PLAIN + "-v2");
            assertThat(JasyptUtil.decrypt("ENC(" + freshCipher + ")"))
                    .as("拒绝后新产出的密文应能正常往返")
                    .isEqualTo(PLAIN + "-v2");
        }).as("拒绝后门面应保持完全可用").doesNotThrowAnyException();
    }
}
