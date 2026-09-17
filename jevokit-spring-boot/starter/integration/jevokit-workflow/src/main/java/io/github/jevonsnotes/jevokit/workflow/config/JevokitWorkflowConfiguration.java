package io.github.jevonsnotes.jevokit.workflow.config;

import org.camunda.bpm.engine.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.jevonsnotes.jevokit.workflow.service.JevokitWorkflowService;

/**
 * @Description 工作流组件自动化配置
 * @Date 2023/5/9 13:44
 * @Version 0.0.1
 */
@Configuration
public class JevokitWorkflowConfiguration {

    @ConditionalOnMissingBean(JevokitWorkflowService.class)
    @Bean
    public JevokitWorkflowService jevokitWorkflowService(
            RuntimeService runtimeService,
            TaskService taskService,
            HistoryService historyService,
            IdentityService identityService,
            RepositoryService repositoryService) {
        return new JevokitWorkflowService(runtimeService, taskService, historyService, identityService,repositoryService);
    }
}
