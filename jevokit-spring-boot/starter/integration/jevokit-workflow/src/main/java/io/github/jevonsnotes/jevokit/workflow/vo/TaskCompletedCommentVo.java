package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.workflow.annotation.FlowVar;

 /**
 * 工作流任务对象
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
