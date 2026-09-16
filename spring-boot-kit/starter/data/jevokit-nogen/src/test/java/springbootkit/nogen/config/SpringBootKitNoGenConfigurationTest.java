package springbootkit.nogen.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import springbootkit.nogen.api.SpringBootKitNoGenApi;
import springbootkit.nogen.dao.NoGenDao;
import springbootkit.nogen.helper.BzNoHelper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * nogen 自动装配条件单测（B 档测试重构 #5）。
 * 以 ApplicationContextRunner + H2 内存库验证：NoGenDao 按 jdbc url 路由到 H2SQLDao、
 * 全套 bean（properties/dao/helper/autoInitDB/api/webConfig）成套装配。
 * RedissonClient 由 mock 提供（BzNoHelper 只透传引用，无真实连接）。
 */
class SpringBootKitNoGenConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JdbcTemplateAutoConfiguration.class, SpringBootKitNoGenConfiguration.class))
            .withBean("dataSource", SimpleDriverDataSource.class, () -> {
                SimpleDriverDataSource ds = new SimpleDriverDataSource();
                ds.setDriver(new org.h2.Driver());
                ds.setUrl("jdbc:h2:mem:nogentest;DB_CLOSE_DELAY=-1");
                ds.setUsername("sa");
                ds.setPassword("");
                return ds;
            })
            .withBean("redissonClient", org.redisson.api.RedissonClient.class,
                    () -> org.mockito.Mockito.mock(org.redisson.api.RedissonClient.class));

    @Test
    @DisplayName("H2 数据源下全套 nogen bean 成套装配且 dao 为 H2 实现")
    void wiresAllNoGenBeansWithH2Dao() {
        runner.run(context -> {
            assertThat(context).as("上下文应正常启动").hasNotFailed();
            assertThat(context).as("NoGenDao 应存在").hasSingleBean(NoGenDao.class);
            assertThat(context.getBean(NoGenDao.class))
                    .as("jdbc:h2 url 应路由到 H2SQLDao")
                    .isInstanceOf(springbootkit.nogen.dao.H2SQLDao.class);
            assertThat(context).as("属性 bean 应存在").hasSingleBean(SpringBootKitNoGenProperties.class);
            assertThat(context).as("BzNoHelper 应存在").hasSingleBean(BzNoHelper.class);
            assertThat(context).as("自动建表初始化器应存在")
                    .hasSingleBean(SpringBootKitNoGenAutoInitDB.class);
            assertThat(context).as("对外 API bean 应存在").hasSingleBean(SpringBootKitNoGenApi.class);
            assertThat(context).as("web 配置 bean 应存在")
                    .hasSingleBean(SpringBootKitNoGenWebConfiguration.class);
            // 路由验证附带证据：JdbcTemplate 就绪（nogen 依赖其初始化）
            assertThat(context).as("JdbcTemplate 应存在").hasSingleBean(JdbcTemplate.class);
        });
    }
}
