package io.github.jevonsnotes.jevokit.okhttp.util;

import io.github.jevonsnotes.jevokit.okhttp.config.JevokitOkhttpConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OkHttpUtil 静态门面生命周期回归测试（遗留 #1：of() 家族按 DEF-1 定式两阶段发布 + 关闭清理）。
 *
 * <p>原 of() 家族每次调用 SpringUtil.getBean——门面为静态入口，存在业务 bean 初始化期/
 * 异步初始化线程在上下文刷新窗口内触达的理论路径（与日志链路同型的低危等待环）。
 * 修复后 ContextRefreshedEvent 把门面单例提升为静态引用、ContextClosedEvent 清理
 * （与 DesHelper 同定式），刷新完成的常态路径零容器查找；未发布窗口回落 getBean 原语义。
 *
 * <p>关键证据设计：runner 刻意不注册 SpringUtil（hutool 静态上下文不可用）——修复前
 * {@code of()} 直接抛 UtilException；修复后刷新事件发布的静态引用使 of() 在"无容器查找通道"
 * 环境下照常返回容器单例。关闭后静态引用清理归零，多上下文按"最后刷新者胜出"重新发布。
 */
class OkHttpUtilStaticFacadeLifecycleTest {

    /** 不注册 SpringUtil：静态引用是 of() 唯一可用通道，回归证据才成立 */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RefreshAutoConfiguration.class, JevokitOkhttpConfiguration.class));

    @BeforeEach
    @AfterEach
    void resetStaticState() throws Exception {
        setStaticInstance(null);
    }

    @Test
    @DisplayName("刷新发布：of() 消费静态引用返回容器单例（零容器查找，SpringUtil 未注册仍可用）")
    void ofReturnsPublishedInstanceWithoutSpringUtil() {
        runner.run(context -> {
            assertThat(context).as("上下文应正常启动").hasNotFailed();
            OkHttpUtil bean = context.getBean(OkHttpUtil.class);

            assertThat(OkHttpUtil.of())
                    .as("刷新后 of() 应返回静态发布的容器单例（本 runner 未注册 SpringUtil，修复前此形态直接抛异常）")
                    .isSameAs(bean);
        });
        // runner 消费完即关闭上下文：ContextClosedEvent 清理静态引用（关闭前 finally 已兜底复位，
        // 此断言验证的是真实关闭事件路径）
        assertThat(readStaticInstance()).as("上下文关闭后静态引用应清理").isNull();
    }

    @Test
    @DisplayName("多上下文：后刷新者重新发布，of() 切换到新上下文单例；关闭清理不残留")
    void secondContextRepublishesFacade() {
        OkHttpUtil[] firstFacade = new OkHttpUtil[1];
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            firstFacade[0] = context.getBean(OkHttpUtil.class);
            assertThat(OkHttpUtil.of()).as("第一上下文刷新后 of() 应返回其门面单例").isSameAs(firstFacade[0]);
        });

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            OkHttpUtil second = context.getBean(OkHttpUtil.class);
            assertThat(OkHttpUtil.of())
                    .as("第二上下文刷新后 of() 应重新发布并切换到新单例（不残留第一上下文引用）")
                    .isSameAs(second)
                    .isNotSameAs(firstFacade[0]);
        });

        assertThat(readStaticInstance()).as("全部上下文关闭后静态引用不应残留").isNull();
    }

    // ------------------------------------------------------------------
    // helpers（反射读写静态引用，静态状态与测试容器生命周期解耦）
    // ------------------------------------------------------------------

    private static Object readStaticInstance() {
        try {
            Field field = OkHttpUtil.class.getDeclaredField("staticInstance");
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取 OkHttpUtil.staticInstance 失败", e);
        }
    }

    private static void setStaticInstance(Object value) throws Exception {
        Field field = OkHttpUtil.class.getDeclaredField("staticInstance");
        field.setAccessible(true);
        field.set(null, value);
    }
}
