package io.github.jevonsnotes.jevokit.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-1（{@code PageUtil.consumeAsync} 页号竞态）对抗性复测：独立于开发的 PageUtilTest，
 * 以「确定性必杀」+「多轮加压」两条路径验证页号快照修复，不采信开发自述与单轮绿。
 *
 * <p>设计说明：
 * <ol>
 *   <li><b>确定性变体</b>：提交后延迟执行的执行器，使主循环的全部 {@code page.value++}
 *       先于任何任务执行。CompletableFuture 提交自带的 happens-before 边保证工作线程
 *       看到的是「已递增完」的终值——修复前该变体 <b>100%</b> 复现页号错乱（竞态是
 *       「必然错」而非「偶发错」）；修复后 supplier 读提交时 final 快照，序列恒为 0..N。</li>
 *   <li><b>加压变体</b>：50 页（更小 pageSize、更快自增节奏）× 150 轮迭代，逐轮
 *       containsExactly 锁死确定序。竞态修复的验证靠压力暴露，单次绿不算数。</li>
 *   <li><b>一致性校验</b>：consumer 内联断言「页号 × 切片首元素」匹配
 *       （{@code 首元素 == 页号 * pageSize + 1}）——若快照与切片来自不同迭代的
 *       {@code page.value}，此处立即暴露。</li>
 *   <li><b>同步回归</b>：无执行器路径行为应与修复前完全一致。</li>
 * </ol>
 */
@DisplayName("BUG-1 复测：consumeAsync 页号快照（对抗性验证）")
class PageUtilRaceVerificationTest {

