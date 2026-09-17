package io.github.jevonsnotes.jevokit.core.util;

/**
 * 异常工具，在 hutool ExceptionUtil 基础上补充"沿因链匹配异常消息"的能力，用于识别根因型错误的归类与重试判定。
 * <p>常用入口：{@link #loopMatchMessage(Throwable, String)}（不限深度）或 {@link #loopMatchMessage(Throwable, String, int)}（限定因链深度）。
 * <p>关键语义：pattern 按 {@link String#matches} 全量匹配（非子串查找）；逐层下探 cause 链，任一层命中即返回 true。
 * 注意两处陷阱：maxDeep 传 -1 表示不限深度；某层异常的 message 为 null 时匹配直接终止并返回 false，不再继续向根因下探。
 */
public class ExceptionUtil extends cn.hutool.core.exceptions.ExceptionUtil {
    /**
     * 消息内容匹配（不限因链深度）
     *
     * @param throwable 异常对象
     * @param pattern   正则模式（全量匹配）
     * @return 匹配结果
     */
    public static boolean loopMatchMessage(Throwable throwable, String pattern) {
        return loopMatchMessage(throwable, pattern, -1, 0);
    }

    /**
     * 消息内容匹配（限定因链深度）
     *
     * @param throwable 异常对象
     * @param pattern   正则模式（全量匹配）
     * @param maxDeep   最大下探深度；-1 表示不限
     * @return 匹配结果
     */
    public static boolean loopMatchMessage(Throwable throwable, String pattern, int maxDeep) {
        return loopMatchMessage(throwable, pattern, maxDeep, 0);
    }

    /**
     * 消息内容匹配（递归实现，current 为当前深度）
     *
     * @param throwable 异常对象
     * @param pattern   正则模式（全量匹配）
     * @param maxDeep   最大下探深度；-1 表示不限
     * @param current   当前已下探深度（内部计数用，外部从 0 传起）
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
