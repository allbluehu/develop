package org.allbluehu.develop.lock.support;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * 把注解上的 SpEL key 表达式解析成实际的 Redis key 字符串。
 *
 * <p>最终 key 格式：{@code prefix + (namespace 为空 ? "" : namespace + ":") + 值}
 * <br>例：prefix="lock:"、namespace="order"、keys={"#orderId"}、orderId=100023 → {@code lock:order:100023}
 *
 * <p>keys 为空时，退化为按「类名.方法名」整体加锁（分布式 synchronized 语义）。
 */
public class LockKeyResolver {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    /**
     * @param keyExprs   注解上的 SpEL 表达式数组
     * @param prefix     key 前缀（如 "lock:"）
     * @param namespace  命名空间，可为空
     * @param method     目标方法
     * @param args       方法实参
     */
    public List<String> resolveKeys(String[] keyExprs, String prefix, String namespace,
                                    Method method, Object[] args) {
        String base = prefix == null ? "" : prefix;
        if (namespace != null && !namespace.isEmpty()) {
            base = base + namespace + ":";
        }

        // 无 key 表达式：按「类名.方法名」整体加锁
        if (keyExprs == null || keyExprs.length == 0) {
            return List.of(base + method.getDeclaringClass().getSimpleName() + "." + method.getName());
        }

        MethodBasedEvaluationContext ctx =
                new MethodBasedEvaluationContext(null, method, args, discoverer);

        List<String> keys = new ArrayList<>(keyExprs.length);
        for (String expr : keyExprs) {
            Expression expression = parser.parseExpression(expr);
            Object value = expression.getValue(ctx);
            if (value == null) {
                throw new IllegalStateException("Lock key SpEL evaluated to null: " + expr);
            }
            keys.add(base + value);
        }
        return keys;
    }
}
