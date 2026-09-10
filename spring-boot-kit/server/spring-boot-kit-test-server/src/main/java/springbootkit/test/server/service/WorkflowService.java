package springbootkit.test.server.service;

import org.camunda.bpm.engine.*;
import org.springframework.stereotype.Service;
import springbootkit.workflow.service.SpringBootKitWorkflowService;

/**
 * @Description
 * @Date 2025/4/29
 * @Version 0.0.1
 */
@Service
public class WorkflowService extends SpringBootKitWorkflowService {
    public WorkflowService(RuntimeService runtimeService, TaskService taskService, HistoryService historyService, IdentityService identityService, RepositoryService repositoryService) {
        super(runtimeService, taskService, historyService, identityService, repositoryService);
    }
}
