package io.github.jevonsnotes.jevokit.desensitization.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.jevonsnotes.jevokit.desensitization.DesensitizationTestSupport;
import io.github.jevonsnotes.jevokit.desensitization.config.AlgorithmProperties;
import io.github.jevonsnotes.jevokit.desensitization.config.JevokitDesensitizationProperties;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstAlgorithm;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstConst;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstProcessType;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.desensitizer.DefaultDesensitizer;
import io.github.jevonsnotes.jevokit.desensitization.desensitizer.Desensitizer;
import io.github.jevonsnotes.jevokit.desensitization.dto.DstContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DesUtil Spring 依赖路径：des/desByProperties/decrypt/power/currentAlgorithm。
 * 经 DesensitizationTestSupport 装配最小上下文（SpringUtil + properties + DesHelper + SM4 密钥）。
 */
class DesUtilDesTest {

    private static final String MOBILE = "13800138000";

    /** DesUtil 走 SecurityUtil 统一密钥入口（静态有状态）：用例前后重置，防先注入的密钥污染缺失语义用例 */
    @BeforeEach
    void resetKeys() {
        DesensitizationTestSupport.resetSecurityUtilKeys();
    }

    @AfterEach
    void cleanUp() {
        DesensitizationTestSupport.resetSecurityUtilKeys();
    }

    private static JevokitDesensitizationProperties mobileNameProps() {
        return DesensitizationTestSupport.properties(
                Map.of(DstLevel.LV0, Map.of(
                        DstType.MOBILE, List.of("mobile", "phone"),
                        DstType.NAME, List.of("name", "applicantName"))),
                List.of(),
                Map.of());
    }

    @Test
    @DisplayName("des：BASIC 算法（默认）直接掩码")
    void desBasicMasksValue() {
        DesensitizationTestSupport.minimalRunner(mobileNameProps()).run(context -> {
            assertThat(context).hasNotFailed();
            String masked = DesUtil.des(MOBILE, DstContext.builder().type(DstType.MOBILE).build());
            assertThat(masked).as("BASIC 脱敏 11 位手机号").isEqualTo("138****8000");
        });
    }

    @Test
    @DisplayName("des：@DST 前缀的已脱敏数据不重复处理（幂等）")
    void desSkipsAlreadyDesensitized() {
        DesensitizationTestSupport.minimalRunner(mobileNameProps()).run(context -> {
            String enc = DesUtil.processSM4(MOBILE, DstContext.builder().type(DstType.MOBILE).level(DstLevel.LV0).build());
            assertThat(DesUtil.des(enc, DstContext.builder().type(DstType.MOBILE).build()))
                    .as("@DST 密文应原样返回")
                    .isEqualTo(enc);
        });
    }

    @Test
    @DisplayName("des：dstContext 为 null 时原样返回")
    void desNullContextPassthrough() {
        DesensitizationTestSupport.minimalRunner(mobileNameProps()).run(context ->
                assertThat(DesUtil.des(MOBILE, null)).as("无上下文不脱敏").isEqualTo(MOBILE));
    }

    @Test
    @DisplayName("des：SM4 算法输出 @DST 密文并可解密回原值")
    void desSm4RoundTrip() {
        JevokitDesensitizationProperties properties = mobileNameProps();
        AlgorithmProperties algorithm = new AlgorithmProperties();
        algorithm.setView(DstAlgorithm.SM4);
        properties.setAlgorithm(algorithm);

        DesensitizationTestSupport.minimalRunner(properties).run(context -> {
            String enc = DesUtil.des(MOBILE, DstContext.builder().type(DstType.MOBILE).level(DstLevel.LV0).build());
            assertThat(enc).as("SM4 输出必须以 @DST( 开头 ) 结尾").startsWith(DstConst.DESENSITIZED_ENC_KEY + "(").endsWith(")");
            assertThat(enc).as("密文不含明文手机号").doesNotContain(MOBILE);

            String dec = DesUtil.decrypt(enc, true);
            assertThat(dec).as("解密（忽略权限）应还原明文").isEqualTo(MOBILE);
        });
    }

    @Test
    @DisplayName("des：MIXED 算法输出 密文|基础脱敏 双段")
    void desMixedProducesCiphertextAndMask() {
        JevokitDesensitizationProperties properties = mobileNameProps();
        AlgorithmProperties algorithm = new AlgorithmProperties();
        algorithm.setView(DstAlgorithm.MIXED);
        properties.setAlgorithm(algorithm);

        DesensitizationTestSupport.minimalRunner(properties).run(context -> {
            String mixed = DesUtil.des(MOBILE, DstContext.builder().type(DstType.MOBILE).level(DstLevel.LV0).build());
            assertThat(mixed).as("MIXED 输出形如 @DST(<enc>|<mask>)")
                    .startsWith("@DST(")
                    .endsWith(")")
                    .contains("|")
                    .contains("138****8000");
        });
    }

