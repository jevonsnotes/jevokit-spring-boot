package io.github.jevonsnotes.jevokit.redis.lock.aspect;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import io.github.jevonsnotes.jevokit.redis.lock.annotation.Key;
import io.github.jevonsnotes.jevokit.redis.lock.annotation.Lock;
import io.github.jevonsnotes.jevokit.redis.util.RedisLock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 并发锁
 * 用于解决并发导致的脏数据问题
 * 使用样例：
 * 在spring代理方法上使用@Lock注解标注启用并发锁
 */
@Aspect
@Slf4j
public class LockAspect {
    @Value("${spring.application.name:app}")
    private String service;

    private static final String KEY_PATTERN = "lock:%s:%s:%s";

    /**
    * 切点：@Lock 注解标注的方法（切点表达式含包名，包名迁移时须同步——rename 教训项）。
    */
    @Pointcut("@annotation(io.github.jevonsnotes.jevokit.redis.lock.annotation.Lock)")
    public void Lock() {
    }

    /**
    * @Lock 环绕：按 value/route 提键，键为空白直接放行（锁失效而非报错）；
    * 拿锁失败时按优先级降级——fallback 类 > fallbackWithException 抛出 > 仅 warn 日志（返回 null）。
    * @param joinPoint 连接点
    * @return 业务原返回值；或降级路径产物（fallback 结果 / null）
    * @throws Throwable 业务异常，或降级配置的 fallbackWithException 异常实例
    */
    @Around("Lock()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Lock lock = signature.getMethod().getAnnotation(Lock.class);
        Object[] args = joinPoint.getArgs();

        String key = getKey(lock, args);
        Object o = null;
        //key值非空时，并发锁才有效
        if (StrUtil.isNotBlank(key)) {
            //key demo = lock:service:namespace:key
            String finalKey = String.format(KEY_PATTERN, service, getNamespace(signature), key);
            if (RedisLock.tryLock(finalKey, lock.waitTime(), lock.timeout(), TimeUnit.SECONDS)) {
                try {
                    o = joinPoint.proceed();
                } finally {
                    RedisLock.unlock(finalKey);
                }
            } else {
                if (isFallback(lock)) {
                    o = lock.fallback()[0].newInstance().fallback(signature, args);
                } else {
                    String fallbackStr = getFallbackStr(lock, key);
                    if (isFallbackWithException(lock)) {
                        log.error(fallbackStr);
                        throw ReflectUtil.newInstance(lock.fallbackWithException()[0], fallbackStr);
                    } else {
                        log.warn(fallbackStr);
                    }
                }
            }
        } else {
            o = joinPoint.proceed();
        }

        return o;
    }

    /**
    * 组装降级文案：@Key.print=true 时追加键值便于排查。
    */
    private String getFallbackStr(Lock lock,String key) {
        return lock.key().print() ? new StrBuilder().append(lock.fallbackStr()).append("：").append(key).toString() : lock.fallbackStr();
    }
    /**
     * 是否加锁失败抛异常
     *
     * @param Lock
     * @return
     */
    private boolean isFallbackWithException(Lock Lock) {
        return Lock.fallbackWithException() != null && Lock.fallbackWithException().length > 0;
    }

    /**
     * 是否加锁失败自定义处理返回
     *
     * @param Lock
     * @return
     */
    private boolean isFallback(Lock Lock) {
        return Lock.fallback() != null && Lock.fallback().length > 0;
    }

    /**
    * 取锁命名空间：注解显式声明优先，否则以 类名.方法名 兜底。
    */
    private String getNamespace(MethodSignature signature) {
        Lock Lock = signature.getMethod().getAnnotation(Lock.class);
        if (Lock == null || StrUtil.isBlank(Lock.namespace())) {
            final String methodName = signature.getMethod().getName();
            final String className = signature.getMethod().getDeclaringClass().getName();
            return className + "#" + methodName;
        } else {
            return Lock.namespace();
        }

    }

    /**
    * 提锁键：value 直取 > paramAt+route 属性路由；解析失败记日志返回 null（上层按空白放行）。
    */
    private String getKey(Lock lock, Object[] args) {
        if (lock != null) {
            if (StrUtil.isNotBlank(lock.key().value())) {
                return lock.key().value();
            } else if (args != null && args.length > 0) {
                StrBuilder sb = new StrBuilder();
                Key key = lock.key();
                for (int p : key.paramAt()) {
                    if (args[p] != null) {
                        if(ArrayUtil.isNotEmpty(key.route())){
                            for (String r : key.route()) {
                                String valueByRoute = getValueByRoute(r, args[p]);
                                if (StrUtil.isNotBlank(valueByRoute)) {
                                    sb.append(valueByRoute).append("|");
                                }
                            }
                        } else {
                            String pStr = args[p].toString();
                            if (StrUtil.isNotBlank(pStr)) {
                                sb.append(pStr).append("|");
                            }
                        }
                    }

                }
                if (!sb.isEmpty()) {
                    return sb.delTo(sb.length() - 1).toStringAndReset();
                }
            }
        }
        return null;
    }


    /**
    * 沿 a.b.c 属性路径反射取值（逐段 getter）。
    */
    private String getValueByRoute(String route, Object o) {
        route = StrUtil.trimToEmpty(route);
        String[] properties = route.split("\\.");
        Object temp = o;
        for (String p : properties) {
            Object fieldValue = ReflectUtil.getFieldValue(temp, p);
            if (fieldValue != null) {
                temp = fieldValue;
            } else {
                return null;
            }
        }
        return temp.toString();
    }


}
