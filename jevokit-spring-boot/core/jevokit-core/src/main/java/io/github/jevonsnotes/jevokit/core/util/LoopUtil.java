package io.github.jevonsnotes.jevokit.core.util;

import java.util.*;
import java.util.function.Supplier;

/**
 * 循环工具
 */
public class LoopUtil {

    /**
     * 循环调用供应商收集为列表
     *
     * @param count    调用次数
     * @param supplier 元素供应商
     * @param <T>      元素类型
     * @return 收集结果（ArrayList）
     */
    public static <T>List<T> supplyToList(long count, Supplier<T> supplier) {

        return supplyToList(count,supplier,false);
    }
    /**
     * 循环调用供应商收集为列表
     *
     * @param count    调用次数
     * @param supplier 元素供应商
     * @param linked   true 使用 LinkedList（频繁头中部插入友好），false 使用 ArrayList（随机访问友好）
     * @param <T>      元素类型
     * @return 收集结果
     */
    public static <T>List<T> supplyToList(long count, Supplier<T> supplier,boolean linked) {
        List<T> container = linked ? new LinkedList<>() : new ArrayList<T>();
        for (int i = 0; i < count; i++) {
            container.add(supplier.get());
        }
        return container;
    }
    public static <T>Set<T> supplyToSet(long count, Supplier<T> supplier) {
        Set<T> container = new HashSet<>();
        for (int i = 0; i < count; i++) {
            container.add(supplier.get());
        }
        return container;
    }

    public static void run(long count, Runnable runnable) {
        for (int i = 0; i < count; i++) {
            runnable.run();
        }
    }

}