    @Test
    @DisplayName("des：未配置 sm4-key 时抛 IllegalStateException（与 SecurityUtil requireKey 行为一致）")
    void desSm4WithoutKeyFails() {
        JevokitDesensitizationProperties properties = mobileNameProps();
        AlgorithmProperties algorithm = new AlgorithmProperties();
        algorithm.setView(DstAlgorithm.SM4);
        properties.setAlgorithm(algorithm);

        // given：上下文不注入 SM4 密钥（SecurityUtil 未 init，BeforeEach 已清残留静态密钥）
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(cn.hutool.extra.spring.SpringUtil.class)
                .withBean(JevokitDesensitizationProperties.class, () -> properties)
                .run(context -> assertThatThrownBy(() ->
                                DesUtil.des(MOBILE, DstContext.builder().type(DstType.MOBILE).build()))
                        .as("未配置 jevokit.crypto.sm4-key 必须 fail-fast")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("jevokit.crypto.sm4-key"));
    }

    // ------------------------- desByProperties -------------------------

    @Test
    @DisplayName("[真] desByProperties：prop-pattern 命中字段名即脱敏（含嵌套短名匹配）")
    void desByPropertiesMatchesPatterns() {
        DesensitizationTestSupport.minimalRunner(mobileNameProps()).run(context -> {
            assertThat(DesUtil.desByProperties("mobile", MOBILE)).as("mobile 字段命中 MOBILE 规则").isEqualTo("138****8000");
            assertThat(DesUtil.desByProperties("applicantInfo.mobile", MOBILE)).as("嵌套字段取短名匹配").isEqualTo("138****8000");
            assertThat(DesUtil.desByProperties("applicantName", "张三丰")).as("applicantName 命中 NAME 规则").isEqualTo("张*丰");
        });
    }

    @Test
    @DisplayName("[假] desByProperties：不命中字段原样返回")
    void desByPropertiesNonMatchUnchanged() {
        DesensitizationTestSupport.minimalRunner(mobileNameProps()).run(context ->
                assertThat(DesUtil.desByProperties("remark", "13800138000"))
                        .as("未配置的字段名不脱敏")
                        .isEqualTo("13800138000"));
    }

    @Test
    @DisplayName("desByProperties：ignore-pattern 优先于 prop-pattern")
    void ignorePatternWins() {
        JevokitDesensitizationProperties properties = DesensitizationTestSupport.properties(
                Map.of(DstLevel.LV0, Map.of(DstType.MOBILE, List.of("mobile"))),
                List.of("mobile"),
                Map.of());
        DesensitizationTestSupport.minimalRunner(properties).run(context ->
                assertThat(DesUtil.desByProperties("mobile", MOBILE))
                        .as("ignore 命中时即使有规则也放行")
                        .isEqualTo(MOBILE));
    }

    @Test
    @DisplayName("desByProperties：含反斜杠点号的模式按全名匹配")
    void dottedPatternMatchesFullName() {
        JevokitDesensitizationProperties properties = DesensitizationTestSupport.properties(
                Map.of(DstLevel.LV0, Map.of(DstType.MOBILE, List.of("payload\\.mobile"))),
                List.of(),
                Map.of());
        DesensitizationTestSupport.minimalRunner(properties).run(context -> {
            assertThat(DesUtil.desByProperties("payload.mobile", MOBILE))
                    .as("点号模式匹配全名")
                    .isEqualTo("138****8000");
            assertThat(DesUtil.desByProperties("mobile", MOBILE))
                    .as("点号模式下短名不匹配")
                    .isEqualTo(MOBILE);
        });
    }

    @Test
    @DisplayName("desByProperties：非法正则模式不阻断（告警吞掉）")
    void invalidPatternDoesNotBreakProcessing() {
        JevokitDesensitizationProperties properties = DesensitizationTestSupport.properties(
                Map.of(DstLevel.LV0, Map.of(DstType.MOBILE, List.of("mobile(", "mobile"))),
                List.of(),
                Map.of());
        DesensitizationTestSupport.minimalRunner(properties).run(context ->
                assertThat(DesUtil.desByProperties("mobile", MOBILE))
                        .as("坏模式跳过后好模式仍生效")
                        .isEqualTo("138****8000"));
    }

    // ------------------------- decrypt / 权限 -------------------------

    @Test
    @DisplayName("decrypt：多段密文一次性解密，非密文文本保留")
    void decryptMultipleSections() {
        DesensitizationTestSupport.minimalRunner(mobileNameProps()).run(context -> {
            String enc1 = DesUtil.processSM4(MOBILE, DstContext.builder().type(DstType.MOBILE).level(DstLevel.LV0).build());
            String enc2 = DesUtil.processSM4("张三丰", DstContext.builder().type(DstType.NAME).level(DstLevel.LV0).build());
            String text = "手机=" + enc1 + " 姓名=" + enc2 + " 尾注";
            String dec = DesUtil.decrypt(text, true);
            assertThat(dec).as("两段密文均还原").contains(MOBILE).contains("张三丰").endsWith("尾注");
        });
    }

