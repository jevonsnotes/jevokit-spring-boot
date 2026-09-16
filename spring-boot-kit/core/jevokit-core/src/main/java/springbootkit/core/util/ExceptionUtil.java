package springbootkit.core.util;

/**
 * @Description TODO
 * @Date 2025/7/1
 * @Version 0.0.1
 */
public class ExceptionUtil extends cn.hutool.core.exceptions.ExceptionUtil {
    /**
     * 消息内容匹配
     *
     * @param throwable 异常对象
     * @param pattern   模式
     * @return 匹配结果
     */
    public static boolean loopMatchMessage(Throwable throwable, String pattern) {
        return loopMatchMessage(throwable, pattern, -1, 0);
    }

    /**
     * 消息内容匹配
     *
     * @param throwable 异常对象
     * @param pattern   模式
     * @return 匹配结果
     */
    public static boolean loopMatchMessage(Throwable throwable, String pattern, int maxDeep) {
        return loopMatchMessage(throwable, pattern, maxDeep, 0);
    }

    /**
     * 消息内容匹配
     *
     * @param throwable 异常对象
     * @param pattern   模式
     * @return 匹配结果
     */
    private static boolean loopMatchMessage(Throwable throwable, String pattern, int maxDeep, int current) {
        if (maxDeep == -1 || current < maxDeep) {
            if (throwable != null) {
                String message = throwable.getMessage();
                if (message != null) {
                    return message.matches(pattern) || loopMatchMessage(throwable.getCause(), pattern, maxDeep, current + 1);
                }
            }
        }
        return false;
    }
}
