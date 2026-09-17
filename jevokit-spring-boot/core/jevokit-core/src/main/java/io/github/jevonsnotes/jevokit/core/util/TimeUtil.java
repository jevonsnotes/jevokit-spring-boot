package io.github.jevonsnotes.jevokit.core.util;

/**
 * 时长人性化格式化工具：把毫秒/秒数值渲染为人类易读的分段字符串。
 * <p>常用入口：{@link #humanMSec(double)}、{@link #humanSec(double)}。
 * <p>输出逐级进位：不足 1 秒为 "0.123 ms"；不足 1 小时为 "02m05.123s"；
 * 不足 1 天为 "03h02m05.123s"；超过 1 天为 "1d 03h02m05.123s"（天数不带前导零）。
 */
public class TimeUtil {

    /**
     * 毫秒数值转人读时长字符串
     *
     * @param msec 毫秒数
     * @return 人读时长字符串，格式随数量级进位
     */
    public static String humanMSec(double msec) {
        double seconds;
        double minutes;
        double hours;
        double days;


        if (msec < 1000.0)
            return String.format("%.3f ms", msec / 1f);

        seconds = msec / 1000.0;
        minutes = Math.floor(seconds / 60.0);
        seconds -= 60.0 * minutes;
        if (minutes < 60.0)
            return String.format("%02dm%06.3fs", (int) minutes, seconds);

        hours = Math.floor(minutes / 60.0);
        minutes -= 60.0 * hours;
        if (hours < 24.0)
            return String.format("%02dh%02dm%06.3fs", (int) hours, (int) minutes, seconds);

        days = Math.floor(hours / 24.0);
        hours -= 24.0 * days;
        return String.format("%.0fd %02dh%02dm%06.3fs", days, (int) hours, (int) minutes, seconds);
    }

    /**
     * 秒数值转人读时长字符串（内部放大 1000 倍复用 {@link #humanMSec(double)}）
     *
     * @param sec 秒数
     * @return 人读时长字符串
     */
    public static String humanSec(double sec) {
        return humanMSec(sec * 1000);
    }
}
