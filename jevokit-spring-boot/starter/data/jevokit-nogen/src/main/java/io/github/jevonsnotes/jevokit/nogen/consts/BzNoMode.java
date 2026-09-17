package io.github.jevonsnotes.jevokit.nogen.consts;

import lombok.AllArgsConstructor;
import lombok.Getter;

 /**
 * 单号生成模式：DEFAULT 顺序发号（不回收）；RECYCLE 可回收模式（业务释放的号段回池复用，
 * 回收接口仅 RECYCLE 模式可用）。绑定在 biz_no 配置行上，按业务粒度选择。
 */
@Getter
@AllArgsConstructor
public enum BzNoMode {
    DEFAULT(1),//默认模式
    RECYCLE(2), //可回收模式
    ;
    private Integer code;
}
