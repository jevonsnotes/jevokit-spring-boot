package io.github.jevonsnotes.jevokit.workflow.config;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import io.github.jevonsnotes.jevokit.workflow.service.JevokitWorkflowService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * workflow 自动装配条件单测（B 档测试重构 #5）。
 * camunda 五服务以 mock 提供，仅验证装配与 @ConditionalOnMissingBean 覆盖语义，不启动流程引擎。
 */
class JevokitWorkflowConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JevokitWorkflowConfiguration.class))
            .withBean(RuntimeService.class, () -> org.mockito.Mockito.mock(RuntimeService.class))
            .withBean(TaskService.class, () -> org.mockito.Mockito.mock(TaskService.class))
            .withBean(HistoryService.class, () -> org.mockito.Mockito.mock(HistoryService.class))
            .withBean(IdentityService.class, () -> org.mockito.Mockito.mock(IdentityService.class))
            .withBean(RepositoryService.class, () -> org.mockito.Mockito.mock(RepositoryService.class));

    @Test
    @DisplayName("camunda 服务在位时 kit 工作流服务成套装配")
    void wiresWorkflowService() {
        runner.run(context -> {
            assertThat(context).as("上下文应正常启动").hasNotFailed();
            assertThat(context).as("JevokitWorkflowService 应装配")
                    .hasSingleBean(JevokitWorkflowService.class);
        });
    }

    @Test
    @DisplayName("用户自带 JevokitWorkflowService 时 kit 默认让位")
    void customWorkflowServiceTakesPrecedence() {
        JevokitWorkflowService custom = org.mockito.Mockito.mock(JevokitWorkflowService.class);

        runner.withBean("customWorkflowService", JevokitWorkflowService.class, () -> custom)
                .run(context -> {
                    assertThat(context).as("应只有一个工作流服务 bean")
                            .hasSingleBean(JevokitWorkflowService.class);
                    assertThat(context.getBean(JevokitWorkflowService.class))
                            .as("生效的应是用户自定义实例").isSameAs(custom);
                });
    }
}
