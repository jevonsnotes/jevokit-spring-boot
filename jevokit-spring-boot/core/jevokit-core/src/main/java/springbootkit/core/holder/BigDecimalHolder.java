package springbootkit.core.holder;

import java.math.BigDecimal;

/**
 * @Description TODO
 * @Date 2025/6/26
 * @Version 0.0.1
 */
public class BigDecimalHolder implements Holder{
    public BigDecimal value;
    public BigDecimalHolder(BigDecimal value) {
        this.value = value;
    }
}
