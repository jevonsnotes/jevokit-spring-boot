package io.github.jevonsnotes.jevokit.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PageUtil} 单元测试：内存分页与分页消费（同步/异步）。
 *
 * <p>不使用 Thread.sleep：异步用例的等待语义由实现内部的 {@code CompletableFuture.join} 保证。
 */
@DisplayName("PageUtil 分页工具")
class PageUtilTest {

    private static List<Integer> range(int from, int to) {
        List<Integer> list = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            list.add(i);
        }
        return list;
    }

    // ------------------------------------------------------------------
    // pagination
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pagination：按页取数，末页自动截断")
    void paginationSlicesData() {
        // given
        List<Integer> data = range(1, 5);
        // when / then
        assertThat(PageUtil.pagination(data, 0, 2)).as("第 0 页").containsExactly(1, 2);
        assertThat(PageUtil.pagination(data, 1, 2)).as("第 1 页").containsExactly(3, 4);
        assertThat(PageUtil.pagination(data, 2, 2)).as("末页只剩 1 条").containsExactly(5);
        assertThat(PageUtil.pagination(data, 3, 2)).as("越界页返回空列表").isEmpty();
    }

    @Test
    @DisplayName("pagination：null 与空集合直通返回")
    void paginationNullOrEmptyPassthrough() {
        // given
        List<Integer> empty = Collections.emptyList();
        // when / then
        assertThat(PageUtil.pagination(null, 0, 2)).as("null 直通返回 null").isNull();
        assertThat(PageUtil.pagination(empty, 0, 2)).as("空集合直通返回").isEmpty();
    }

    // ------------------------------------------------------------------
    // consume（同步）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("consume 同步：整除/非整除页均被消费，页号从 0 递增")
    void consumeSyncSlicesAndConsumes() {
        // given
        List<Integer> data = range(1, 10);
        List<Integer> pageSizes = new ArrayList<>();
        List<Integer> pageIndexes = new ArrayList<>();
        // when：返回每页首元素，收集页大小与页号
        List<Integer> heads = PageUtil.consume(data, 3, (page, idx) -> {
            pageSizes.add(page.size());
            pageIndexes.add(idx);
            return page.get(0);
        });
        // then：10 条按 3 切成 4 页（3+3+3+1）
        assertThat(pageSizes).as("各页大小应为 3,3,3,1").containsExactly(3, 3, 3, 1);
        assertThat(pageIndexes).as("页号应从 0 连续递增").containsExactly(0, 1, 2, 3);
        assertThat(heads).as("同步结果应按页序返回").containsExactly(1, 4, 7, 10);
    }

    @Test
    @DisplayName("consume：数据量不超过页大小时整包单次消费")
    void consumeSingleBatchWhenSmall() {
        // given
        List<Integer> data = range(1, 2);
        // when
        List<Integer> sizes = PageUtil.consume(data, 5, (page, idx) -> page.size());
        // then
        assertThat(sizes).as("应只调用一次且整包传入").containsExactly(2);
    }

    @Test
    @DisplayName("consume：空数据仍单次消费（空页）")
    void consumeEmptyDataSingleCall() {
        // when
        List<Integer> sizes = PageUtil.consume(new ArrayList<Integer>(), 3, (page, idx) -> page.size());
        // then
        assertThat(sizes).as("空数据应调用一次消费空列表").containsExactly(0);
    }

    @Test
    @DisplayName("consume 指定执行器：以直通执行器运行结果等价")
    void consumeWithProvidedExecutor() {
        // given：Runnable::run 直通执行器（调用线程内联执行，结果顺序确定）
        List<Integer> data = range(1, 6);
        // when
        List<Integer> heads = PageUtil.consume(data, 2, (page, idx) -> page.get(0), Runnable::run);
        // then
        assertThat(heads).as("直通执行器结果应与同步一致").containsExactly(1, 3, 5);
    }

    // ------------------------------------------------------------------
    // consumeAsync
    // ------------------------------------------------------------------

    @Test
    @DisplayName("consumeAsync：页号集合完整无重（乱序容忍），返回值按提交顺序归集（确定序）")
    void consumeAsyncCollectsInPageOrder() {
        // given
        List<Integer> data = range(1, 10);
        List<Integer> receivedIndexes = new CopyOnWriteArrayList<>();
        // when：consumer 记录实际收到的页号（worker 线程按执行完成序写入），返回各页首元素
        List<Integer> heads = PageUtil.consumeAsync(data, 2, (page, idx) -> {
            receivedIndexes.add(idx);
            return page.get(0);
        });
        // then：BUG-1 修复保证每页恰好收到一次自己的页号——集合完整、无缺失无重复
        //（修复前实测 [1,2,3,4,4]：0 号丢失、4 号重复，任一形态都会被本断言拦截）。
        // 写入顺序取决于线程池调度（乱序合法），故只断言集合语义，不断言顺序
        assertThat(receivedIndexes).as("异步 consumer 应收到全部页号且不重不漏")
                .containsExactlyInAnyOrder(0, 1, 2, 3, 4);
        // and：返回值由 futures 按提交顺序 join 归集，是确定性有序契约
        assertThat(heads).as("异步结果应按提交顺序归集（join 序确定）").containsExactly(1, 3, 5, 7, 9);
    }

    @Test
    @DisplayName("consumeAsync：各页数据不重不漏覆盖全量")
    void consumeAsyncCoversAllData() {
        // given
        List<Integer> data = range(1, 12);
        List<Integer> seen = new CopyOnWriteArrayList<>();
        // when
        PageUtil.consumeAsync(data, 5, (page, idx) -> {
            seen.addAll(page);
            return idx;
        });
        // then
        assertThat(seen).as("所有元素应恰被消费一次").containsExactlyInAnyOrderElementsOf(range(1, 12));
    }

    @Test
    @DisplayName("consume 同步：页消费异常向上传播")
    void consumePropagatesConsumerException() {
        // given / when / then
        assertThatThrownBy(() -> PageUtil.consume(range(1, 10), 3, (page, idx) -> {
            throw new IllegalStateException("page-failed");
        }))
                .as("消费异常应向上抛出")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("page-failed");
    }

    @Test
    @DisplayName("pagination 返回原列表的视图切片（subList 语义）")
    void paginationReturnsSubListView() {
        // given
        List<Integer> data = new ArrayList<>(Arrays.asList(1, 2, 3, 4));
        // when
        List<Integer> slice = PageUtil.pagination(data, 1, 2);
        // then：subList 视图随原列表变化（锁死 subList 语义，防止误拷贝大列表）
        data.set(2, 99);
        assertThat(slice).as("分页结果应为原列表视图").containsExactly(99, 4);
    }
}