    private static List<Integer> range(int from, int to) {
        List<Integer> list = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            list.add(i);
        }
        return list;
    }

    @Test
    @DisplayName("确定性复现：延迟执行器下主循环先跑完全部自增，页号仍须为提交时快照")
    void delayedExecutionStillReceivesSubmissionTimePageIndex() {
        // given：10 条 / 每页 2 条 = 5 页；执行器把每个任务推迟 30ms，
        // 主循环微秒级完成全部提交与 page.value++ —— 竞态窗口被拉到最宽
        List<Integer> data = range(1, 10);
        int pageSize = 2;
        List<String> mismatches = new CopyOnWriteArrayList<>();
        Executor delayed = task -> {
            Thread worker = new Thread(() -> {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                task.run();
            }, "delayed-page-worker");
            worker.setDaemon(true);
            worker.start();
        };

        // when：延迟执行下分页消费，返回收到的页号
        List<Integer> pageIndexes = PageUtil.consume(data, pageSize, (page, idx) -> {
            int expectedHead = idx * pageSize + 1;
            if (page.isEmpty() || page.get(0) != expectedHead) {
                mismatches.add("页号=" + idx + " 的切片首元素="
                        + (page.isEmpty() ? "<空>" : page.get(0)) + "（期望 " + expectedHead + "）");
            }
            return idx;
        }, delayed);

        // then：
        //   1) 快照与切片内容一致（错位即快照语义失效）
        //   2) consumer 收到的页号集合 = {0,1,2,3,4}（每个任务收到自己的提交时快照）
        // 注：pageIndexes 由工作线程按实际执行顺序写入，顺序非确定性；
        //     修复前所有任务都读到终值 4，集合为 {4,4,4,4,4}，本断言必挂
        assertThat(mismatches)
                .as("页号快照与切片内容应逐页一致（错位即快照语义失效）")
                .isEmpty();
        assertThat(pageIndexes)
                .as("延迟执行下 consumer 应收到提交时快照 0..4（集合，顺序由调度决定）")
                .containsExactlyInAnyOrder(0, 1, 2, 3, 4);
    }

    @Test
    @DisplayName("加压：50 页 × 150 轮，页号逐轮 containsExactly 确定序，各页切片不重不漏")
    void stressFiftyPagesManyRoundsExactSequence() {
        // given：100 条 / 每页 2 条 = 50 页（自增节奏比常规 5 页用例快一个量级）
        int pageSize = 2;
        int elements = 100;
        int rounds = 150;
        List<Integer> data = range(1, elements);
        List<Integer> expectedIndexes = IntStream.rangeClosed(0, elements / pageSize - 1)
                .boxed().toList();
        List<Integer> expectedHeads = IntStream.iterate(1, v -> v + pageSize)
                .limit(elements / pageSize).boxed().toList();
        // 杀伤力自证：统计运行在非主线程的任务数——若全部被 CallerRunsPolicy 内联到主线程，
        // 快照读与共享读不可区分，本压力用例对 BUG-1 就没有检出能力（阳性对照原理）
        String mainThread = Thread.currentThread().getName();
        AtomicInteger asyncTaskCount = new AtomicInteger();

        for (int round = 1; round <= rounds; round++) {
            // when
            List<Integer> seenIndexes = new CopyOnWriteArrayList<>();
            List<Integer> heads = new CopyOnWriteArrayList<>();
            List<String> mismatches = new CopyOnWriteArrayList<>();
            List<Integer> result = PageUtil.consumeAsync(data, pageSize, (page, idx) -> {
                if (!Thread.currentThread().getName().equals(mainThread)) {
                    asyncTaskCount.incrementAndGet();
                }
                seenIndexes.add(idx);
                int expectedHead = idx * pageSize + 1;
                if (page.isEmpty() || page.get(0) != expectedHead) {
                    mismatches.add("页号=" + idx + " 的切片首元素="
                            + (page.isEmpty() ? "<空>" : page.get(0)) + "（期望 " + expectedHead + "）");
                }
                return page.get(0);
            });
            // then：
            //   1) 逐任务不变式：页号 ↔ 切片内容匹配（快照语义，BUG-1 核心）
            //   2) seenIndexes 是工作线程按实际执行完成顺序写入的，顺序非契约；
            //      但其作为多重集合必须是 {0..49} 各一次——修复前出现 {4×k, 5×m...} 错值/重复，
            //      anyOrder 断言同样必挂
            //   3) result 顺序是契约（futures 提交序 join，有序流 collect 保序）→ containsExactly
            assertThat(mismatches)
                    .as("第 %d/%d 轮：页号与切片内容应逐页一致", round, rounds)
                    .isEmpty();
            assertThat(seenIndexes)
                    .as("第 %d/%d 轮：consumer 收到的页号应各页恰一次", round, rounds)
                    .containsExactlyInAnyOrderElementsOf(expectedIndexes);
            assertThat(result)
                    .as("第 %d/%d 轮：归集结果应按页序、无缺漏", round, rounds)
                    .containsExactlyElementsOf(expectedHeads);
        }
        // 杀伤力自证：必须有足量任务真跑在池线程上，快照语义才被实际检验
        assertThat(asyncTaskCount.get())
                .as("压力用例需有相当比例任务运行在池线程（否则对竞态无检出能力）")
                .isGreaterThan(rounds * 2);
    }

    @Test
    @DisplayName("回归：同步路径（无执行器）行为与修复前一致")
    void syncPathRegressionUnchanged() {
        // given / when：10 条按 3 切 4 页，同步消费收集页号与首元素
        List<Integer> pageIndexes = new ArrayList<>();
        List<Integer> heads = PageUtil.consume(range(1, 10), 3, (page, idx) -> {
            pageIndexes.add(idx);
            return page.get(0);
        });
        // then
        assertThat(pageIndexes).as("同步路径页号应为 0..3").containsExactly(0, 1, 2, 3);
        assertThat(heads).as("同步路径结果应按页序").containsExactly(1, 4, 7, 10);

        // and：数据量不超过页大小时整包单次消费、页号 0（else 分支直读 holder，不受快照改动影响）
        List<Integer> singleBatchIndexes = new ArrayList<>();
        PageUtil.consume(range(1, 3), 10, (page, idx) -> {
            singleBatchIndexes.add(idx);
            return null;
        });
        assertThat(singleBatchIndexes).as("小数据单批路径页号应为 0").containsExactly(0);
    }
}
