package springbootkit.configuration.audit.log;

import lombok.Data;
import springbootkit.core.consts.HttpMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description TODO
 * @Date 2022/6/28 14:31
 * @Version 0.0.1
 */
@Data
public class AuditLogContentLimitProperties {
    private Integer queryString = -1;
    private Integer requestBody = -1;
    private Integer responseBody = -1;
    private Strategy strategy = Strategy.TRUNC;

    public enum Strategy {
        TRUNC,ABORT
    }

}
