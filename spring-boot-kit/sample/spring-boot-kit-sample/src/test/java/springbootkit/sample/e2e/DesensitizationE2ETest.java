package springbootkit.sample.e2e;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import springbootkit.sample.SpringBootKitSampleApplication;
import springbootkit.sample.vo.TestVo;

/**
 * desensitization 模块 E2E（原 MockTest.testDesensitization 拆分）。
 *
 * <p>环境要求（@Tag("env")）：默认构建不跑（surefire excludedGroups=env），
 * 需要真实 Redis + H2 上下文。执行：mvn test -Dgroups=env -DexcludedGroups=
 */
@Slf4j
@Tag("env")
@DisplayName("desensitization 端到端")
@SpringBootTest(classes = SpringBootKitSampleApplication.class)
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
