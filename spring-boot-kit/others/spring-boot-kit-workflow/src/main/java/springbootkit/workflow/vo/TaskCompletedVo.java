package springbootkit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import springbootkit.workflow.annotation.FlowVar;

import java.util.Map;

/**
 * @Description 工作流任务对象
 * @Date 2023/5/9 16:38
 * @Version 0.0.1
 */
//@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(builderMethodName = "tcBuilder")  //父类builder不带泛型，必须重命名字类builder,否则会产生覆盖冲突问题
@Accessors(chain = true)
public class TaskCompletedVo<T> implements TaskVo{
    /**
     * 任务ID
     */
    private String taskId;


    /**
     * 受理人
     */
    private String assignee;



    /**
     * 流程状态
     */
    @FlowVar
    private String flowState;

    /**
     * 数据载体
     * 如 Map<String,Object>
     */
    @FlowVar
    private T data;


}
