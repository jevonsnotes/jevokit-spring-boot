package io.github.jevonsnotes.jevokit.desensitization.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.jevonsnotes.jevokit.desensitization.desensitizer.DefaultDesensitizer;
import io.github.jevonsnotes.jevokit.desensitization.desensitizer.Desensitizer;
import io.github.jevonsnotes.jevokit.desensitization.dto.DstContext;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-10 对抗性复测（终验 #6）：eng 用例已锁「二次获取 isSameAs」单线程面；
 * 本类补并发面——修复把 HashMap+手写双检锁换成 ConcurrentHashMap.computeIfAbsent，
 * 并发竞争是它的存在理由，必须以并发用例验证（单线程 isSameAs 测不出锁修复的任何东西）。
 *
 * <p>攻击假设：修复若回退为非线程安全实现，16 线程同时首次/重复取同一类，会出现
 * 多实例（HashMap 丢条目）或异常（并发扩容）。断言：零异常 + 全线程同实例 + 不同类各自稳定单例。
 */
@DisplayName("BUG-10 复测：脱敏器缓存并发竞争（对抗性）")
class DesUtilCacheAdversarialTest {

    /** 测试自持的第二实现：与 DefaultDesensitizer 并发竞争不同缓存键 */
    static class AnotherDesensitizer implements Desensitizer {
        @Override
        public String des(Object source, java.lang.reflect.Field field, String value, DstContext context) {
            return "another";
        }
    }

    @Test
    @DisplayName("16 线程 × 300 轮并发取同一类：零异常且全线程拿到同一实例")
    void concurrentGetSameClassReturnsSingleInstance() throws Exception {
        // given：先预热一个基准实例（对抗"首实例竞争"与"重复命中"两种路径混合）
        Desensitizer warm = DesUtil.getDesensitizer(DefaultDesensitizer.class);
        int threads = 16;
        int rounds = 300;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Desensitizer>> futures = new CopyOnWriteArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit((Callable<Desensitizer>) () -> {
                    start.await();
                    Desensitizer last = null;
                    for (int r = 0; r < rounds; r++) {
                        last = DesUtil.getDesensitizer(DefaultDesensitizer.class);
                    }
                    return last;
                }));
            }
            start.countDown();

            // when / then：零异常（Future.get 抛出即失败），全部结果与预热实例同一引用
            for (Future<Desensitizer> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS))
                        .as("并发获取必须返回同一缓存实例（修复回退为 HashMap 则出现多实例/异常）")
                        .isSameAs(warm);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("不同类并发竞争：各自缓存键独立且稳定单例")
    void concurrentDistinctClassesEachGetStableInstance() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Desensitizer>> futures = new CopyOnWriteArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                final boolean defaultSide = i % 2 == 0;
                futures.add(pool.submit((Callable<Desensitizer>) () -> {
                    start.await();
                    Desensitizer last = null;
                    for (int r = 0; r < 150; r++) {
                        last = defaultSide
                                ? DesUtil.getDesensitizer(DefaultDesensitizer.class)
                                : DesUtil.getDesensitizer(AnotherDesensitizer.class);
                    }
                    return last;
                }));
            }
            start.countDown();

            Set<Class<? extends Desensitizer>> resultClasses = futures.stream()
                    .map(f -> {
                        try {
                            return f.get(30, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(Desensitizer::getClass)
                    .collect(Collectors.toSet());

            // then：两侧各自稳定——恰好两个实现类，且各自内部单例（同类线程拿到的运行时类一致）
            assertThat(resultClasses)
                    .as("两个缓存键各自返回自己的实现类，无交叉污染")
                    .containsExactlyInAnyOrder(DefaultDesensitizer.class, AnotherDesensitizer.class);
            // and：同一实现类的所有获取结果 isSameAs 逐线程核对
            List<Desensitizer> defaults = collectByClass(futures, DefaultDesensitizer.class);
            List<Desensitizer> others = collectByClass(futures, AnotherDesensitizer.class);
            assertThat(defaults.stream().distinct().count())
                    .as("DefaultDesensitizer 侧应全为同一实例").isEqualTo(1);
            assertThat(others.stream().distinct().count())
                    .as("AnotherDesensitizer 侧应全为同一实例").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Desensitizer> collectByClass(List<Future<Desensitizer>> futures,
                                              Class<? extends Desensitizer> target) {
        return futures.stream()
                .map(f -> {
                    try {
                        return f.get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(d -> d.getClass() == target)
                .collect(Collectors.toList());
    }

    /** 烟雾：自持实现经缓存可用（防止 fixture 本身无效导致的假阳性） */
    @Test
    @DisplayName("fixture 有效性：AnotherDesensitizer 经缓存可正常工作")
    void anotherDesensitizerFixtureWorks() {
        Desensitizer another = DesUtil.getDesensitizer(AnotherDesensitizer.class);
        assertThat(another.des(null, null, "any",
                DstContext.builder().type(DstType.MOBILE).build()))
                .isEqualTo("another");
    }
}
