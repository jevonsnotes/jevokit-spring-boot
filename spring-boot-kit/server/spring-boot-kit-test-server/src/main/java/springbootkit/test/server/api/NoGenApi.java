package springbootkit.test.server.api;

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
import springbootkit.core.util.ExceptionUtil;
import springbootkit.core.util.LoopUtil;
import springbootkit.nogen.helper.BzNoHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @Description TODO
 * @Date 2023/3/10 15:07
 * @Version 0.0.1
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
    public String test(@RequestParam("bizNo") String bizNo,@RequestParam("loop") int loop) {
//        jdbcTemplate.update("""
//                INSERT INTO biz_no (biz_tag, max_id, step, mode, threhold, description) VALUES ('%s', 0, 1000, 1, '70.00', '单号获取测试demo');
//                """.formatted(bizNo));
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
    public String testRecycle(@RequestParam("bizNo") String bizNo,@RequestParam("loop") int loop) {
//        jdbcTemplate.update("""
//                INSERT INTO biz_no (biz_tag, max_id, step, mode, threhold, description) VALUES ('%s', 0, 1000, 1, '70.00', '单号获取测试demo');
//                """.formatted(bizNo));
        long start = System.currentTimeMillis();

        try {
            Set<String> nos = new HashSet<>();
            for(int i=0;i<loop;i++) {
                nos.add( RandomUtil.randomString(16));
            }

            // 添加模拟数据
            List<Boolean> list = nos.stream().parallel().map(no -> bzNoHelper.recycle(bizNo, no)).toList();
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
