package io.github.jevonsnotes.jevokit.core.lang;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

/**
 * 进程内锁对象：作为 {@code LockUtil} 字符串锁池中按 key 复用的监视锁载体。
 * <p>作为 synchronized 的锁目标，两个时间戳不参与互斥语义，仅由锁池的 WATCH_DOG 看门狗读取——
 * updateTime 每次取锁都会刷新，超过 12 小时未活跃的锁对象将被从池中清理以防内存膨胀。
 * <p>equals/hashCode 由 @Data 基于时间戳生成，切勿据此判断"同一把锁"，锁的身份由池中的 key 决定。
 */
@Data
@AllArgsConstructor
public class LockKey{
    /** 锁对象创建时间 */
    private Date createTime;
    /** 最近一次取锁时间，供看门狗判断陈旧清理 */
    private Date updateTime;
}
