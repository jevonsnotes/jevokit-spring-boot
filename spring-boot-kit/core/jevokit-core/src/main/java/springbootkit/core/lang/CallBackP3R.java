package springbootkit.core.lang;


/**
 * @Description TODO
 * @Date 2022/7/11 16:01
 * @Version 0.0.1
 */
public interface CallBackP3R<R,P1,P2,P3> {
    R call(P1 p1, P2 p2,P3 p3);
}
