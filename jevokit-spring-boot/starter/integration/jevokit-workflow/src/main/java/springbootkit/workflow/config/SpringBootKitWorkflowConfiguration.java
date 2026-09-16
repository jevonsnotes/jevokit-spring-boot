package springbootkit.workflow.config;

import org.camunda.bpm.engine.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springbootkit.workflow.service.SpringBootKitWorkflowService;

/**
 * @Description 工作流组件自动化配置
 * @Date 2023/5/9 13:44
 * @Version 0.0.1
 */
@Configuration
public class SpringBootKitWorkflowConfiguration {

    @ConditionalOnMissingBean(SpringBootKitWorkflowService.class)
    @Bean
    public SpringBootKitWorkflowService springBootKitWorkflowService(
            RuntimeService runtimeService,
            TaskService taskService,
            HistoryService historyService,
            IdentityService identityService,
            RepositoryService repositoryService) {
        return new SpringBootKitWorkflowService(runtimeService, taskService, historyService, identityService,repositoryService);
    }
}
