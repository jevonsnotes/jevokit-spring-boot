package io.github.jevonsnotes.jevokit.sample.api;

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
import io.github.jevonsnotes.jevokit.redis.cache.generator.extend.UserKeyGenerator;
import io.github.jevonsnotes.jevokit.redis.lock.annotation.Key;
import io.github.jevonsnotes.jevokit.redis.lock.annotation.Lock;
import io.github.jevonsnotes.jevokit.redis.lock.fallback.DefaultLockFallback;
import io.github.jevonsnotes.jevokit.sample.service.RedisService;
import io.github.jevonsnotes.jevokit.sample.vo.TestVo;

import java.util.concurrent.TimeUnit;

/**
 * redis 能力演示端点（/redis/*）：/testLock 演示 @Lock 并发锁 + DefaultLockFallback
 * 降级文案；/testCache 演示随机 TTL 缓存——E2E token 往返与撞锁降级的靶点。
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
/**
    * @Lock 并发锁演示：同名请求并发时仅一个进入，其余走 DefaultLockFallback 文案降级
    * （E2E 断言降级返回的靶点）。注解细节在方法上方声明，此处不赘。
    * @param testVo 请求体（name 参与 @Key 路由）
    * @return 正常路径回显 name；撞锁降级路径返回 fallbackStr 文案
    */
    public String testLock(@RequestBody TestVo testVo){

        return testVo.getName();
    }

    @RequestMapping("/testCache")
/**
    * 缓存三形态演示：随机 TTL / 指定 TTL（#expire= 后缀）/ 全局与用户双键空间。
    * @param testVo 请求体
    * @return 回显 name（缓存行为在 RedisService 内部观察）
    */
    public String testCache(@RequestBody TestVo testVo){
        redisService.userCache();
        redisService.userCacheWithExpire();
        redisService.globalCache();
        return testVo.getName();
    }

    @RequestMapping("/testCacheEvict")
/**
    * 缓存逐出演示：用户/全局两个键空间的 allEntries 清除。
    * @param testVo 请求体
    * @return 回显 name
    */
    public String testCacheEvict(@RequestBody TestVo testVo){
        redisService.userCacheEvict();
        redisService.globalCacheEvict();
        return testVo.getName();
    }

}
