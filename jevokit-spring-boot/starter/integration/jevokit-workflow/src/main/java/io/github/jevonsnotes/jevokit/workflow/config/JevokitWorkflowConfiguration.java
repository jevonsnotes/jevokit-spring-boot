package io.github.jevonsnotes.jevokit.workflow.config;

import org.camunda.bpm.engine.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.jevonsnotes.jevokit.workflow.service.JevokitWorkflowService;

/**
 * 工作流组件自动化配置
 */
@Configuration
public class JevokitWorkflowConfiguration {

    @ConditionalOnMissingBean(JevokitWorkflowService.class)
    @Bean
/**
    * 工作流门面 Bean：注入 Camunda 五大引擎服务。
    * @return 门面服务
    */
    public JevokitWorkflowService jevokitWorkflowService(
            RuntimeService runtimeService,
            TaskService taskService,
            HistoryService historyService,
            IdentityService identityService,
            RepositoryService repositoryService) {
        return new JevokitWorkflowService(runtimeService, taskService, historyService, identityService,repositoryService);
    }
}
