package io.github.jevonsnotes.jevokit.sample.e2e;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import io.github.jevonsnotes.jevokit.sample.JevokitSampleApplication;
import io.github.jevonsnotes.jevokit.sample.vo.TopicVo;

/**
 * rocketmq 模块 E2E（原 MockTest.testRocketMq 拆分，异步投递 + 监听消费验证）。
 *
 * <p>环境要求（@Tag("mq")）：默认构建不跑（surefire excludedGroups=mq）——本类是唯一默认排除项。
 * <b>本机无 RocketMQ 时该类必然连接失败（预期行为）</b>——需要 9876 nameserver +
 * broker 在位，执行：mvn test -DexcludedGroups=（全量跑需真 MQ 装配：先移除主
 * application.yml 中 spring.config.import 的 application-ignore-mq.yml 行，否则
 * RocketMQ 自动装配被默认排除，生产者 bean 不会注册）。
 */
@Slf4j
@Tag("mq")
@DisplayName("rocketmq 端到端")
@SpringBootTest(classes = JevokitSampleApplication.class)
@AutoConfigureMockMvc
class RocketMqE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("POST /rocketmq/test：同步投递成功返回 success")
    void rocketMqSyncSendSucceeds() throws Exception {
        // given
        TopicVo topicVo = new TopicVo();
        topicVo.setType(1);
        topicVo.setAsync(false);
        topicVo.setSleepTime(1000);
        topicVo.setTransactionRollback(false);

        // when / then
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/rocketmq/test")
                        .content(JSON.toJSONString(topicVo))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("success"))
                .andDo(MockMvcResultHandlers.print());
    }
}
