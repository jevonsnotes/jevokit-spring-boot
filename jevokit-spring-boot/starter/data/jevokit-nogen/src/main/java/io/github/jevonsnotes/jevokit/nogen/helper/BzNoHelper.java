package io.github.jevonsnotes.jevokit.nogen.helper;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.util.Assert;
import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;
import io.github.jevonsnotes.jevokit.core.util.LockUtil;
import io.github.jevonsnotes.jevokit.nogen.config.JevokitNoGenProperties;
import io.github.jevonsnotes.jevokit.nogen.consts.BzNoMode;
import io.github.jevonsnotes.jevokit.nogen.dao.NoGenDao;
import io.github.jevonsnotes.jevokit.nogen.po.BizNo;
import io.github.jevonsnotes.jevokit.nogen.vo.BizNoVo;
import io.github.jevonsnotes.jevokit.redis.util.RedisLock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 单号工具类
 */
@Slf4j
@RequiredArgsConstructor
public class BzNoHelper {
    private final JevokitNoGenProperties properties;

    public static final int CORES = Runtime.getRuntime().availableProcessors();
    private static final ThreadPoolExecutor executor = ThreadUtil.newExecutor(CORES, CORES, Integer.MAX_VALUE);
    private static final String R_PREFIX = "BizNoPool:%s:";
    private static final String PATTERN_LOCK = R_PREFIX + "lock";
    private static final String PATTERN_MAX_ID = R_PREFIX + "max_id";
    private static final String PATTERN_BIZ_NO = R_PREFIX + "biz_no";
    private static final String PATTERN_RECYCLE = R_PREFIX + "recycle";
    private static final Map<String, String> KEY_CACHE_LOCK = new ConcurrentHashMap<>();
    private static final Map<String, String> KEY_CACHE_MAX_ID = new ConcurrentHashMap<>();
    private static final Map<String, String> KEY_CACHE_BIZ_NO = new ConcurrentHashMap<>();
    private static final Map<String, String> KEY_CACHE_RECYCLE = new ConcurrentHashMap<>();
    private final RedissonClient client;
    private final Map<String, Long> CACHE_MAX_ID = new HashMap<>();
    private final TimedCache<String, BizNo> CACHE_CONFIG = CacheUtil.newTimedCache(10*60 * 1000);
    private final Map<String, Boolean> CACHE_INIT_STATUS = new ConcurrentHashMap<>();

    private final NoGenDao dao;


    /**
     * 获取单号（左填充）
     *
     * @param bizTag
     * @param length
     * @param pad
     * @return
     */
    public String getNoLPad(String bizTag, int length, String pad) {
        return getNoPad(bizTag, length, pad, true);
    }

    /**
     * 获取单号（右填充）
     *
     * @param bizTag
     * @param length
     * @param pad
     * @return
     */
    public String getNoRPad(String bizTag, int length, String pad) {
        return getNoPad(bizTag, length, pad, false);
    }

    /**
     * 获取单号（填充序列）
     * 不做长度校验，因为会导致号码浪费。消费方自行把控长度
     * @param bizTag
     * @param length
     * @param pad
     * @param isLeft
     * @return
     */
    public String getNoPad(String bizTag, int length, String pad, boolean isLeft) {

        String bizNo = String.valueOf(getNo(bizTag));
        int srcLength = bizNo.length();

        for (int i = 0; i < length - srcLength; i++) {
            if (isLeft) {
                bizNo = pad + bizNo;
            } else {
                bizNo = bizNo + pad;
            }
        }
        return bizNo;
    }

/**
    * 取单号：RECYCLE 模式先从回收池取，取不到/DEFAULT 模式走 Redis 自增发号（异步落 DB）。
    * @param bizTag 业务标签
    * @return 单号（回收池命中为历史号，否则为新自增号）
    */
    public String getNo(String bizTag) {
        String no = getByMode(bizTag);
        return StrUtil.isEmpty(no) ? String.valueOf(noGen(bizTag)) : no;
    }

    private String getByMode(String bizTag){
        String no = null;
        BizNo config = getConfig(bizTag);
        if(BzNoMode.RECYCLE.getCode().equals(config.getMode())){
            no = getNoFromRecyclePool(bizTag);
        }
        return no;
    }

