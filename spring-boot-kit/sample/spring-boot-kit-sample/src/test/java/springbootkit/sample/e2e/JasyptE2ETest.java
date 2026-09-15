package springbootkit.sample.e2e;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import springbootkit.sample.SpringBootKitSampleApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * jasypt 模块 E2E（原 MockTest.testJasypt 拆分）。
 *
 * <p>环境要求（@Tag("env")）：默认构建不跑（surefire excludedGroups=env），
 * 需要真实 Redis + H2 上下文。执行：mvn test -Dgroups=env -DexcludedGroups=
 * 密文 ENC(...) 与 application.yml 的演示密钥 DemoJasyptKey123456 配套。
 */
@Slf4j
@Tag("env")
@DisplayName("jasypt 端到端")
@SpringBootTest(classes = SpringBootKitSampleApplication.class)
@AutoConfigureMockMvc
class JasyptE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("POST /jasypt/test：演示密钥解密 ENC 密文还原 demo-password")
    void jasyptDecryptDemoCipher() throws Exception {
        // when / then
        String response = mockMvc.perform(MockMvcRequestBuilders
                        .post("/jasypt/test")
                        .queryParam("encryptVal", "ENC(Zvnlzd4m/DHE+WEQq4ROTT+EePp45eQ0)"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("demo-password"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(response).isEqualTo("demo-password");
    }
}
