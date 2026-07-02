package org.allbluehu.develop.lock.support;

import java.lang.reflect.Method;
import java.util.List;

import org.allbluehu.develop.lock.annotation.DistributedLock;
import org.allbluehu.develop.lock.annotation.FailStrategy;
import org.allbluehu.develop.lock.exception.LockAcquireFailureException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.util.ReflectionUtils;

/**
 * 获取锁失败后的统一处理：THROW / RETURN_NULL / FAILBACK。
 *
 * <p>FAILBACK 通过反射调用同对象上、与原方法参数列表相同的兜底方法。
 */
public class LockFailureHandler {

    /**
     * @return 兜底/降级后的返回值；THROW 策略下不会返回而是抛异常
     */
    public Object handle(ProceedingJoinPoint pjp, Method method, DistributedLock annotation,
                         List<String> keys) throws Throwable {
        FailStrategy strategy = annotation.failStrategy();
        switch (strategy) {
            case THROW -> throw new LockAcquireFailureException(keys);
            case RETURN_NULL -> {
                return null;
            }
            case FAILBACK -> {
                return invokeFallback(pjp, method, annotation.fallbackMethod());
            }
            default -> throw new IllegalStateException("Unknown fail strategy: " + strategy);
        }
    }

    private Object invokeFallback(ProceedingJoinPoint pjp, Method method, String fallbackName) throws Throwable {
        if (fallbackName == null || fallbackName.isEmpty()) {
            throw new IllegalStateException(
                    "failStrategy=FAILBACK but fallbackMethod is empty on " + method);
        }
        Class<?> targetClass = pjp.getTarget().getClass();
        Method fallback = ReflectionUtils.findMethod(targetClass, fallbackName, method.getParameterTypes());
        if (fallback == null) {
            throw new IllegalStateException(
                    "Fallback method not found: " + fallbackName + " on " + targetClass.getName());
        }
        ReflectionUtils.makeAccessible(fallback);
        return fallback.invoke(pjp.getTarget(), pjp.getArgs());
    }
}
