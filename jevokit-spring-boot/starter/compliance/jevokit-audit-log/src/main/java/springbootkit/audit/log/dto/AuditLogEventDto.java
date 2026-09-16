package springbootkit.audit.log.dto;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.Date;

/**
 * @Description TODO
 * @Date 2024/6/28 17:08
 * @Version 0.0.1
 */
@Data
@SuperBuilder
public class AuditLogEventDto {
    private Date startTime;
    private Date endTime;
    private String localKey;
    private String globalKey;
    private String parentKey;
    private AuditLogEventDto parent;
}
