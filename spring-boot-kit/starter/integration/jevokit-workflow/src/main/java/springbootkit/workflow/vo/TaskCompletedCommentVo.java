package springbootkit.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import springbootkit.workflow.annotation.FlowVar;

/**
 * @Description 工作流任务对象
 * @Date 2023/5/9 16:38
 * @Version 0.0.1
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(builderMethodName = "tcBuilder")  //父类builder不带泛型，必须重命名字类builder,否则会产生覆盖冲突问题
@Accessors(chain = true)
public class TaskCompletedCommentVo<T> extends TaskCompletedVo<T>{
    /**
     * 意见
     */
    private String comment;

}
