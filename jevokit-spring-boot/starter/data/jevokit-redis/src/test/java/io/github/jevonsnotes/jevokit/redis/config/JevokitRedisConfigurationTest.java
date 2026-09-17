package io.github.jevonsnotes.jevokit.redis.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.redisson.api.RedissonClient;
import io.github.jevonsnotes.jevokit.redis.cache.GlobalCacheKeyPrefix;
import io.github.jevonsnotes.jevokit.redis.lock.aspect.LockAspect;
import io.github.jevonsnotes.jevokit.redis.token.delegate.TokenDelegate;
import io.github.jevonsnotes.jevokit.redis.token.strategy.DefaultTokenStrategy;
import io.github.jevonsnotes.jevokit.redis.token.strategy.TokenStrategy;
import io.github.jevonsnotes.jevokit.redis.util.RedisLock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * redis 自动装配条件单测（B 档测试重构 #5）。
 * 仅验证条件装配与 bean 存在性：RedisConnectionFactory/RedissonClient 以 mock 提供，
 * 不建立任何真实连接（RedisTemplate/Redisson 均为惰性连接）。
 */
class JevokitRedisConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JevokitRedisConfiguration.class))
            .withBean(RedisConnectionFactory.class, () -> org.mockito.Mockito.mock(RedisConnectionFactory.class))
            .withBean(RedissonClient.class, () -> org.mockito.Mockito.mock(RedissonClient.class))
            .withBean(StringRedisTemplate.class, () -> {
                StringRedisTemplate template = new StringRedisTemplate();
                template.setConnectionFactory(org.mockito.Mockito.mock(RedisConnectionFactory.class));
                return template;
            });

    @Test
    @DisplayName("redis 模块全套 bean 成套装配（无真实连接）")
    void wiresAllRedisBeansWithoutRealConnection() {
        runner.run(context -> {
            assertThat(context).as("上下文应正常启动").hasNotFailed();

            // StringRedisTemplate 是 RedisTemplate 子类，按 bean 名断言 kit 的 redisTemplate
            assertThat(context).as("RedisTemplate 应装配").hasBean("redisTemplate");
            assertThat(context).as("StringRedisTemplate 应装配").hasSingleBean(StringRedisTemplate.class);
            assertThat(context).as("全局缓存 key 前缀应装配").hasSingleBean(GlobalCacheKeyPrefix.class);
            assertThat(context).as("redisson 缓存配置应装配").hasSingleBean(RedisCacheConfiguration.class);
            assertThat(context).as("分布式锁组件应装配").hasSingleBean(RedisLock.class);
            assertThat(context).as("锁切面应装配").hasSingleBean(LockAspect.class);
            assertThat(context).as("token 代理应装配").hasSingleBean(TokenDelegate.class);
            assertThat(context).as("默认 token 策略应装配").hasSingleBean(DefaultTokenStrategy.class);
            assertThat(context).as("redisson 定制器应装配")
                    .hasSingleBean(org.redisson.spring.starter.RedissonAutoConfigurationCustomizer.class);

            // CacheRedisConfig（嵌套配置）的 ops 门面 bean
            assertThat(context).as("ValueOperations 门面应装配").hasSingleBean(ValueOperations.class);
            assertThat(context).as("HashOperations 门面应装配")
                    .hasSingleBean(org.springframework.data.redis.core.HashOperations.class);

            // 三个缓存 key 生成器
            assertThat(context).as("全局 key 生成器应装配")
                    .hasSingleBean(io.github.jevonsnotes.jevokit.redis.cache.generator.extend.GlobalKeyGenerator.class);
            assertThat(context).as("用户级 key 生成器应装配")
                    .hasSingleBean(io.github.jevonsnotes.jevokit.redis.cache.generator.extend.UserKeyGenerator.class);
        });
    }

    @Test
    @DisplayName("tokenDelegate 支持用户覆盖（@ConditionalOnMissingBean）")
    void tokenDelegateIsOverridable() {
        runner.withBean(TokenDelegate.class, () -> org.mockito.Mockito.mock(TokenDelegate.class))
                .run(context -> {
                    assertThat(context).as("用户自定义 TokenDelegate 应生效（kit 默认让位）")
                            .hasSingleBean(TokenDelegate.class);
                });
    }

    @Test
    @DisplayName("类初始化顺序验收：无 Spring 环境强制加载 TokenStrategy 类不抛（终验裁定修复）")
    void tokenStrategyClassInitBeforeSpringUtilDoesNotExplode() throws Exception {
        // 原 static delegate = SpringUtil.getBean(...) 在类初始化期执行：
        // SpringUtil 未就绪时任何实现类加载即抛 UtilException（类加载顺序依赖）。
        // 修复后为调用期惰性查找——此处无任何 Spring 环境，Class.forName(initialize=true)
        // 强制执行 <clinit>，若仍依赖 SpringUtil 会直接抛 ExceptionInInitializerError 使本用例失败。
        // 装配期证据：本类主 runner 已不再注册 SpringUtil，wiresAllRedisBeansWithoutRealConnection 仍绿
        Class.forName(TokenStrategy.class.getName(), true, TokenStrategy.class.getClassLoader());
    }
}
