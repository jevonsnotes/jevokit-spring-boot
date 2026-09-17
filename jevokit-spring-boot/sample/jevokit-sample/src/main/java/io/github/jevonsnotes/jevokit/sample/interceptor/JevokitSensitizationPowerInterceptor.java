package io.github.jevonsnotes.jevokit.sample.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.SensitizationPowerInterceptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 脱敏权限拦截器示例：实现 SensitizationPowerInterceptor，按敏感级 + 权限键决定
 * 当前调用是否可见明文。演示实现刻意用计数器取模（每 3 次放行 1 次）模拟动态权限，
 * 便于人工观察"权限变化→可见性变化"的效果，无对应自动化用例。
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