    @Test
    @DisplayName("decrypt：ignorePower=false 且无权限（默认拦截器恒 false）时抛异常")
    void decryptPowerDeniedThrows() {
        JevokitDesensitizationProperties properties = DesensitizationTestSupport.properties(
                Map.of(),
                List.of(),
                Map.of(DstLevel.LV0, List.of("power.admin")));
        DesensitizationTestSupport.minimalRunner(properties).run(context -> {
            String enc = DesUtil.processSM4(MOBILE, DstContext.builder().type(DstType.MOBILE).level(DstLevel.LV0).build());
            assertThatThrownBy(() -> DesUtil.decrypt(enc, false))
                    .as("无权限查看 LV0 数据必须被拒绝")
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("LV0");
        });
    }

    @Test
    @DisplayName("decrypt：垃圾密文解密失败抛异常（fail-fast）")
    void decryptGarbageThrows() {
        DesensitizationTestSupport.minimalRunner(mobileNameProps()).run(context ->
                assertThatThrownBy(() -> DesUtil.decrypt("@DST(not-a-cipher)", true))
                        .as("非密文内容进入解密通道应抛异常")
                        .isInstanceOf(RuntimeException.class));
    }

    @Test
    @DisplayName("decrypt：解密成功但无级别前缀的内容原样跳过")
    void decryptNoLevelPrefixSkipped() {
        DesensitizationTestSupport.minimalRunner(mobileNameProps()).run(context -> {
            String enc = io.github.jevonsnotes.jevokit.core.util.SecurityUtil.sm4cbcEncrypt("无级别前缀内容",
                    DesensitizationTestSupport.TEST_SM4_KEY);
            String wrapped = "@DST(" + enc + ")";
            assertThat(DesUtil.decrypt(wrapped, true))
                    .as("非 [LV*] 前缀的解密结果不进入权限与还原流程")
                    .isEqualTo(wrapped);
        });
    }

    // ------------------------- 算法选择 / processType -------------------------

    @Test
    @DisplayName("currentAlgorithm：VIEW/LOG 分别取 view/log 算法配置")
    void currentAlgorithmPerProcessType() {
        JevokitDesensitizationProperties properties = mobileNameProps();
        AlgorithmProperties algorithm = new AlgorithmProperties();
        algorithm.setView(DstAlgorithm.BASIC);
        algorithm.setLog(DstAlgorithm.SM4);
        properties.setAlgorithm(algorithm);

        DesensitizationTestSupport.minimalRunner(properties).run(context -> {
            assertThat(DesUtil.currentAlgorithm()).as("缺省（VIEW）取 view 配置").isEqualTo(DstAlgorithm.BASIC);
            DesUtil.desByProcessTypeWrap(DstProcessType.LOG, () ->
                    assertThat(DesUtil.currentAlgorithm()).as("LOG 包装内取 log 配置").isEqualTo(DstAlgorithm.SM4));
            assertThat(DesUtil.currentAlgorithm()).as("包装结束后恢复（ThreadLocal 清理）").isEqualTo(DstAlgorithm.BASIC);
        });
    }

    @Test
    @DisplayName("desByProcessTypeWrap：无返回值回调同样生效并清理")
    void desByProcessTypeWrapVoidCallback() {
        JevokitDesensitizationProperties properties = mobileNameProps();
        AlgorithmProperties algorithm = new AlgorithmProperties();
        algorithm.setLog(DstAlgorithm.MIXED);
        properties.setAlgorithm(algorithm);

        DesensitizationTestSupport.minimalRunner(properties).run(context -> {
            DesUtil.desByProcessTypeWrap(DstProcessType.LOG, () ->
                    assertThat(DesUtil.currentAlgorithm()).isEqualTo(DstAlgorithm.MIXED));
            assertThat(DesUtil.currentAlgorithm()).isEqualTo(DstAlgorithm.BASIC);
        });
    }

    // ------------------------- Desensitizer 缓存 -------------------------

    @Test
    @DisplayName("getDesensitizer：同类复用缓存实例（BUG-10 修复后行为）")
    void desensitizerCached() {
        Desensitizer first = DesUtil.getDesensitizer(DefaultDesensitizer.class);
        Desensitizer second = DesUtil.getDesensitizer(DefaultDesensitizer.class);
        assertThat(first).as("实例可用").isNotNull();
        assertThat(second).as("缓存命中必须返回同一实例").isSameAs(first);
    }

    @Test
    @DisplayName("DefaultDesensitizer：des 委托 DesUtil.des（BASIC 掩码）")
    void defaultDesensitizerDelegates() {
        DesensitizationTestSupport.minimalRunner(mobileNameProps()).run(context -> {
            Desensitizer desensitizer = DesUtil.getDesensitizer(DefaultDesensitizer.class);
            String masked = desensitizer.des(null, null, MOBILE,
                    DstContext.builder().type(DstType.MOBILE).build());
            assertThat(masked).as("DefaultDesensitizer 走 DesUtil.des 通路").isEqualTo("138****8000");
        });
    }
}
