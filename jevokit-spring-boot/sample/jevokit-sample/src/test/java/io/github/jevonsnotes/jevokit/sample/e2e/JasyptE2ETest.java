package io.github.jevonsnotes.jevokit.sample.e2e;

import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import io.github.jevonsnotes.jevokit.core.consts.JasyptConst;
import io.github.jevonsnotes.jevokit.sample.JevokitSampleApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * jasypt 模块 E2E（原 MockTest.testJasypt 拆分）。
 *
 * <p>默认测试集（无需 MQ）：MQ 排除由主 application.yml 默认 import 的 application-ignore-mq.yml 承担
 * （默认排除 RocketMQ 自动装配，无 MQ 环境可启动与测试），环境仅需 Redis 在位 + H2 内存自给。
 *
 * <p>密文自产自销：用例内经真实 encryptor bean 加密明文后走 HTTP 解密端点往返。
 * 曾硬编码旧 MockTest 时代的 ENC(...) 密文——该密文与当前演示密钥/算法配置不配套
 * （EncryptionOperationNotPossibleException），且此类此前 env-tagged 从未在无 MQ 机器执行而未被察觉；
 * 自产密文使用例对密钥/算法轮换免疫（配置改了测试自动跟随）。
 */
@Slf4j
@ActiveProfiles("local")
@DisplayName("jasypt 端到端")
@SpringBootTest(classes = JevokitSampleApplication.class)
@AutoConfigureMockMvc
class JasyptE2ETest {

    private static final String PLAIN = "demo-password";

    @Autowired
    MockMvc mockMvc;
    /** 与 JasyptApi 同源同限定：kit 的 encryptor bean（上下文中另有 jasypt starter 的 lazyJasyptStringEncryptor，须消歧） */
    @Autowired
    @Qualifier(JasyptConst.ENCRYPTOR_NAME)
    StringEncryptor encryptor;

    @Test
    @DisplayName("POST /jasypt/test：ENC 包装密文经端点解密还原明文（自产密文往返）")
    void jasyptDecryptRoundTrip() throws Exception {
        // given：真实 encryptor bean 按当前演示密钥产出密文（对密钥/算法轮换免疫）
        String cipher = encryptor.encrypt(PLAIN);
        assertThat(cipher).as("加密产物应非空且非明文").isNotBlank().isNotEqualTo(PLAIN);

        // when / then：ENC() 包装形态走端点，detector 识别 → 解包 → 解密还原
        String response = mockMvc.perform(MockMvcRequestBuilders
                        .post("/jasypt/test")
                        .queryParam("encryptVal", "ENC(" + cipher + ")"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(PLAIN))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(response).isEqualTo(PLAIN);
    }
}
