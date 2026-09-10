package springbootkit.core.lang;

import lombok.Data;

/**
 * @Description TODO
 * @Date 2024/12/3
 * @Version 0.0.1
 */
@Data
public class SimpleData<T> {
    private T data;
}
