package springbootkit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.Date;
import java.util.List;

/**
 * @Description 工作流任务对象
 * @Date 2023/5/9 16:38
 * @Version 0.0.1
 */
//@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class TaskInfoVo implements TaskVo{
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
     * 工作流节点文件定义值
     * 此值与bpmn的task name保持一致
     */
    private String taskDefinitionName;
    /**
     * 流程ID
     */
    private String procInsId;
    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 受理人代码
     */
    private String assignee;
    /**
     * 受理人名称
     */
    private String assigneeName;
    /**
     * 状态
     */
    private String flowState;
    /**
     * 意见
     */
    private String comment;
    /**
     * 任务开始时间
     */
    private Date startTime;
    /**
     * 任务结束时间
     */
    private Date endTime;
    /**
     * 意见时间
     */
    private Date commentTime;
    /**
     * 附件
     */
    private List<TaskAttachmentVo> attachments;

}
