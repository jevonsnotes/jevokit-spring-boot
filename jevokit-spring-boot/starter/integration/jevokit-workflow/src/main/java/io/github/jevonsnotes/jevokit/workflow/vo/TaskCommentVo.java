package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.Date;

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
public class TaskCommentVo implements TaskVo{
    /**
     * 任务ID
     */
    private String taskId;
    /**
     * 流程ID
     */
    private String procInsId;
    /**
     * 评论内容
     */
    private String comment;
    /**
     * 评论ID
     */
    private String id;
    /**
     * 评论创建时间
     */
    private Date createTime;


}
