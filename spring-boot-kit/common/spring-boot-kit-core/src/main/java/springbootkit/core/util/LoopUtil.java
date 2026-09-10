package springbootkit.core.util;

import java.util.*;
import java.util.function.Supplier;

/**
 * @Description 循环工具
 * @Date 2025/7/9
 * @Version 0.0.1
 */
public class LoopUtil {

    public static <T>List<T> supplyToList(long count, Supplier<T> supplier) {

        return supplyToList(count,supplier,false);
    }
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
