package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.camunda.bpm.engine.impl.persistence.entity.ByteArrayEntity;

import java.util.Date;

 /**
 * 任务附件描述：字段与 Camunda Attachment 一一对应，内容以 url 外链形态承载
 * （引擎只存描述与链接，不存文件本体）。
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
