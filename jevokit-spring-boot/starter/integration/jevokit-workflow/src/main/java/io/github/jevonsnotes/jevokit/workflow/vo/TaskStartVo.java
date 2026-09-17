package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.workflow.annotation.FlowVar;

import java.util.Map;

/**
 * @Description TODO
 * @Date 2025/2/27
 * @Version 0.0.1
 */
//@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class TaskStartVo implements TaskVo{
    /**
     * 工作流定义主键
     * 此值与bpmn的process id保持一致
     */
    private String processDefinitionKey;
//    /**
//     * 工作流节点文件定义值
//     * 此值与bpmn的task id保持一致
//     */
//    private String taskDefinitionKey;


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
