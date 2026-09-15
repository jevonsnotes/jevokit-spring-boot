package springbootkit.sample.e2e;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import springbootkit.sample.SpringBootKitSampleApplication;
import springbootkit.sample.service.RedisService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * redis 模块 E2E（原 MockTest.testRedis 拆分）。
 *
 * <p>环境要求（@Tag("env")）：默认构建不跑（surefire excludedGroups=env），
 * 需要真实 Redis。执行：mvn test -Dgroups=env -DexcludedGroups=
 * 二次调用命中缓存返回同值，验证 kit 缓存键前缀与 Spring Cache 装配。
 */
@Slf4j
@Tag("env")
@DisplayName("redis 端到端")
@SpringBootTest(classes = SpringBootKitSampleApplication.class)
class RedisE2ETest {

    @Autowired
    RedisService redisService;

    @Test
    @DisplayName("同一 key 二次读取命中缓存：两次 userCache() 结果一致")
    void redisCacheSecondReadHits() {
        // when
        String first = redisService.userCache();
        String second = redisService.userCache();
        // then
        assertThat(first).as("首次读取应有缓存值").isNotBlank();
        assertThat(second).as("二次读取应命中缓存返回同值").isEqualTo(first);
    }
}
