package io.github.jevonsnotes.jevokit.sample.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.SensitizationPowerInterceptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Description TODO
 * @Date 2024/6/7 11:21
 * @Version 0.0.1
 */
@Slf4j
@Component
public class JevokitSensitizationPowerInterceptor implements SensitizationPowerInterceptor {
    private AtomicInteger counter = new AtomicInteger(0);

    @Override
    public boolean check(DstLevel level, List<String> powerKey) {
        log.info("识别到权限 level {} , key {}",level,powerKey);
        return counter.incrementAndGet() % 3 ==0;
    }
}
