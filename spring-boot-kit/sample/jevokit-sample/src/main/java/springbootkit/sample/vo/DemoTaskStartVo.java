package springbootkit.sample.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import springbootkit.workflow.annotation.FlowVar;
import springbootkit.workflow.vo.TaskStartVo;

/**
 * @Description TODO
 * @Date 2025/2/27
 * @Version 0.0.1
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class DemoTaskStartVo extends TaskStartVo {
    /**
     * 地市
     */
    @FlowVar(ignoreIfNull = false)
    private String diShiCode;

    /**
     * 省级
     */
    @FlowVar(ignoreIfNull = false)
    private String shengJiCode;

}
