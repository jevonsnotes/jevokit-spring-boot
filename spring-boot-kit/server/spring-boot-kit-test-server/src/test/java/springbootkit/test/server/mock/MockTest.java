package springbootkit.test.server.mock;

import cn.hutool.core.io.FastByteArrayOutputStream;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.map.MapBuilder;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import springbootkit.api.dto.JSecureResponse;
import springbootkit.api.util.JSecureApiUtil;
import springbootkit.core.lang.ApiResponse;
import springbootkit.okhttp.util.OkHttpUtil;
import springbootkit.test.server.SpringBootKitTestServerApplication;
import springbootkit.test.server.service.RedisService;
import springbootkit.test.server.vo.DemoTaskStartVo;
import springbootkit.test.server.vo.TestVo;
import springbootkit.test.server.vo.TopicVo;
import springbootkit.workflow.consts.FlowState;
import springbootkit.workflow.vo.*;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;


/**
 * @Description TODO
 * @Date 2025/7/2
 * @Version 0.0.1
 */
@Slf4j
@DisplayName("模拟测试")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {SpringBootKitTestServerApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MockTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    RedisService redisService;


    /**
     * 测试审计日志
     *
     * @throws Exception
     */
    @Transactional
    @Test
    public void testAuditLog() throws Exception {
        TestVo mock = getMockTestVo();
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                        .post("/audit-log/test")
                        .content(JSON.toJSONString(mock))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value(mock.getName()))
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        String contentAsString = mvcResult.getResponse().getContentAsString();
        System.out.println(contentAsString);
    }

    /**
     * 测试脱敏
     *
     * @throws Exception
     */
    @Test
    public void testDesensitization() throws Exception {
        TestVo mock = getMockTestVo();
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                        .post("/desensitization/test")
                        .content(JSON.toJSONString(mock))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value(containsString("*")))
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        String contentAsString = mvcResult.getResponse().getContentAsString();
        System.out.println(contentAsString);
    }

    /**
     * 测试加解密
     *
     * @throws Exception
     */
    @Test
    public void testJasypt() throws Exception {

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                        .post("/jasypt/test")
                        .queryParam("encryptVal", "ENC(Zvnlzd4m/DHE+WEQq4ROTT+EePp45eQ0)")
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("demo-password"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        String contentAsString = mvcResult.getResponse().getContentAsString();
        System.out.println(contentAsString);
    }

    /**
     * 测试注册中心
     *
     * @throws Exception
     */
    @Test
    public void testRocketMq() throws Exception {
        Object mock = getTopicVo();
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                        .post("/rocketmq/test")
                        .content(JSON.toJSONString(mock))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("success"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        String contentAsString = mvcResult.getResponse().getContentAsString();
        System.out.println(contentAsString);
    }

    /**
     * 测试单号获取
     *
     * @throws Exception
     */
    @Test
    @Transactional(propagation = Propagation.NEVER) //禁用事务，让数据提交
    public void testNoGen() throws Exception {
        String bizNo = "demo" + RandomUtil.randomString(8);
        log.info("模拟获取单号，业务类型 {}", bizNo);
        int update = jdbcTemplate.update("""
                INSERT INTO biz_no (biz_tag, max_id, step, mode, threhold, description) VALUES ('%s', 0, 10000, 1, '70.00', '单号获取测试demo');
                """.formatted(bizNo));
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                        .post("/nogen/test")
                        .queryParam("loop", "100000")
                        .queryParam("bizNo", bizNo)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("success"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        String contentAsString = mvcResult.getResponse().getContentAsString();
        System.out.println(contentAsString);
    }
    /**
     * 测试单号回收
     *
     * @throws Exception
     */
    @Test
    @Transactional(propagation = Propagation.NEVER) //禁用事务，让数据提交
    public void testNoGenRecycle() throws Exception {
        String bizNo = "demo" + RandomUtil.randomString(8);
        log.info("模拟获取单号，业务类型 {}", bizNo);
        int update = jdbcTemplate.update("""
                INSERT INTO biz_no (biz_tag, max_id, step, mode, threhold, description) VALUES ('%s', 0, 10000, 1, '70.00', '单号获取测试demo');
                """.formatted(bizNo));
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                        .post("/nogen/testRecycle")
                        .queryParam("loop", "100000")
                        .queryParam("bizNo", bizNo)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("success"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        String contentAsString = mvcResult.getResponse().getContentAsString();
        System.out.println(contentAsString);
    }
    /**
     * 测试redis
     *
     */
    @Test
    public void testRedis() {

        String cache1 = redisService.userCache();
        String cache2 = redisService.userCache();
        Assert.isTrue(cache1.equals(cache2));
    }

    /**
     * 测试SecureApi
     *
     */
    @Test
    public void testSecureApi() {

        TestVo demo = TestVo.builder().name("demo").build();
        JSecureResponse<TestVo> gzDemo = JSecureApiUtil
                .getExecutor("gz_demo","/secure/test",demo,TestVo.class)
                .transNo(UUID.randomUUID().toString())
                .transType("TN0000001")
                .execute((url,json, headers) -> {

                    try {
                        //构建请求
                        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
                                .post(url)
                                .content(json)
                                .contentType(MediaType.APPLICATION_JSON);
                        // 封装请求头
                        headers.forEach(requestBuilder::header);
                        // 模拟请求
                        return mockMvc.perform(requestBuilder)
                                .andExpect(MockMvcResultMatchers.status().isOk())
                                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"))
                                .andDo(MockMvcResultHandlers.print())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        log.info("SecureApi response {}", JSON.toJSONString(gzDemo));
    }

    /**
     * 测试工作流
     * @throws Exception 抛出异常
     */
    @Test
    @Transactional(propagation = Propagation.NEVER)
    public void testWorkflow() throws Exception {
        String drafterCode = "00000000";
        String diShiAuditCode = "demo_city";
        String processDefinitionKey = "task_demo_simple_audit";

        // 部署流程
        URL resource = ResourceUtil.getResource("processes/task_demo_simple_audit.bpmn");
        String bpmn = IoUtil.readUtf8(resource.openStream());

        DeploymentDeployVo deployVo = DeploymentDeployVo.builder().name(processDefinitionKey).build();
        deployVo.getResources().add(DeploymentResource.builder().name(processDefinitionKey).text(bpmn).build());
        mockMvc.perform(MockMvcRequestBuilders.post("/workflow/deployment/deploy")
                        .content(JSON.toJSONString(deployVo))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 构建发起对象
        DemoTaskStartVo startVo = DemoTaskStartVo.builder()
                .processDefinitionKey(processDefinitionKey)
                .businessKey("dishi_active_audit")
                .drafterCode(drafterCode)
                .build();

        // 起草人 发起流程 自动认领
        ApiResponse<String> processInstanceId = JSONObject.parseObject(mockMvc.perform(MockMvcRequestBuilders.post("/workflow/start")
                        .content(JSON.toJSONString(startVo))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString(), ApiResponse.class);




        // 起草人 查询自己任务
        String taskListContent = mockMvc.perform(MockMvcRequestBuilders.post("/workflow/search")
                        .content(
                                JSON.toJSONString(
                                        TaskQueryVo.builder()
                                                .processDefinitionKey(processDefinitionKey)
                                                .taskDefinitionKey("audit_faqi")
                                                .build())
                        )
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString();
        ApiResponse<List<TaskInfoVo>> taskVos = JSONObject.parseObject(taskListContent, new TypeReference<>() {});
        String taskId = taskVos.getData().get(0).getTaskId();

        // 起草人 提交任务

        TaskCompletedVo<?> completedVo = TaskCompletedVo.tcBuilder()
                .taskId(taskId)
                .assignee(drafterCode)
                .flowState(FlowState.SUBMIT.code())
                .data(MapBuilder.create().put("activity_num", 235).build())
                .build();
        mockMvc.perform(MockMvcRequestBuilders.post("/workflow/complete")
                        .content(JSON.toJSONString(completedVo))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 地市审核岗-一级 查询任务
        String diShiTaskListContent = mockMvc.perform(MockMvcRequestBuilders.post("/workflow/search")
                        .content(
                                JSON.toJSONString(
                                        TaskQueryVo.builder()
                                                .processDefinitionKey(processDefinitionKey)
                                                .taskDefinitionKey("audit_dishi_01")
                                                .build())
                        )
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString();
        ApiResponse<List<TaskInfoVo>> diShiTaskVos = JSONObject.parseObject(diShiTaskListContent, new TypeReference<>() {});
        String diShiTaskId = diShiTaskVos.getData().get(0).getTaskId();

        // 地市审核岗-一级 认领任务
        mockMvc.perform(MockMvcRequestBuilders.post("/workflow/claim/do")
                        .content(
                                JSON.toJSONString(
                                        TaskClaimVo.builder()
                                                .taskId(diShiTaskId)
                                                .assignee(diShiAuditCode)
                                                .build())
                        )
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString();


        // 地市审核岗-一级 提交任务
        mockMvc.perform(MockMvcRequestBuilders.post("/workflow/complete")
                        .content(
                                JSON.toJSONString(
                                        TaskCompletedVo.tcBuilder()
                                                .taskId(diShiTaskId)
                                                .assignee(diShiAuditCode)
                                                .flowState(FlowState.PASS.code())
                                                .build())
                        )
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 省级审核岗 查询任务
        String shengJiTaskListContent = mockMvc.perform(MockMvcRequestBuilders.post("/workflow/search")
                        .content(
                                JSON.toJSONString(
                                        TaskQueryVo.builder()
                                                .processDefinitionKey(processDefinitionKey)
                                                .taskDefinitionKey("audit_shengji")
                                                .build())
                        )
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString();
        ApiResponse<List<TaskInfoVo>> shengJiTaskVos = JSONObject.parseObject(shengJiTaskListContent, new TypeReference<>() {});
        String procInsId = shengJiTaskVos.getData().get(0).getProcInsId();

        Assert.isTrue(procInsId.equals(processInstanceId.getData()),"流程ID不一致");
    }

    @Test
    public void testExcel() throws Exception {
        URL resources = ResourceUtil.getResource("tpl/excel/importExcel.xlsx");

        MockPart part = new MockPart("file1",
                "importExcel.xlsx",
                IoUtil.readBytes(resources.openStream()),
                MediaType.MULTIPART_FORM_DATA);
        String contentAsString = mockMvc.perform(MockMvcRequestBuilders
                        .multipart("/excel/importExcel")
//                        .file("file1",IoUtil.readBytes(resources.openStream()))
                        .part(part)
                        .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString();
        log.info(contentAsString);
    }


    private TestVo getMockTestVo() throws Exception {
        TestVo mock = Mockito.mock(TestVo.class);
        Mockito.when(mock.getName()).thenReturn(RandomUtil.randomString(8));
        return mock;
    }

    private TopicVo getTopicVo() {
        TopicVo topicVo = new TopicVo();
        topicVo.setType(1);
        topicVo.setAsync(false);
        topicVo.setSleepTime(1000);
        topicVo.setTransactionRollback(false);
        return topicVo;
    }


}
