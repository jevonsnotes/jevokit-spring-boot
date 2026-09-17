package io.github.jevonsnotes.jevokit.desensitization.interceptor.impl;

import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.SensitizationPowerInterceptor;

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
