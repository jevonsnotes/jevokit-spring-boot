package io.github.jevonsnotes.jevokit.core.util;

/**
 * @Description TODO
 * @Date 2023/3/24 10:40
 * @Version 0.0.1
 */
public class TimeUtil {

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

    public static String humanSec(double sec) {
        return humanMSec(sec * 1000);
    }
}
