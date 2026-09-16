package springbootkit.nogen.consts;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Description TODO
 * @Date 2023/3/14 9:43
 * @Version 0.0.1
 */
@Getter
@AllArgsConstructor
public enum BzNoMode {
    DEFAULT(1),//默认模式
    RECYCLE(2), //可回收模式
    ;
    private Integer code;
}
