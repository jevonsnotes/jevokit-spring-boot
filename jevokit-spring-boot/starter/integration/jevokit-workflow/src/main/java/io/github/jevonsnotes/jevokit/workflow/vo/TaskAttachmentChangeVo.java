package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

 /**
 * 附件同步比对结果三桶（deletes / adds / noChanges）：由 taskAttachment 将目标清单与
 * 引擎现存附件按 id diff 产出，调用方据此执行增删，未变化集合用于回显确认。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class TaskAttachmentChangeVo implements TaskVo{
    @Builder.Default
    private List<TaskAttachmentVo> deletes = new ArrayList<>();
    @Builder.Default
    private List<TaskAttachmentVo> adds = new ArrayList<>();
    @Builder.Default
    private List<TaskAttachmentVo> noChanges = new ArrayList<>();
}
