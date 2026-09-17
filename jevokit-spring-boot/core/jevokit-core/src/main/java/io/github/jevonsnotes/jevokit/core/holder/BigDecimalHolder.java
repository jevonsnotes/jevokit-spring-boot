package io.github.jevonsnotes.jevokit.core.holder;

import java.math.BigDecimal;

/**
 * BigDecimal 可变值信封：在 lambda/匿名类回调中承载可变的金额类状态（绕开局部变量 effectively final 约束）。
 * 注意与 util 包的 {@code BigDecimalHolder}（链式计算器）用途完全不同，勿混用。
 */
public class BigDecimalHolder implements Holder{
    public BigDecimal value;
    public BigDecimalHolder(BigDecimal value) {
        this.value = value;
    }
}