    /**
     * 基于自增Long数据类型实现,异步同步数据库 40000tps (6c,8g,200线程测试)
     * 10000 | 100000 | 1000000 | 10000000
     * 200ms | 2.3s   | 23s     | 3.5m
     * 优化序列读写、内存消耗
     * @param bizTag
     * @return
     */
    private Long noGen(String bizTag) {
        RAtomicLong atomicLong = getRNo(bizTag);
        long no;
        if (!getInitStatus(bizTag)){
            CACHE_INIT_STATUS.put(bizTag,true);
            if((no = atomicLong.get()) == 0 && no != getMaxId(bizTag)) {
                if (lock(bizTag)) {
                    try {
                        if ((no = atomicLong.get()) == 0 && no != getMaxId(bizTag)) {
                            BizNo bizNo = dao.getBizNo(bizTag);
                            atomicLong.compareAndSet(no, bizNo.getMaxId());
                        }
                    } finally {
                        unlock(bizTag);
                    }
                }
            }
            return noGen(bizTag);
        } else {
            no = atomicLong.getAndIncrement();
            dbAsync(bizTag,no);
        }

        return no;
    }

/**
    * 回收单号入池（Redis 阻塞队列 offer）。
    * @param bizTag 业务标签
    * @param bizNo 单号
    * @return true=入池成功；false=入队失败（容量/状态异常，号段丢失需人工核）
    */
    public boolean recycle(String bizTag, String bizNo){
        return client.getBlockingQueue(getRecycleKey(bizTag)).offer(bizNo);
//        return client.getSet(getRecycleKey(bizTag)).add(bizNo);
    }

    /**
     * 从回收池获取单号
     * @param bizTag
     * @return
     */
    public String getNoFromRecyclePool(String bizTag){
//        String key = getRecycleKey(bizTag);
//        RSet<String> set = client.getSet(key);
//        RLock lock = set.getLock(key);
//        Iterator<String> it = set.iterator(1);
//        while(it.hasNext()){
//            lock.lock(5*60, TimeUnit.SECONDS);
//            try {
//                String next = it.next();
//                it.remove();
//                return next;
//            } finally {
//                lock.unlock();
//            }
//        }

//        return null;
        RBlockingQueue<String> blockingQueue = client.getBlockingQueue(getRecycleKey(bizTag));
        return blockingQueue.poll();
    }

    /**
     * 获取初始化状态
     * @param bizTag
     * @return
     */
    private boolean getInitStatus(String bizTag){
//        Boolean status = CACHE_INIT_STATUS.get(bizTag);
//        if(status == null){
//
//            synchronized (LockUtil.getKey(bizTag)){
//
//                if((status = CACHE_INIT_STATUS.get(bizTag)) == null){
//                    CACHE_INIT_STATUS.put(bizTag,status = false);
//                }
//            }
//        }
        return CACHE_INIT_STATUS.computeIfAbsent(bizTag, s -> false);
    }

/**
    * 查全部单号配置。
    * @return 配置行列表
    */
    public List<BizNo> getTags(){
        return dao.getTags();
    }
/**
    * 查全部配置并转视图对象。
    * @return 配置视图列表
    */
    public List<BizNoVo> getTagsVo(){
        List<BizNo> tags = getTags();
        return CollectionUtil.extract(tags, new Func1<BizNo, BizNoVo>() {

            @Override
            public BizNoVo call(BizNo parameter) throws Exception {
                return BeanUtil.toBean(parameter,BizNoVo.class);
            }
        });
    }
/**
    * 取当前最大号（本地缓存 → Redis → DB 三级，双检防击穿）。
    * @param bizTag 业务标签
    * @return 当前最大号
    */
    public long getMaxId(String bizTag) {
        Long maxId = CACHE_MAX_ID.get(bizTag);
        if(maxId == null ){
            synchronized (LockUtil.getKey(bizTag)){
                if((maxId = CACHE_MAX_ID.get(bizTag))== null){
                    RAtomicLong atomicLong = getRMaxId(bizTag);
                    maxId = atomicLong.get();
                    if (maxId == 0) {
                        BizNo bizNo = dao.getBizNo(bizTag);
                        if (!maxId.equals(bizNo.getMaxId())) {
                            atomicLong.compareAndSet(maxId, bizNo.getMaxId());
                            maxId = atomicLong.get();
                        }
                    }
                    CACHE_MAX_ID.put(bizTag,maxId);
                }
            }
        }
        return maxId;
    }

