package springbootkit.core.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Description TODO
 * @Date 2023/3/13 8:45
 * @Version 0.0.1
 */
@Data
@Accessors(chain = true)
public class SqlBundleDto {
    private String sql;
    private Object[] args;
}
