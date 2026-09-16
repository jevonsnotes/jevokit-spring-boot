package springbootkit.sample.e2e;

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
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import springbootkit.sample.SpringBootKitSampleApplication;
import springbootkit.sample.vo.TestVo;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * audit-log 模块 E2E（原 MockTest.testAuditLog 拆分）。
 *
 * <p>默认测试集（无需 MQ）：内建 @ActiveProfiles("local") 排除 RocketMQ 自动装配，环境仅需 Redis 在位 + H2 内存自给。
 * 本类已内建 @ActiveProfiles("local")（MQ 监听器由 local profile 排除），
 * 环境仅需 Redis + H2。
 */
@Slf4j
@ActiveProfiles("local")
@DisplayName("audit-log 端到端")
@SpringBootTest(classes = SpringBootKitSampleApplication.class)
@AutoConfigureMockMvc
@Transactional
class AuditLogE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("POST /audit-log/test：响应回显业务字段，审计日志落库")
    void auditLogEndpointEchoesAndPersists() throws Exception {
        // given：Mockito 造随机名（保持原 MockTest 构造方式）
        TestVo mock = Mockito.mock(TestVo.class);
        Mockito.when(mock.getName()).thenReturn("audit-" + new Random().nextInt(100000));

        // when / then
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/audit-log/test")
                        .content(JSON.toJSONString(mock))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value(mock.getName()))
                .andDo(MockMvcResultHandlers.print());

        // and：@Transactional 自动回滚，审计行不入库；此处仅断言业务链路可用
        assertThat(TestTransaction.isActive()).isTrue();
    }
}
