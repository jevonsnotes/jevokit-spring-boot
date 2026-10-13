package io.github.jevonsnotes.jevokit.sample.api;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.stream.CollectorUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.github.jevonsnotes.jevokit.core.util.ExceptionUtil;
import io.github.jevonsnotes.jevokit.core.util.LoopUtil;
import io.github.jevonsnotes.jevokit.nogen.helper.BzNoHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

 /**
 * 单号生成演示端点（/nogen/*）：经 BzNoHelper 发号与回收（RECYCLE 模式），
 * E2E 断言单号递增与回收复用的靶点。
 */
@Slf4j
@RestController
@RequestMapping("nogen")
@RequiredArgsConstructor
public class NoGenApi {
    private final BzNoHelper bzNoHelper;
    private final JdbcTemplate jdbcTemplate;
    ExecutorService executor = ThreadUtil.newExecutor(200, 200, Integer.MAX_VALUE);

    @RequestMapping("/test")
    /**
    * 发号压测端点：loop 次并发取号计时（E2E 发号递增断言靶点）。
    * @param bizNo 业务标签
    * @param loop 循环次数
    * @return 计时结果文本
    */
    public String test(@RequestParam("bizNo") String bizNo,@RequestParam("loop") int loop) {
        long start = System.currentTimeMillis();
        try {
            Supplier<String> task = () -> bzNoHelper.getNoLPad(bizNo,8,"0");

            List<CompletableFuture<String>> futures = LoopUtil.supplyToList(loop, () -> CompletableFuture.supplyAsync(task, executor));

            Set<String> nos = futures.stream().parallel().map(CompletableFuture::join)
//                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toSet());
//            log.info("获取到新单号:{}", JSON.toJSONString(nos));
            Assert.isTrue(nos.size() == loop,()-> new RuntimeException("获取单号异常，存在重复值"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            long end = System.currentTimeMillis();
            log.info("获取单号耗时：{}/s", (end - start)/1000d);
        }
        return "success";
    }
    @RequestMapping("/testRecycle")
    /**
    * 回收模式压测端点：发号后回收再复用。
    * @param bizNo 业务标签
    * @param loop 循环次数
    * @return 计时结果文本
    */
    public String testRecycle(@RequestParam("bizNo") String bizNo,@RequestParam("loop") int loop) {
        long start = System.currentTimeMillis();

        try {
            Set<String> nos = new HashSet<>();
            for(int i=0;i<loop;i++) {
                nos.add( RandomUtil.randomString(16));
            }

            // 添加模拟数据
            List<Boolean> list = nos.stream().parallel().map(no -> bzNoHelper.recycle(bizNo, no)).collect(Collectors.toList());
            Assert.isTrue(list.size() == nos.size());

            // 并发获取数据
            Supplier<String> task = () -> bzNoHelper.getNoFromRecyclePool(bizNo);

            List<CompletableFuture<String>> futures = LoopUtil.supplyToList( nos.size(), () -> CompletableFuture.supplyAsync(task, executor));
            // 收集队列数据
            Set<String> dbNos = futures.stream().map(CompletableFuture::join)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toSet());
//            log.info("获取到新单号:{}", JSON.toJSONString(nos));
            Assert.isTrue(nos.size() == dbNos.size(),()-> new RuntimeException("获取单号异常，存在重复值"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            long end = System.currentTimeMillis();
            log.info("获取单号耗时：{}/s", (end - start)/1000d);
        }
        return "success";
    }

}
