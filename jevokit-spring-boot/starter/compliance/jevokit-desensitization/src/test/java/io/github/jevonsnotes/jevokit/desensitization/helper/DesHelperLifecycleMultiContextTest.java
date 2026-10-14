package io.github.jevonsnotes.jevokit.desensitization.helper;

import io.github.jevonsnotes.jevokit.desensitization.config.JevokitDesensitizationProperties;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.SensitizationPowerInterceptor;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.impl.DefaultSensitizationPowerInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DesHelper 静态引用生命周期回归测试（ContextClosedEvent 清理配条款）。
 *
 * <p>DEF-1 修复把日志链路改为消费 init()（@PostConstruct）/ContextRefreshedEvent 提升的静态引用，
 * 但发布与清理须成对：spring-test 多上下文、热重启场景下，旧上下文的静态引用在下一上下文刷新
 * 完成前若仍呈"就绪"态（陈旧配置、已销毁实例——DEF-1 刷新窗口的放大器）会被误用。修复后
 * ContextClosedEvent 清理全部静态引用，形成"刷新发布 → 关闭清理"的生命周期闭环。
 *
 * <p>形态参照 DesHelperLogPathNoContainerTest：静态状态经反射读写、前后置复位隔离；
 * 双 AnnotationConfigApplicationContext（无 Spring Boot 环境）驱动真实的刷新/关闭事件序列，
 * 断言"第二个上下文刷新后 ready 重新置位、关闭后清理"。
 */
class DesHelperLifecycleMultiContextTest {

    /** 最小装配：properties + 权限拦截器 + DesHelper（init 触发静态提升，刷新/关闭触发发布与清理） */
    @Configuration
    static class DesHelperConfig {
        @Bean
        JevokitDesensitizationProperties properties() {
            JevokitDesensitizationProperties properties = new JevokitDesensitizationProperties();
            properties.setPropPattern(Collections.singletonMap(
                    DstLevel.LV0, Collections.singletonMap(DstType.MOBILE, Collections.singletonList(".*mobile"))));
            properties.setIgnorePattern(Collections.emptyList());
            properties.setLevelPowerKey(Collections.singletonMap(DstLevel.LV0, Collections.singletonList("DST_POWER_LV0")));
            return properties;
        }

        @Bean
        SensitizationPowerInterceptor interceptor() {
            return new DefaultSensitizationPowerInterceptor();
        }

        @Bean
        DesHelper desHelper(JevokitDesensitizationProperties properties, SensitizationPowerInterceptor interceptor) {
            return new DesHelper(properties, interceptor);
        }
    }

    @BeforeEach
    @AfterEach
    void resetStaticState() throws Exception {
        setStaticField("ready", false);
        setStaticField("staticInstance", null);
        setStaticField("staticProperties", null);
        setStaticField("staticSensitizationPowerInterceptor", null);
    }

    @Test
    @DisplayName("多上下文：第二上下文刷新后 ready 重新置位并覆盖引用，关闭后全部清理（关闭清理回归）")
    void secondContextRefreshRepublishesAndCloseCleansUp() {
        AnnotationConfigApplicationContext contextA = new AnnotationConfigApplicationContext(DesHelperConfig.class);
        try {
            assertThat(readStatic("ready")).as("第一上下文刷新后 ready 应置位").isEqualTo(true);
            assertThat(readStatic("staticInstance"))
                    .as("第一上下文刷新后实例引用应经 ContextRefreshedEvent 发布")
                    .isSameAs(contextA.getBean(DesHelper.class));
            assertThat(readStatic("staticProperties"))
                    .as("第一上下文刷新后配置引用应为 init() 提升的实例")
                    .isSameAs(contextA.getBean(JevokitDesensitizationProperties.class));

            AnnotationConfigApplicationContext contextB = new AnnotationConfigApplicationContext(DesHelperConfig.class);
            try {
                assertThat(readStatic("ready")).as("第二上下文刷新后 ready 应重新置位").isEqualTo(true);
                assertThat(readStatic("staticInstance"))
                        .as("第二上下文刷新后实例引用应重新发布（覆盖第一上下文，刷新发布语义不变）")
                        .isSameAs(contextB.getBean(DesHelper.class))
                        .isNotSameAs(contextA.getBean(DesHelper.class));
                assertThat(readStatic("staticProperties"))
                        .as("配置引用应切换到第二上下文")
                        .isSameAs(contextB.getBean(JevokitDesensitizationProperties.class));
            } finally {
                // ContextClosedEvent → onContextClosed 清理静态引用
                contextB.close();
            }

            assertClearedAfterClose("第二上下文关闭后");

            // 清理后入口回到既有防御语义：fail-fast（not ready），绝不进入容器查找
            assertThatThrownBy(() -> DesHelper.desensitizeLog(new Object()))
                    .as("关闭清理后 desensitizeLog 应 fail-fast 而非静默走陈旧引用")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("desHelper not ready");
        } finally {
            contextA.close();
        }
    }

    @Test
    @DisplayName("关闭清理后：checkPower 安全默认 false（清理竞态窗口不得 NPE）")
    void afterClosePowerCheckDefaultsToSafeFalse() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(DesHelperConfig.class)) {
            assertThat(readStatic("ready")).as("刷新后 ready 应置位").isEqualTo(true);
        }

        assertClearedAfterClose("上下文关闭后");

        // ready 门闩已落 false：走未就绪分支返回 false；若读方落在清理竞态窗口内
        // （读到陈旧 ready=true），引用判空兜底同样返回安全默认 false——绝不 NPE
        assertThat(DesHelper.checkPower(DstLevel.LV0))
                .as("关闭清理后权限判定应返回安全默认（无权限）")
                .isFalse();
    }

    // ------------------------------------------------------------------
    // helpers（反射读写静态状态，形态与 DesHelperLogPathNoContainerTest 一致）
    // ------------------------------------------------------------------

    private static void assertClearedAfterClose(String phase) {
        assertThat(readStatic("ready")).as(phase + " ready 应清理").isEqualTo(false);
        assertThat(readStatic("staticInstance")).as(phase + " 实例引用应清理").isNull();
        assertThat(readStatic("staticProperties")).as(phase + " 配置引用应清理").isNull();
        assertThat(readStatic("staticSensitizationPowerInterceptor"))
                .as(phase + " 权限拦截器引用应清理").isNull();
    }

    private static Object readStatic(String name) {
        try {
            Field field = DesHelper.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取 DesHelper 静态字段失败：" + name, e);
        }
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = DesHelper.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
