package io.github.jevonsnotes.jevokit.core.lang;


/**
 * 双参有返回值回调：JDK BiFunction 语义的自定义等价物，如 {@code JsonUtil.editJsonArr} 的字符串转换器
 * （入参为属性名与原值、返回替换值）。
 * <p>注意泛型声明顺序为 R,P1,P2（返回类型在前、参数类型在后），与直觉的"先参后返"相反，书写类型引用时需留意。
 *
 * @param <R>  回调返回值类型
 * @param <P1> 第一个入参类型
 * @param <P2> 第二个入参类型
 */
public interface CallBackP2R<R,P1,P2> {
    /**
     * 执行回调逻辑
     *
     * @param p1 第一个入参
     * @param p2 第二个入参
     * @return 回调结果
     */
    R call(P1 p1, P2 p2);
}