    /**
     * 获取静态配置
     * 注意：此处数据作为配置用，采用不走redis缓存的方式，否则会严重影响吞吐量
     * @param bizTag
     * @return
     */

    public BizNo getConfig(String bizTag) {
        BizNo bizNo = CACHE_CONFIG.get(bizTag);
        if(bizNo == null){
            synchronized (LockUtil.getKey(bizTag)){
                if((bizNo = CACHE_CONFIG.get(bizTag)) == null){
                    CACHE_CONFIG.put(bizTag,bizNo = dao.getBizNo(bizTag));
                }
            }
        }

        return bizNo;
    }

    /**
     * 缓存清理钩子
     */
    public void clearHook(){
        CACHE_CONFIG.clear();
        CACHE_INIT_STATUS.clear();
    }
    /**
     * 异步同步数据库
     * @param bizTag
     */
    private void dbAsync(String bizTag,long currentNo) {
        BizNo config = _this().getConfig(bizTag);
        Assert.notNull(config, "业务号序列未配置");
        long maxId = getMaxId(bizTag);
        if (currentNo >= maxId
                || (currentNo % config.getStep() >= config.getStep() * (config.getThrehold().doubleValue() / 100) && Math.abs(currentNo - maxId) <= config.getStep())) {
            Runnable task = () -> {
                if (lock(bizTag)) {
                    try {
                        long maxId1;
                        long no1 = getRNo(bizTag).get();
                        while (no1 >= (maxId1 = getMaxId(bizTag))
                                || (no1 % config.getStep() >= config.getStep() * (config.getThrehold().doubleValue() / 100) && Math.abs(no1 - maxId1) <= config.getStep())) {
                            dbSync(config.getBizTag());
                        }
                    } finally {
                        unlock(bizTag);
                    }
                }
            };

            if (currentNo < getMaxId(bizTag)) {
                executor.execute(task);
            } else {
                task.run();
            }

        }
    }

    /**
     * 同步数据库
     *
     * @param bizTag
     */
    private void dbSync(String bizTag) {
        BizNo bizNo = dao.getBizNo(bizTag);
        bizNo.setMaxId(bizNo.getMaxId() + bizNo.getStep());
        dao.updateSelectiveByPrimaryKey(bizNo);
        RAtomicLong atomicLong = getRMaxId(bizTag);
        atomicLong.set(bizNo.getMaxId());
        CACHE_MAX_ID.put(bizTag,bizNo.getMaxId());
    }

    private RAtomicLong getRNo(String bizTag) {
        return client.getAtomicLong(getBizNoKey(bizTag));
    }

    private RAtomicLong getRMaxId(String bizTag) {
        return client.getAtomicLong(getMaxIdKey(bizTag));
    }

    private boolean lock(String bizTag) {
        return RedisLock.tryLock(getLockKey(bizTag), -1);
    }

    private void unlock(String bizTag) {
        RedisLock.unlock(getLockKey(bizTag));
    }

    private String getLockKey(String bizTag) {
        return KEY_CACHE_LOCK.computeIfAbsent(bizTag, t -> String.format(BzNoHelper.PATTERN_LOCK, t));
    }
    private String getMaxIdKey(String bizTag) {
        return KEY_CACHE_MAX_ID.computeIfAbsent(bizTag, t -> String.format(BzNoHelper.PATTERN_MAX_ID, t));
    }
    private String getBizNoKey(String bizTag) {
        return KEY_CACHE_BIZ_NO.computeIfAbsent(bizTag, t -> String.format(BzNoHelper.PATTERN_BIZ_NO, t));
    }
    private String getRecycleKey(String bizTag) {
        return KEY_CACHE_RECYCLE.computeIfAbsent(bizTag, t -> String.format(BzNoHelper.PATTERN_RECYCLE, t));
    }

    private BzNoHelper _this(){
        return SpringUtil.getBean(BzNoHelper.class);
    }
}
