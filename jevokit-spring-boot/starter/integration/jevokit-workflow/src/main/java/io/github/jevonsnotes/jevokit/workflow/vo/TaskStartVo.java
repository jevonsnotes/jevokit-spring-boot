package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.workflow.annotation.FlowVar;

import java.util.Map;

 /**
 * 发起流程入参：processDefinitionKey 必须与 BPMN 的 process id 一致；
 * @FlowVar 标注字段（起草人/业务主键）经 FlowUtil 提取为 GLOBAL 流程变量随实例启动写入。
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
