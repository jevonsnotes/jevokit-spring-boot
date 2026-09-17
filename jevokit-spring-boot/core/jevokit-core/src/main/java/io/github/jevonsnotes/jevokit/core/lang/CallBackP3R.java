package io.github.jevonsnotes.jevokit.core.lang;


/**
 * 三参有返回值回调：无 JDK 等价物的扩展形态，供需要三元上下文的转换器使用，
 * 如 {@code SecurityUtil.filterStr} 的字符串过滤器（对象、字段、原值 → 新值）。
 * <p>注意泛型声明顺序为 R,P1,P2,P3（返回类型在前），与直觉的"先参后返"相反，书写类型引用时需留意。
 *
 * @param <R>  回调返回值类型
 * @param <P1> 第一个入参类型
 * @param <P2> 第二个入参类型
 * @param <P3> 第三个入参类型
 */
public interface CallBackP3R<R,P1,P2,P3> {
    /**
     * 执行回调逻辑
     *
     * @param p1 第一个入参
     * @param p2 第二个入参
     * @param p3 第三个入参
     * @return 回调结果
     */
    R call(P1 p1, P2 p2,P3 p3);
}
