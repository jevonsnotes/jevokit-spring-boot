package io.github.jevonsnotes.jevokit.sample.job;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.http.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import io.github.jevonsnotes.jevokit.core.util.SecureRandomUtil;

import java.util.concurrent.CountDownLatch;

 /**
 * 定时任务示例占位：原定时压测逻辑已注释保留，现仅作为 @Scheduled 装配形态的
 * 演示载体（默认不启用任何调度）。
 */
@Slf4j
@Component
public class TestJob {
//    @Autowired
//    private RestTemplate restTemplate;
//    @Scheduled(cron = "0/30 * * * * ?")
//    public void testJob() {
//        log.info("test job print " + UUID.fastUUID().toString(true));
//        int loop = 1;
//        final CountDownLatch countDownLatch = ThreadUtil.newCountDownLatch(loop);
//        for (int i = 0; i < loop; i++) {
//            ThreadUtil.execute(new Runnable() {
//
//                @Override
//                public void run() {
//                    try {
//                        log.info("multi thread :" + SecureRandomUtil.randomString(20));
//
//                        ResponseEntity<String> forEntity = restTemplate.getForEntity("http://localhost:8080/test/xss/form?content=aaaa", String.class);
//                        log.info("请求返回:"+ forEntity.getBody());
//                        ResponseEntity<String> forEntity2 = restTemplate.getForEntity("http://localhost:8080/test/xss/form?content=aaaa", String.class);
//                        log.info("请求返回:"+ forEntity2.getBody());
//                        String test2 = HttpUtil.get("http://localhost:8080/test/xss/form?content=aaaa");
//                        log.info("请求返回2:"+ test2);
//                    } finally {
//                        countDownLatch.countDown();
//                    }
//                }
//            });
//        }
//        try {
//            countDownLatch.await();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
}
