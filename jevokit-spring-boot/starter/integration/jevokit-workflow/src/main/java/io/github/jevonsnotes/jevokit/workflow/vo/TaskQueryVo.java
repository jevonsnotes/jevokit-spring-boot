package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.workflow.annotation.FlowVar;

/**
 * 任务查询过滤条件：流程定义 key / 节点定义 key / 起草人 / 业务主键四维组合，任一可空。
 * 供 taskQuery 族收敛查询范围（当前任务与历史任务均可）。
 */
//@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class TaskQueryVo implements TaskVo{
    /**
     * 工作流定义主键
     * 此值与bpmn的process id保持一致
     */
    private String processDefinitionKey;
    /**
     * 工作流节点文件定义值
     * 此值与bpmn的task id保持一致
     */
    private String taskDefinitionKey;


    /**
     * 起草人
     */
    @FlowVar
    private String drafterCode;


    /**
     * 业务主键
     */
    @FlowVar
    private String businessKey;

}
