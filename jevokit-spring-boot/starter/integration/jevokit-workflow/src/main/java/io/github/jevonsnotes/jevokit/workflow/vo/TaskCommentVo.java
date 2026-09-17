package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.Date;

/**
 * 任务审批意见写入入参：comment 为意见内容，taskId/procInsId 定位目标任务。
 * 意见进 Camunda Comment 表，随历史时间线（taskHisTimelines）回读。
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
