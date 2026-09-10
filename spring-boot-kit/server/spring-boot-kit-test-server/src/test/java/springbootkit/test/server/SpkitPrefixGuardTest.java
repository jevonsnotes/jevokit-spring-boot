package springbootkit.test.server;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import springbootkit.core.util.SecurityUtil;
import springbootkit.desensitization.util.DesUtil;
import springbootkit.redis.cache.GlobalCacheKeyPrefix;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 配置前缀回归防线（springbootkit &rarr; spkit 改名的用例固化，2026-09-10 复测后补强）。
 *
 * <p>背景：{@link SpkitCryptoDefaultsTest} 以硬编码字符串 {@code bind("spkit.crypto")} 验证 yml 侧，
 * 变异实测证明 Java 侧前缀回退（如 @ConfigurationProperties 改回旧前缀）它测不出；且其余 11 个
 * 前缀当时无任何防线。本测试补齐 Java 侧：
 * <ol>
 *   <li>classpath 扫描全部 {@code @ConfigurationProperties} 注解（新增 Properties 类自动被覆盖，
 *       不硬编码类清单——避免"清单投影损失"教训 docs/lessons/bug-rename-refactor-checklist-projection-loss-20260910.md）；</li>
 *   <li>反射断言 {@code SecurityUtil.CFG_PREFIX} 与 {@code GlobalCacheKeyPrefix} 的 @Value 配置键；</li>
 *   <li>{@code DesUtil} 的配置键是方法体内联字面量，无法反射——扫描其 .class 字节码中的旧前缀
 *       点号形态（class 常量池中 FQCN 一律 {@code '/'} 分隔，{@code '.'} 形态仅出现在字符串字面量，
 *       即配置键/提示文案）。</li>
 * </ol>
 *
 * <p>断言期望值均为本测试自持字面量（"spkit." / "springbootkit"），不引用被测值，避免自证；
 * 扫描数量下限为内置阳性对照——扫描机制静默失配时本测试失败而非假通过
 * （教训 docs/lessons/bug-glob-brace-pattern-silent-mismatch-20260910.md：零命中必须先证明检索通路有效）。
 */
public class SpkitPrefixGuardTest {

    /** 新前缀（测试自持字面量，勿改为引用任何被测代码的常量） */
    private static final String NEW_PREFIX = "spkit.";

    /** 旧前缀（测试自持字面量，用于"不含"方向的独立判定） */
    private static final String OLD_PREFIX = "springbootkit";

    /** 阳性对照下限：当前仓库 @ConfigurationProperties 类数量。扫描静默失配（0 命中）时拒绝对空集放行 */
    private static final int MIN_PROPERTIES_CLASSES = 10;

    @Test
    public void allConfigPrefixesUseSpkit() throws Exception {
        // --- 1. classpath 扫描全部 @ConfigurationProperties ---
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));
        Set<BeanDefinition> candidates = scanner.findCandidateComponents("springbootkit");

        List<String> seen = new ArrayList<>();
        for (BeanDefinition bd : candidates) {
            Class<?> clazz = Class.forName(bd.getBeanClassName(), false, getClass().getClassLoader());
            // AnnotationUtils 做注解合成，归一 value/prefix 的 @AliasFor（直接 getAnnotation 读别名属性会得到空串）
            ConfigurationProperties props = AnnotationUtils.findAnnotation(clazz, ConfigurationProperties.class);
            assertNotNull("扫描候选缺失注解：" + clazz, props);
            String prefix = props.prefix();
            seen.add(clazz.getSimpleName() + "->" + prefix);
            assertFalse(clazz.getName() + " 的 @ConfigurationProperties 前缀为空", prefix.isEmpty());
            assertTrue(clazz.getName() + " 前缀应以 " + NEW_PREFIX + " 开头，实际：" + prefix,
                    prefix.startsWith(NEW_PREFIX));
            assertFalse(clazz.getName() + " 前缀含旧前缀 " + OLD_PREFIX + "：" + prefix,
                    prefix.contains(OLD_PREFIX));
        }
        assertTrue("classpath 扫描到的 @ConfigurationProperties 仅 " + seen.size() + " 个，低于下限 "
                + MIN_PROPERTIES_CLASSES + "（扫描机制可能静默失配，拒绝对空集放行）。实扫清单：" + seen,
                seen.size() >= MIN_PROPERTIES_CLASSES);

        // --- 2. SecurityUtil.CFG_PREFIX（private static final，反射读取） ---
        Field cfgPrefix = SecurityUtil.class.getDeclaredField("CFG_PREFIX");
        cfgPrefix.setAccessible(true);
        String utilPrefix = (String) cfgPrefix.get(null);
        assertTrue("SecurityUtil.CFG_PREFIX 应以 " + NEW_PREFIX + " 开头，实际：" + utilPrefix,
                utilPrefix.startsWith(NEW_PREFIX));
        assertFalse("SecurityUtil.CFG_PREFIX 含旧前缀：" + utilPrefix, utilPrefix.contains(OLD_PREFIX));

        // --- 3. GlobalCacheKeyPrefix 的 @Value 配置键 ---
        Field field = GlobalCacheKeyPrefix.class.getField("CACHE_NAMESPACE_PATTERN");
        Value value = field.getAnnotation(Value.class);
        assertNotNull("GlobalCacheKeyPrefix.CACHE_NAMESPACE_PATTERN 缺 @Value 注解", value);
        Matcher m = Pattern.compile("\\$\\{([^:}]+)").matcher(value.value());
        assertTrue("无法从 @Value 提取配置键：" + value.value(), m.find());
        String cacheKey = m.group(1);
        assertTrue("GlobalCacheKeyPrefix 的 @Value 键应以 " + NEW_PREFIX + " 开头，实际：" + cacheKey,
                cacheKey.startsWith(NEW_PREFIX));
        assertFalse("GlobalCacheKeyPrefix 的 @Value 含旧前缀：" + value.value(),
                value.value().contains(OLD_PREFIX));

        // --- 4. DesUtil 内联字面量（无法反射，扫描 .class 字节码中旧前缀的点号形态） ---
        byte[] classBytes;
        try (InputStream in = DesUtil.class.getResourceAsStream("DesUtil.class")) {
            assertNotNull("classpath 上找不到 DesUtil.class 资源", in);
            classBytes = in.readAllBytes();
        }
        String constantPoolText = new String(classBytes, StandardCharsets.ISO_8859_1);
        assertFalse("DesUtil.class 字符串常量中检测到旧前缀点号形态 " + OLD_PREFIX + "."
                        + "（配置键/文案回退；class 文件中 FQCN 为 '/' 分隔，'.' 形态即配置键字面量）",
                constantPoolText.contains(OLD_PREFIX + "."));
    }
}
