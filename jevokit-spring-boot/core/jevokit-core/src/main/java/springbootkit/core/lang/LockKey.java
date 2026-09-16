package springbootkit.core.lang;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

/**
 * @Description TODO
 * @Date 2024/12/13
 * @Version 0.0.1
 */
@Data
@AllArgsConstructor
public class LockKey{
    private Date createTime;
    private Date updateTime;
}
