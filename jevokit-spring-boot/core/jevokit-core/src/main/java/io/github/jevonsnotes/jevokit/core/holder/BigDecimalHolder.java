package io.github.jevonsnotes.jevokit.core.holder;

import java.math.BigDecimal;

/**
 * BigDecimal 可变值信封：在 lambda/匿名类回调中承载可变的金额类状态（绕开局部变量 effectively final 约束）。
 * 注意与 util 包的 {@code BigDecimalHolder}（链式计算器）用途完全不同，勿混用。
 */
public class BigDecimalHolder implements Holder{
    /** 承载的 BigDecimal 值（公开字段，回调内直接读写） */
    public BigDecimal value;
    /**
     * 以初始值构造信封
     *
     * @param value 初始值
     */
    public BigDecimalHolder(BigDecimal value) {
        this.value = value;
    }
}
