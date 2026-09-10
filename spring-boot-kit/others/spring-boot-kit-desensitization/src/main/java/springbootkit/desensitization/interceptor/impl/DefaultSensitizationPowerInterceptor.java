package springbootkit.desensitization.interceptor.impl;

import springbootkit.desensitization.consts.DstLevel;
import springbootkit.desensitization.interceptor.SensitizationPowerInterceptor;

import java.util.List;

/**
 * @Description TODO
 * @Date 2024/6/7 10:30
 * @Version 0.0.1
 */
public class DefaultSensitizationPowerInterceptor implements SensitizationPowerInterceptor {

    @Override
    public boolean check(DstLevel level, List<String> powerKey) {
        return false;
    }
}
