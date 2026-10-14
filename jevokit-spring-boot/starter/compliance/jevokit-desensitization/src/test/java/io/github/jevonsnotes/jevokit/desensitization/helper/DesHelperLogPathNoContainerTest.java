package io.github.jevonsnotes.jevokit.desensitization.helper;

import cn.hutool.extra.spring.SpringUtil;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;
import io.github.jevonsnotes.jevokit.desensitization.DesensitizationTestSupport;
import io.github.jevonsnotes.jevokit.desensitization.config.JevokitDesensitizationProperties;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.impl.DefaultSensitizationPowerInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DesHelper/DesUtil 日志链路无容器化回归测试（DEF-1 缺陷防线）。
 *
 * <p>DEF-1：logback 脱敏过滤器在任意线程的日志事件上曾调 SpringUtil.getBean 取 DesHelper 与
 * 配置——上下文刷新期间 main 线程持单例锁创建 bean（redisson 连接回调打日志即触发），getBean
 * 阻塞在单例锁上形成三方循环等待，JVM 永久挂起。
 * 修复后日志路径仅消费 init()/ContextRefreshedEvent 提升的静态引用，对容器零依赖。
 *
 * <p>本类以"无任何可用 Spring 容器"为前置（SpringUtil 静态上下文置 null），锁定四条契约：
 * 未就绪 fail-fast、JSON 报文参数脱敏零容器可用、实例未发布时对象参数 fail-open 跳过、
 * 实例发布后对象参数走静态实例处理且同样零容器。
 */
class DesHelperLogPathNoContainerTest {

    /** 最小规则：LV0 手机号脱敏（与 DesensitizedLogFilterTest 同款，通路一致） */
    private static JevokitDesensitizationProperties minimalProperties() {
        JevokitDesensitizationProperties properties = new JevokitDesensitizationProperties();
        properties.setPropPattern(Collections.singletonMap(
                DstLevel.LV0, Collections.singletonMap(DstType.MOBILE, Collections.singletonList(".*mobile"))));
        properties.setIgnorePattern(Collections.emptyList());
        properties.setLevelPowerKey(Collections.singletonMap(DstLevel.LV0, Collections.singletonList("DST_POWER_LV0")));
        return properties;
    }

    /** 普通复杂对象日志参数（走 desensitizeLogAfterClone 对象分支——DEF-1 死锁触发分支） */
    private static class ComplexLogArg implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String mobile = "15818222222";
    }

    private Object previousSpringUtilContext;

    @BeforeEach
    void recordAndClearStaticState() throws Exception {
        // SM4 密钥静态灌入（等价 minimalRunner 的 cryptoKeyInitializer，但不依赖任何容器）
        SecurityUtil.init(null, null, null, DesensitizationTestSupport.TEST_SM4_KEY);
        previousSpringUtilContext = readSpringUtilContext();
        resetStaticState();
    }

    @AfterEach
    void restoreStaticState() throws Exception {
        resetStaticState();
        writeSpringUtilContext(previousSpringUtilContext);
    }

    @Test
    @DisplayName("未就绪：desensitizeLog 快速失败（防御语义保留，绝不静默阻塞）")
    void notReadyFailsFast() {
        assertThatThrownBy(() -> DesHelper.desensitizeLog(new ComplexLogArg()))
                .as("ready=false 时应快速失败而非进入容器查找")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desHelper not ready");
    }

    @Test
    @DisplayName("就绪但无容器：JSON 报文参数脱敏经静态配置完成，对象参数 fail-open 原样返回（DEF-1 回归）")
    void logPathWorksWithoutSpringContainer() {
        new DesHelper(minimalProperties(), new DefaultSensitizationPowerInterceptor()).init();
        // 模拟"容器不可用"环境：SpringUtil 静态上下文置 null——修复前此形态下日志路径的
        // SpringUtil.getBean 直接抛 UtilException/NoSuchBeanDefinition（或持锁场景下死锁）
        writeSpringUtilContext(null);

        // JSON 字符串参数：走 DesUtil（配置取静态引用），全链路零容器仍生效
        String masked = (String) DesHelper.desensitizeLog("{\"mobile\":\"15818222222\"}");
        assertThat(masked).as("JSON 报文参数脱敏应零容器可用").doesNotContain("15818222222");

        // 复杂对象参数：实例未发布（无容器不触发 ContextRefreshedEvent）→ fail-open 原样返回，绝不抛异常
        Object out = DesHelper.desensitizeLog(new ComplexLogArg());
        assertThat(out).as("实例未发布时对象参数应 fail-open 跳过脱敏且不抛容器异常").isNotNull();
    }

    @Test
    @DisplayName("实例发布后：对象参数经静态实例处理，同样零容器依赖")
    void objectArgUsesPublishedInstanceWithoutContainer() throws Exception {
        DesHelper helper = new DesHelper(minimalProperties(), new DefaultSensitizationPowerInterceptor());
        helper.init();
        // 模拟 ContextRefreshedEvent 发布效果（生产经容器 getBean 得代理实例；单测无容器，直填裸实例验证零容器通路）
        setStaticField("staticInstance", helper);
        writeSpringUtilContext(null);

        Object out = DesHelper.desensitizeLog(new ComplexLogArg());

        assertThat(out).as("实例发布后对象参数应走静态实例处理且不抛容器异常").isNotNull();
    }

    private static void resetStaticState() throws Exception {
        setStaticField("ready", false);
        setStaticField("staticInstance", null);
        setStaticField("staticProperties", null);
        setStaticField("staticSensitizationPowerInterceptor", null);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = DesHelper.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Object readSpringUtilContext() throws Exception {
        Field field = SpringUtil.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        return field.get(null);
    }

    private static void writeSpringUtilContext(Object value) {
        try {
            Field field = SpringUtil.class.getDeclaredField("applicationContext");
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("操作 SpringUtil.applicationContext 失败", e);
        }
    }
}
