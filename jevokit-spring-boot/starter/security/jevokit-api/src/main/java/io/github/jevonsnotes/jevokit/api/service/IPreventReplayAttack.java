package io.github.jevonsnotes.jevokit.api.service;

/**
 * 接口防重放校验 SPI：JSecure 协议按 nonce + timestamp 判定请求是否重放/过期。
 * 使用方式：终端配置开启 prevent-replay 后，解密适配器对每个请求调用 {@link #checkReplay(String, String)}；
 * 默认实现为进程内存储（{@code DefaultPreventReplayAttack}），多实例部署时请提供自定义 bean
 * （如 Redis 集中存储）替换——@ConditionalOnMissingBean 注册，业务 bean 优先。
 * <p>关键约束：返回 true 表示"是重放/已失效"应拒绝（注意语义方向），timestamp 为秒级字符串。
 */
public interface IPreventReplayAttack {
    /**
     * 接口防重放校验
     * @param timestamp 秒
     * @param nonce 随机码
     * @return 校验结果
     */
    boolean checkReplay(String nonce,String timestamp);
}
