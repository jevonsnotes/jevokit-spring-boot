package io.github.jevonsnotes.jevokit.sample.e2e;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import io.github.jevonsnotes.jevokit.sample.JevokitSampleApplication;
import io.github.jevonsnotes.jevokit.sample.vo.TestVo;

/**
 * desensitization 模块 E2E（原 MockTest.testDesensitization 拆分）。
 *
 * <p>默认测试集（无需 MQ）：内建 @ActiveProfiles("local") 排除 RocketMQ 自动装配，环境仅需 Redis 在位 + H2 内存自给。
 *
 */
@Slf4j
@ActiveProfiles("local")
@DisplayName("desensitization 端到端")
@SpringBootTest(classes = JevokitSampleApplication.class)
@AutoConfigureMockMvc
@Transactional
class DesensitizationE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("POST /desensitization/test：响应中姓名字段被脱敏（含 *）")
    void desensitizationEndpointMasksName() throws Exception {
        // given
        TestVo mock = Mockito.mock(TestVo.class);
        Mockito.when(mock.getName()).thenReturn("张三丰");

        // when / then：响应的 name 应含脱敏占位符
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/desensitization/test")
                        .content(JSON.toJSONString(mock))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value(
                        org.hamcrest.Matchers.containsString("*")))
                .andDo(MockMvcResultHandlers.print());
    }
}
