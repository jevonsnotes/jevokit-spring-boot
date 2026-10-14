package io.github.jevonsnotes.jevokit.desensitization;

import cn.hutool.core.io.IoUtil;
import cn.hutool.extra.spring.SpringUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;
import io.github.jevonsnotes.jevokit.desensitization.config.JevokitDesensitizationProperties;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.helper.DesHelper;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.impl.DefaultSensitizationPowerInterceptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * desensitization 测试公共基座：
 * 1. fixtures 从 classpath 读取（src/test/resources/fixtures/desensitization/）；
 * 2. DesUtil 的静态依赖（SpringUtil/JevokitDesensitizationProperties/DesHelper/SecurityUtil SM4 密钥）
 *    经 ApplicationContextRunner 装配真实上下文，测试运行期与生产同通路。
 */
public final class DesensitizationTestSupport {

    /** 演示用 SM4 密钥（16 字节，与原 SimpleTest 草稿一致） */
    public static final String TEST_SM4_KEY = "0123456789abcdef";

    private DesensitizationTestSupport() {
    }

    /** 读取 fixtures/desensitization/ 下的样例文件（classpath，无外部文件系统依赖） */
    public static String fixture(String name) {
        try (InputStream in = DesensitizationTestSupport.class
                .getResourceAsStream("/fixtures/desensitization/" + name)) {
            if (in == null) {
                throw new IllegalStateException("fixture not found on classpath: " + name);
            }
            return IoUtil.read(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("fixture read failed: " + name, e);
        }
    }

    /** 构造带 propPattern/ignorePattern/levelPowerKey 的 properties（编程式，等价 yml 绑定结果） */
    public static JevokitDesensitizationProperties properties(
            Map<DstLevel, Map<DstType, List<String>>> propPattern,
            List<String> ignorePattern,
            Map<DstLevel, List<String>> levelPowerKey) {
        JevokitDesensitizationProperties properties = new JevokitDesensitizationProperties();
        properties.setPropPattern(propPattern);
        properties.setIgnorePattern(ignorePattern);
        properties.setLevelPowerKey(levelPowerKey);
        return properties;
    }

    /**
     * 满足 DesUtil 静态依赖的最小上下文（SM4 密钥与生产同通路：经 InitializingBean 灌入 SecurityUtil，
     * 等价 JevokitCoreConfiguration#cryptoKeyInitializer 读 jevokit.crypto.sm4-key 后 init 的效果）。
     * <p>注意：SecurityUtil 为静态有状态且上下文关闭不回收，依赖密钥缺失语义的用例须在
     * BeforeEach/AfterEach 调 {@link #resetSecurityUtilKeys()} 隔离残留。
     */
    public static ApplicationContextRunner minimalRunner(JevokitDesensitizationProperties properties) {
        return new ApplicationContextRunner()
                .withBean("cryptoKeyInitializer", InitializingBean.class,
                        () -> () -> SecurityUtil.init(null, null, null, TEST_SM4_KEY))
                .withBean(SpringUtil.class)
                .withBean(JevokitDesensitizationProperties.class, () -> properties)
                .withBean(DesHelper.class,
                        () -> new DesHelper(properties, new DefaultSensitizationPowerInterceptor()));
    }

    /**
     * 与 {@link #minimalRunner(JevokitDesensitizationProperties)} 同套件 bean 的"存活上下文"变体：
     * 注册同一组 bean 并 refresh 后交还调用方，由调用方在用例结束时 {@code close()}。
     * <p>为什么需要：遗留 #2 起 DesHelper 静态引用在 ContextClosedEvent 即清理（关闭即清理，
     * 不再残留）——需要跨 runner 生命周期消费静态引用（如 logback 过滤器在上下文之外打日志）
     * 的用例必须持有存活上下文，与生产"应用运行期间容器存活"语义一致。
     */
    public static AnnotationConfigApplicationContext liveMinimalContext(JevokitDesensitizationProperties properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean("cryptoKeyInitializer", InitializingBean.class,
                () -> () -> SecurityUtil.init(null, null, null, TEST_SM4_KEY));
        context.registerBean(SpringUtil.class);
        context.registerBean(JevokitDesensitizationProperties.class, () -> properties);
        context.registerBean(DesHelper.class,
                () -> new DesHelper(properties, new DefaultSensitizationPowerInterceptor()));
        context.refresh();
        return context;
    }

    /** 反射重置 SecurityUtil 静态密钥字段（init 注入后无公开清除入口，须如此隔离用例间状态） */
    public static void resetSecurityUtilKeys() {
        for (String fieldName : new String[]{"rsaPublicKey", "rsaPrivateKey", "aesKey", "sm4Key"}) {
            try {
                Field field = SecurityUtil.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(null, null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("重置 SecurityUtil 静态字段失败：" + fieldName, e);
            }
        }
    }
}
