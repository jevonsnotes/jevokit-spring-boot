package springbootkit.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TimeUtil} 单元测试：毫秒/秒的人类可读格式化（各量级边界）。
 *
 * <p>期望值全部按实现格式手工推算并固化为字面量（非运行时重新计算），
 * 用于锁定格式契约："%+.3f ms" / "%02dm%06.3fs" / "%02dh%02dm%06.3fs" / "%.0fd ..."。
 */
@DisplayName("TimeUtil 人类可读时长")
class TimeUtilTest {

    @Test
    @DisplayName("humanMSec：毫秒量级（<1000）")
    void humanMSecMilliseconds() {
        assertThat(TimeUtil.humanMSec(0)).as("0ms").isEqualTo("0.000 ms");
        assertThat(TimeUtil.humanMSec(123.456)).as("小数毫秒").isEqualTo("123.456 ms");
        assertThat(TimeUtil.humanMSec(999.999)).as("毫秒上限").isEqualTo("999.999 ms");
    }

    @Test
    @DisplayName("humanMSec：秒量级（1s ~ 59.999s）")
    void humanMSecSeconds() {
        assertThat(TimeUtil.humanMSec(1000)).as("1s 边界：00m01.000s").isEqualTo("00m01.000s");
        assertThat(TimeUtil.humanMSec(61_234)).as("1m1.234s").isEqualTo("01m01.234s");
        assertThat(TimeUtil.humanMSec(59_999)).as("分钟前一毫秒：00m59.999s").isEqualTo("00m59.999s");
    }

    @Test
    @DisplayName("humanMSec：小时量级（1h ~ 23.99h）")
    void humanMSecHours() {
        assertThat(TimeUtil.humanMSec(3_600_000)).as("1h 边界：01h00m00.000s").isEqualTo("01h00m00.000s");
        assertThat(TimeUtil.humanMSec(3_723_400)).as("1h2m3.4s").isEqualTo("01h02m03.400s");
    }

    @Test
    @DisplayName("humanMSec：天量级（>=24h）")
    void humanMSecDays() {
        assertThat(TimeUtil.humanMSec(86_400_000)).as("1d 边界：1d 00h00m00.000s").isEqualTo("1d 00h00m00.000s");
        assertThat(TimeUtil.humanMSec(90_061_000)).as("25h1m1s：1d 01h01m01.000s").isEqualTo("1d 01h01m01.000s");
    }

    @Test
    @DisplayName("humanSec：秒入参换算毫秒后格式化")
    void humanSec() {
        assertThat(TimeUtil.humanSec(0.5)).as("0.5s → 500ms").isEqualTo("500.000 ms");
        assertThat(TimeUtil.humanSec(59.999)).as("59.999s → 00m59.999s").isEqualTo("00m59.999s");
        assertThat(TimeUtil.humanSec(60)).as("60s → 01m00.000s").isEqualTo("01m00.000s");
        assertThat(TimeUtil.humanSec(90_061)).as("90061s → 1d 01h01m01.000s").isEqualTo("1d 01h01m01.000s");
    }
}
