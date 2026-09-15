package springbootkit.sample.api;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.thread.ThreadUtil;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springbootkit.redis.cache.generator.extend.UserKeyGenerator;
import springbootkit.redis.lock.annotation.Key;
import springbootkit.redis.lock.annotation.Lock;
import springbootkit.redis.lock.fallback.DefaultLockFallback;
import springbootkit.sample.service.RedisService;
import springbootkit.sample.vo.TestVo;

import java.util.concurrent.TimeUnit;

/**
 * @Description TODO
 * @Date 2022/7/21 17:35
 * @Version 0.0.1
 */
@RestController
@RequestMapping("/redis")
@RequiredArgsConstructor
public class RedisApi {
    final RedissonClient client;
    final RedisService redisService;
    /**
     * 接口并发锁，在未请求完成之前，阻断并返回个性信息
     * @param testVo
     * @return
     */
    @Lock(namespace = "testLock",fallback = DefaultLockFallback.class,key = @Key(route = "name"))
    @RequestMapping("/testLock")
    public String testLock(@RequestBody TestVo testVo){

        return testVo.getName();
    }

    @RequestMapping("/testCache")
    public String testCache(@RequestBody TestVo testVo){
        redisService.userCache();
        redisService.userCacheWithExpire();
        redisService.globalCache();
        return testVo.getName();
    }

    @RequestMapping("/testCacheEvict")
    public String testCacheEvict(@RequestBody TestVo testVo){
        redisService.userCacheEvict();
        redisService.globalCacheEvict();
        return testVo.getName();
    }

}
