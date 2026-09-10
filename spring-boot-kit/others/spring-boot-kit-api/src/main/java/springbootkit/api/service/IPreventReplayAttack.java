package springbootkit.api.service;

/**
 * @Description TODO
 * @Date 2025/4/17
 * @Version 0.0.1
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
