package springbootkit.workflow.config;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import springbootkit.workflow.service.SpringBootKitWorkflowService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * workflow 自动装配条件单测（B 档测试重构 #5）。
 * camunda 五服务以 mock 提供，仅验证装配与 @ConditionalOnMissingBean 覆盖语义，不启动流程引擎。
 */
class SpringBootKitWorkflowConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringBootKitWorkflowConfiguration.class))
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
            assertThat(context).as("SpringBootKitWorkflowService 应装配")
                    .hasSingleBean(SpringBootKitWorkflowService.class);
        });
    }

    @Test
    @DisplayName("用户自带 SpringBootKitWorkflowService 时 kit 默认让位")
    void customWorkflowServiceTakesPrecedence() {
        SpringBootKitWorkflowService custom = org.mockito.Mockito.mock(SpringBootKitWorkflowService.class);

        runner.withBean("customWorkflowService", SpringBootKitWorkflowService.class, () -> custom)
                .run(context -> {
                    assertThat(context).as("应只有一个工作流服务 bean")
                            .hasSingleBean(SpringBootKitWorkflowService.class);
                    assertThat(context.getBean(SpringBootKitWorkflowService.class))
                            .as("生效的应是用户自定义实例").isSameAs(custom);
                });
    }
}
