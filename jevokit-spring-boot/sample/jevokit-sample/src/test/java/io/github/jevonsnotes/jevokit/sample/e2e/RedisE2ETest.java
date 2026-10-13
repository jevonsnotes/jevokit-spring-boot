package io.github.jevonsnotes.jevokit.sample.e2e;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import io.github.jevonsnotes.jevokit.sample.JevokitSampleApplication;
import io.github.jevonsnotes.jevokit.sample.service.RedisService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * redis 模块 E2E（原 MockTest.testRedis 拆分）。
 *
 * <p>默认测试集（无需 MQ）：MQ 排除由主 application.yml 默认 import 的 application-ignore-mq.yml 承担
 * （默认排除 RocketMQ 自动装配，无 MQ 环境可启动与测试），环境仅需 Redis 在位 + H2 内存自给。
 *
 * 二次调用命中缓存返回同值，验证 kit 缓存键前缀与 Spring Cache 装配。
 *
 * <p>@AutoConfigureMockMvc 说明：本类不使用 MockMvc，加它以与其余 e2e 类共享同一
 * TestContext 缓存键——实测同 fork 双上下文（双 Redisson 实例）第二实例握手失败并
 * 在关闭路径死锁（连接风暴 144 条，干净 Redis 亦复现），统一键后全套件单上下文。
 */
@Slf4j
@ActiveProfiles("local")
@DisplayName("redis 端到端")
@SpringBootTest(classes = JevokitSampleApplication.class)
@AutoConfigureMockMvc
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
