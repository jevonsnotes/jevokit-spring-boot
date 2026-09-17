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

    private BatchProcessor() {

    }

    /**
     * 创建实例
     * @param <B>
     * @param <R>
     * @return
     */
    public static <B,R> BatchProcessor<B,R> create(){
        return new BatchProcessor<>();
    }


    private void initContainer() {
        if (multiThread) {
            results = new ConcurrentLinkedQueue();
        } else {
            results = new ArrayList<>();
        }
    }

    public void reset(){
        results = null;
        errors.clear();
    }


    /**
     * 分批处理列表
     *
     * @param batchList
     * @return 汇总子列表处理结果
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

    public Collection<R> getResults() {
        return results;
    }
    public Collection<R> getResults(boolean reset) {
        Collection<R> results = getResults();
        if(reset){
            reset();
        }
        return results;
    }

    /**
     * collection转list类型
     * @return
     */
    public List<R> getResAsList() {
        return (List<R>) (results instanceof List ? results : CollectionUtil.addAll(new ArrayList<>(), results));
    }

    /**
     * 针对返回int类型的结果进行总计
     * @return
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



    public BatchProcessor<B, R> setBatchLimit(int batchLimit) {
        this.batchLimit = batchLimit;
        return (BatchProcessor<B, R>)this;
    }

    public <B,R> BatchProcessor<B, R> setMultiThread(boolean multiThread) {
        this.multiThread = multiThread;
        return (BatchProcessor<B, R>) this;
    }

    public <B,R> BatchProcessor<B, R> setIgnoreMultiThreadError(boolean ignoreMultiThreadError) {
        this.ignoreMultiThreadError = ignoreMultiThreadError;
        return (BatchProcessor<B, R>) this;
    }
}
