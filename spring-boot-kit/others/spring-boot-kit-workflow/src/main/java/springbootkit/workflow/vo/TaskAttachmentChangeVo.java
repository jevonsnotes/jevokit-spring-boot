package springbootkit.workflow.vo;

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
 * @Description TODO
 * @Date 2025/2/27
 * @Version 0.0.1
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
