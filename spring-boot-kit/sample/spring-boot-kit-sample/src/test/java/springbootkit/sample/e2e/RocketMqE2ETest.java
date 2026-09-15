package springbootkit.sample.e2e;

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
import springbootkit.sample.SpringBootKitSampleApplication;
import springbootkit.sample.vo.TopicVo;

/**
 * rocketmq 模块 E2E（原 MockTest.testRocketMq 拆分，异步投递 + 监听消费验证）。
 *
 * <p>环境要求（@Tag("env")）：默认构建不跑（surefire excludedGroups=env）。
 * <b>本机无 RocketMQ 时该类必然连接失败（预期行为）</b>——需要 9876 nameserver +
 * broker 在位，执行：mvn test -Dgroups=env -DexcludedGroups=（不要激活 local profile，
 * local profile 排除了 RocketMQ 自动装配，生产者 bean 不会注册）。
 */
@Slf4j
@Tag("env")
@DisplayName("rocketmq 端到端")
@SpringBootTest(classes = SpringBootKitSampleApplication.class)
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
