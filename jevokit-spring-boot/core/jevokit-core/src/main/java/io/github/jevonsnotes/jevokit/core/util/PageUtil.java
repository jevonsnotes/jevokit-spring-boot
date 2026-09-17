package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.thread.ExecutorBuilder;
import io.github.jevonsnotes.jevokit.core.function.InheritTransactionSupplier;
import io.github.jevonsnotes.jevokit.core.holder.IntHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 分页处理工具
 */
public class PageUtil extends cn.hutool.core.util.PageUtil {
    private static final int availableProcessors = Runtime.getRuntime().availableProcessors();
    private static final ThreadPoolExecutor executor = ExecutorBuilder
            .create()
            .setHandler(new ThreadPoolExecutor.CallerRunsPolicy())
            .useSynchronousQueue()
            .setAllowCoreThreadTimeOut(true)
            .setCorePoolSize(availableProcessors)
            .setMaxPoolSize(availableProcessors*10)
            .build();

    /**
     * 分页,获取指定页数据
     *
     * @param data     数据
     * @param page     当前页 默认从0开始（基于hutool PageUtil）
     * @param pageSize 每页大小
     * @param <T>
     * @return 分页结果
     */
    public static <T> List<T> pagination(List<T> data, int page, int pageSize) {
        if (CollectionUtil.isNotEmpty(data)) {
            int start = getStart(page, pageSize);
            int end = (end = getEnd(page, pageSize)) > data.size() ? data.size() : end;
            return start > end ? data.subList(0, 0) : data.subList(start, end);
        }
        return data;
    }

    /**
     * 分页消费-同步
     *
     * @param data     数据
     * @param pageSize 每页大小
     * @param consumer 消费方法，入参为（当页子列表，页号从 0 起）
     * @param <T>      入参子元素类型
     * @param <R>      出参元素类型
     * @return 各页消费结果按页序聚合的列表；数据量不超过 pageSize 时整批一次消费（单元素）
     */
    public static <T, R> List<R> consume(List<T> data, int pageSize, BiFunction<List<T>, Integer, R> consumer) {
        return consume(data, pageSize, consumer, null);
    }


    /**
     * 分页消费-异步（使用内部共享线程池，CallerRunsPolicy 拒绝策略保证不丢任务）
     *
     * @param data     数据
     * @param pageSize 每页大小
     * @param consumer 消费方法，入参为（当页子列表，页号从 0 起）
     * @param <T>      入参子元素类型
     * @param <R>      出参元素类型
     * @return 各页消费结果按页序聚合的列表（内部 join 等待全部页完成）
     */
    public static <T, R> List<R> consumeAsync(List<T> data, int pageSize, BiFunction<List<T>, Integer, R> consumer) {
        return consume(data, pageSize, consumer, executor);
    }

    /**
     * 分页消费
     *
     * @param data     数据
     * @param pageSize 每页大小
     * @param consumer 消费方法，入参为（当页子列表，页号从 0 起）
     * @param executor 异步执行器；传 null 表示同步逐页执行
     * @param <T>      入参子元素类型
     * @param <R>      出参元素类型
     * @return 各页消费结果按页序聚合的列表；数据量不超过 pageSize 时整批一次消费（单元素）
     */
    public static <T, R> List<R> consume(List<T> data, int pageSize, BiFunction<List<T>, Integer, R> consumer, Executor executor) {
        List<R> result = new ArrayList<>();
        List<CompletableFuture<R>> futures = new ArrayList<>();
        IntHolder page = new IntHolder(0);
        if (CollectionUtil.isNotEmpty(data) && data.size() > pageSize) {
            while (true) {
                int start = getStart(page.value, pageSize);
                int end = (end = getEnd(page.value, pageSize)) > data.size() ? data.size() : end;
                List<T> sub = start > end ? data.subList(0, 0) : data.subList(start, end);
                // 页号快照：匿名 supplier 读 final 拷贝而非共享 IntHolder，
                // 避免异步执行时主线程已完成 page.value++ 导致 consumer 收到错乱页号（值捕获竞态）
                final int pageIndex = page.value;
                Supplier<R> supplier = new InheritTransactionSupplier<R>() {
                        @Override
                        public R inheritedGet() {
                            return consumer.apply(sub, pageIndex);
                        }
                    };
                //判断同步/异步处理
                if (executor != null) {
                    futures.add(CompletableFuture.supplyAsync(supplier, executor));
                } else {
                    result.add(supplier.get());
                }

                if (end >= data.size()) {
                    break;
                } else {
                    page.value++;
                }
            }
            // 异步结果归集
            if(CollectionUtil.isNotEmpty(futures)) {
                result = futures.stream().parallel().map(CompletableFuture::join).collect(Collectors.toList());
            }
        } else {
            result.add(consumer.apply(data, page.value));
        }
        return result;
    }

}
