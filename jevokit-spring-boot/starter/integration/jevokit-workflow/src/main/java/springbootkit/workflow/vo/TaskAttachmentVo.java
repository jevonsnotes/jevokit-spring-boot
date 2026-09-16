package springbootkit.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.camunda.bpm.engine.impl.persistence.entity.ByteArrayEntity;

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
public class TaskAttachmentVo implements TaskVo{
    private String id;
    private int revision;
    private String name;
    private String description;
    private String type;
    private String taskId;
    private String processInstanceId;
    private String url;
    private String tenantId;
    private Date createTime;
    private String rootProcessInstanceId;
    private Date removalTime;


}
