package springbootkit.sample;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import springbootkit.redis.token.strategy.DefaultTokenStrategy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * sample 应用上下文冒烟测试（无 MQ 机器默认可跑）。
 *
 * <p>激活 local profile 排除 RocketMQ 自动装配（application-local.yml），
 * 上下文仅需 Redis（本机 6379）+ H2 内存库。原 JUnit4 版本零断言且
 * testMain 依赖桌面文件 D:\Desktop\报文.txt（异常吞掉假绿）——重写为
 * 真断言：上下文活跃 + 关键 bean 就位 + Redis/DB 可实际交互。
 */
@DisplayName("sample 上下文冒烟（local profile，无 MQ 可跑）")
@SpringBootTest(classes = SpringBootKitSampleApplication.class)
@ActiveProfiles("local")
class ApplicationTest {

    @Autowired
    ConfigurableApplicationContext applicationContext;
    @Autowired
    RedissonClient redissonClient;
    @Autowired
    DefaultTokenStrategy defaultTokenStrategy;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("上下文活跃且 kit 关键 bean（Redis/token 策略/数据源）全部就位")
    void contextLoadsWithCoreBeans() {
        assertThat(applicationContext.isActive()).as("应用上下文应处于活跃状态").isTrue();
        assertThat(redissonClient).as("RedissonClient 应已装配").isNotNull();
        assertThat(defaultTokenStrategy).as("DefaultTokenStrategy 应已装配").isNotNull();
    }

    @Test
    @DisplayName("token 策略可实际工作：取号非空且刷新返回新值（Redis 真实连通）")
    void tokenStrategyRoundTrip() {
        String token = defaultTokenStrategy.getTokenDelegate();
        String refreshed = defaultTokenStrategy.refreshTokenDelegate();
        assertThat(token).as("getTokenDelegate 应返回非空 token").isNotBlank();
        assertThat(refreshed).as("refreshTokenDelegate 应返回非空新 token").isNotBlank();
    }

    @Test
    @DisplayName("H2 数据源可交互：select 1 生效")
    void dataSourceResponsive() {
        Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
        assertThat(one).as("H2 select 1 应返回 1").isEqualTo(1);
    }
}
