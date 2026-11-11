package io.github.jevonsnotes.jevokit.desensitization.helper;

import io.github.jevonsnotes.jevokit.desensitization.config.JevokitDesensitizationProperties;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.SensitizationPowerInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * desHelper 解环回归测试（构造注入业务拦截器导致的容器环）。
 *
 * <p>场景复刻：业务自定义 SensitizationPowerInterceptor 常注入带 Feign 的业务 service，
 * 而 Feign 侧配置/解码又用 desHelper——旧实现 desHelper 的 bean 方法<b>构造注入</b>拦截器，
 * 三边闭合即成容器环（desHelper → 业务拦截器 → 业务 service → desHelper），应用启动失败。
 *
 * <p>修复：拦截器改为 ObjectProvider 持有、ContextRefreshedEvent（单例锁已释放）时懒解析发布——
 * desHelper 构造期只依赖自身配置，环从 kit 侧断开；业务链无论怎么绕都构不成环。
 *
 * <p>断言三层：① 上下文正常启动（环存在时此处即 BeanCurrentlyInCreationException）；
 * ② 业务拦截器仍生效（checkPower 经事件发布的拦截器判定，业务实现返回 true）；
 * ③ 业务 service 拿到的 desHelper 可用（脱敏能力未因延迟解析受损）。
 */
@SpringBootTest(classes = DesHelperCycleBreakTest.CycleConfig.class,
        properties = "jevokit.crypto.startup-check=false")  // 本测试只关注脱敏装配，core 密钥校验关闭
@DisplayName("desHelper 解环：业务拦截器→业务 service→desHelper 闭环形态可正常启动")
class DesHelperCycleBreakTest {

    /** 业务拦截器实现被调用的标记（证明延迟解析发布的拦截器真正参与了 checkPower）。 */
    private static final AtomicBoolean BIZ_INTERCEPTOR_INVOKED = new AtomicBoolean(false);

    /**
     * 环形态装配：bizInterceptor（实现 SensitizationPowerInterceptor，覆盖缺省）依赖
     * bizService；bizService 依赖 desHelper（模拟 Feign 配置用脱敏）——经 kit 装配的
     * desHelper bean 闭环。
     */
    @Configuration
    @EnableAutoConfiguration   // 走 AutoConfiguration.imports 装配 kit 配置（自动配置后评估，缺省拦截器正确让位业务实现）
    static class CycleConfig {

        @Bean
        JevokitDesensitizationProperties properties() {
            JevokitDesensitizationProperties properties = new JevokitDesensitizationProperties();
            properties.setPropPattern(Collections.singletonMap(
                    DstLevel.LV0, Collections.singletonMap(DstType.MOBILE, Collections.singletonList(".*mobile"))));
            properties.setIgnorePattern(Collections.emptyList());
            properties.setLevelPowerKey(Collections.singletonMap(DstLevel.LV0, Collections.singletonList("DST_POWER_LV0")));
            return properties;
        }

        /** 业务 service：依赖 desHelper（Feign 配置/解码用脱敏的形态）。 */
        @Bean
        BizService bizService(DesHelper desHelper) {
            return new BizService(desHelper);
        }

        /** 业务拦截器：实现 kit 接口（覆盖 @ConditionalOnMissingBean 缺省），依赖业务 service。 */
        @Bean
        SensitizationPowerInterceptor bizInterceptor(BizService bizService) {
            return new BizInterceptor(bizService);
        }
    }

    /** 模拟 Feign 侧业务 service（依赖 desHelper）。 */
    static class BizService {
        final DesHelper desHelper;

        BizService(DesHelper desHelper) {
            this.desHelper = desHelper;
        }
    }

    /** 模拟业务权限拦截器（依赖业务 service，形成环的第三边）。 */
    static class BizInterceptor implements SensitizationPowerInterceptor {
        final BizService bizService;

        BizInterceptor(BizService bizService) {
            this.bizService = bizService;
        }

        @Override
        public boolean check(DstLevel level, java.util.List<String> powerKeys) {
            BIZ_INTERCEPTOR_INVOKED.set(true);
            return true;
        }
    }

    @Autowired
    BizService bizService;

    @AfterEach   // 不做 BeforeEach 清理：上下文刷新（事件发布拦截器）发生在注入之前，事前清空会误杀本用例的发布结果
    void resetStaticState() throws Exception {
        // 静态状态复位隔离（本用例经真实刷新事件发布，结束后清干净防影响他例）
        Field ready = DesHelper.class.getDeclaredField("ready");
        ready.setAccessible(true);
        ready.setBoolean(null, false);
        Field props = DesHelper.class.getDeclaredField("staticProperties");
        props.setAccessible(true);
        props.set(null, null);
        Field interceptor = DesHelper.class.getDeclaredField("staticSensitizationPowerInterceptor");
        interceptor.setAccessible(true);
        interceptor.set(null, null);
        Field instance = DesHelper.class.getDeclaredField("staticInstance");
        instance.setAccessible(true);
        instance.set(null, null);
        BIZ_INTERCEPTOR_INVOKED.set(false);
    }

    @Test
    @DisplayName("环形态启动成功 + 业务拦截器经事件发布生效 + service 侧 desHelper 可用")
    void cycleFormedApplicationContextStartsAndInterceptorWorks() {
        // ① 走到这里即上下文启动成功（旧实现此形态抛 BeanCurrentlyInCreationException）
        assertThat(bizService).isNotNull();
        assertThat(bizService.desHelper).isNotNull();
        // ② checkPower 经 ContextRefreshedEvent 懒解析发布的业务拦截器判定
        boolean allowed = DesHelper.checkPower(DstLevel.LV0);
        assertThat(BIZ_INTERCEPTOR_INVOKED).as("业务拦截器应被延迟解析并参与判定").isTrue();
        assertThat(allowed).as("业务拦截器返回 true 应放行").isTrue();
    }
}
