package io.github.jevonsnotes.jevokit.core.processor;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.thread.ThreadUtil;
import lombok.Getter;
import io.github.jevonsnotes.jevokit.core.lang.CallbackPR;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

/**
 * 批量处理器，用于切割列表分批处理
 */
@Getter
public class BatchProcessor<B, R> {
    private Collection results;
    /**
     * 每批处理数据量，默认1000
     */
    private int batchLimit = 1000;
    /**
     * 异常收集
     */
    private final ConcurrentLinkedQueue<Exception> errors = new ConcurrentLinkedQueue<>();
    /**
     * 忽略多线程错误中断，启用多线程时生效，默认true
     */
    private boolean ignoreMultiThreadError = true;
    /**
     * 启用多线程
     */
    private boolean multiThread;

    /**
     * 私有构造，统一经 {@link #create()} 创建实例
     */
    private BatchProcessor() {

    }

    /**
     * 创建实例（默认单线程、每批 1000 条、多线程错误不中断）
     *
     * @param <B> 批数据元素类型
     * @param <R> 处理结果元素类型
     * @return 新处理器实例
     */
    public static <B,R> BatchProcessor<B,R> create(){
        return new BatchProcessor<>();
    }


    /**
     * 按线程模式初始化结果容器（多线程用并发队列，单线程用 ArrayList）
     */
    private void initContainer() {
        if (multiThread) {
            results = new ConcurrentLinkedQueue();
        } else {
            results = new ArrayList<>();
        }
    }

    /**
     * 清空上一轮处理的结果与错误收集（实例复用时调用）
     */
    public void reset(){
        results = null;
        errors.clear();
    }


    /**
     * 分批处理列表
     *
     * @param batchList 待处理全量数据，按 batchLimit 切片
     * @param callback  单批处理函数，入参为子列表，返回本批结果集合（null 视为无产出）
     * @param <B2>      批数据元素类型（跟随本次调用）
     * @param <R2>      处理结果元素类型
     * @return 本处理器（链式取结果）
     * @throws RuntimeException 多线程关闭（或 ignoreMultiThreadError=false）时处理异常直接上抛；
     *                           多线程且忽略错误时异常转入 {@link #getErrors()}，不中断其余批次
     */
    public <B, R> BatchProcessor<B, R> process(List<B> batchList, CallbackPR<List<B>,Collection<R>> callback) {
        initContainer();
        errors.clear();
        try {
            int loop = batchList.size() / batchLimit + (batchList.size() % batchLimit > 0 ? 1 : 0);
            final CountDownLatch latch = multiThread ? ThreadUtil.newCountDownLatch(loop) : null;
            for (int page = 1; page <= loop; page++) {
                List<B> bs = batchList.subList((page - 1) * batchLimit, Math.min(page * batchLimit, batchList.size()));
                multiOrSingleThread(bs,callback, latch);
            }

            if (latch != null) {
                latch.await();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return (BatchProcessor<B, R>) this;
    }

    /**
     * 决策单线程或多线程处理
     *
     * @param subList
     * @param latch
     */
    private <B,R>void multiOrSingleThread(final List<B> subList,CallbackPR<List<B>,Collection<R>> callback, final CountDownLatch latch) {
        if (latch != null) {
            ThreadUtil.execute(() -> {
                try {
                    callProcessor(subList,callback);
                } catch (Exception e) {
                    if(ignoreMultiThreadError){
                        errors.add(e);
                    } else {
                        throw e;
                    }
                } finally {
                    latch.countDown();
                }
            });
        } else {
            callProcessor(subList,callback);
        }

    }

    /**
     * 调用处理器
     * @param subList
     * @param callback
     * @param <B>
     * @param <R>
     */
    private <B,R>void callProcessor(List<B> subList,CallbackPR<List<B>,Collection<R>> callback) {
        Collection<R> rs = callback.call(subList);
        if (rs != null) {
            results.addAll(rs);
        }
    }

    /**
     * 取全部批次的汇总结果
     *
     * @return 汇总结果集合（多线程时为无序 ConcurrentLinkedQueue，单线程为 ArrayList）；未执行 process 时为 null
     */
    public Collection<R> getResults() {
        return results;
    }
    /**
     * 取汇总结果并可选复位处理器
     *
     * @param reset true 时取值后清空结果与错误收集（实例复用）
     * @return 汇总结果集合
     */
    public Collection<R> getResults(boolean reset) {
        Collection<R> results = getResults();
        if(reset){
            reset();
        }
        return results;
    }

    /**
     * collection转list类型
     *
     * @return 汇总结果的 List 视图（内部本就是 List 时原样返回，否则新建 ArrayList 拷贝）
     */
    public List<R> getResAsList() {
        return (List<R>) (results instanceof List ? results : CollectionUtil.addAll(new ArrayList<>(), results));
    }

    /**
     * 针对返回int类型的结果进行总计
     *
     * @return 结果中 Integer 元素的合计（非 Integer 元素跳过；判定方式较为宽松，混合类型时慎用）
     */
    public int getIntSum() {
        int res = 0;
        for (R next : (Iterable<R>) results) {
            if (next.getClass().isAssignableFrom(Integer.class)) {
                res += (Integer) next;
            }
        }
        return res;
    }



    /**
     * 设置每批处理数据量
     *
     * @param batchLimit 每批条数，需大于 0
     * @return 本处理器（链式）
     */
    public BatchProcessor<B, R> setBatchLimit(int batchLimit) {
        this.batchLimit = batchLimit;
        return (BatchProcessor<B, R>)this;
    }

    /**
     * 设置是否启用多线程分批
     *
     * @param multiThread true 时各批次提交公共线程池并发执行，false 同步逐批
     * @return 本处理器（链式）
     */
    public <B,R> BatchProcessor<B, R> setMultiThread(boolean multiThread) {
        this.multiThread = multiThread;
        return (BatchProcessor<B, R>) this;
    }

    /**
     * 设置多线程模式下批次异常的处理方式
     *
     * @param ignoreMultiThreadError true（默认）异常收集进 errors 不中断其余批次；false 异常上抛
     * @return 本处理器（链式）
     */
    public <B,R> BatchProcessor<B, R> setIgnoreMultiThreadError(boolean ignoreMultiThreadError) {
        this.ignoreMultiThreadError = ignoreMultiThreadError;
        return (BatchProcessor<B, R>) this;
    }
}
