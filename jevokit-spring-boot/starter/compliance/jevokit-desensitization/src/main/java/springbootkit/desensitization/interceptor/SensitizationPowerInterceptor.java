package springbootkit.desensitization.interceptor;

import springbootkit.desensitization.consts.DstLevel;

import java.util.List;

/**
 * @Description TODO
 * @Date 2024/6/7 10:27
 * @Version 0.0.1
 */
public interface SensitizationPowerInterceptor {

    boolean check(DstLevel level,List<String> powerKey);
}
