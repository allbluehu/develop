package org.allbluehu.develop.lock.support;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LockKeyResolver SpEL 解析")
class LockKeyResolverTest {

    private final LockKeyResolver resolver = new LockKeyResolver();

    /** 用于 SpEL 访问对象属性 */
    record User(String id, String name) {}

    public void sample(String orderId, User user, List<String> ids) {}

    private Method sampleMethod() throws NoSuchMethodException {
        return getClass().getMethod("sample", String.class, User.class, List.class);
    }

    @Test
    @DisplayName("keys 为空时按「类名.方法名」整体加锁")
    void emptyKeysFallbackToMethodLevel() throws Exception {
        List<String> keys = resolver.resolveKeys(new String[0], "lock:", "",
                sampleMethod(), new Object[]{"x", null, List.of()});
        assertEquals(List.of("lock:" + getClass().getSimpleName() + ".sample"), keys);
    }

    @Test
    @DisplayName("单参数 SpEL")
    void singleParamSpel() throws Exception {
        List<String> keys = resolver.resolveKeys(new String[]{"#orderId"}, "lock:", "order",
                sampleMethod(), new Object[]{"100023", null, List.of()});
        assertEquals(List.of("lock:order:100023"), keys);
    }

    @Test
    @DisplayName("字符串拼接 SpEL")
    void concatSpel() throws Exception {
        List<String> keys = resolver.resolveKeys(new String[]{"'user:' + #user.id"}, "lock:", "",
                sampleMethod(), new Object[]{"x", new User("u1", "n"), List.of()});
        assertEquals(List.of("lock:user:u1"), keys);
    }

    @Test
    @DisplayName("多 key 表达式")
    void multipleKeys() throws Exception {
        List<String> keys = resolver.resolveKeys(
                new String[]{"#orderId", "'user:' + #user.id"}, "lock:", "",
                sampleMethod(), new Object[]{"ord1", new User("u1", "n"), List.of()});
        assertEquals(List.of("lock:ord1", "lock:user:u1"), keys);
    }

    @Test
    @DisplayName("namespace 为空时不拼接额外冒号")
    void emptyNamespaceNoExtraColon() throws Exception {
        List<String> keys = resolver.resolveKeys(new String[]{"#orderId"}, "lock:", "",
                sampleMethod(), new Object[]{"k", null, List.of()});
        assertEquals("lock:k", keys.get(0));
    }

    @Test
    @DisplayName("SpEL 求值为 null 抛异常")
    void nullEvaluationThrows() {
        // #user 本身为 null → 求值结果 null
        assertThrows(IllegalStateException.class, () ->
                resolver.resolveKeys(new String[]{"#user"}, "lock:", "",
                        sampleMethod(), new Object[]{"x", null, List.of()}));
    }
}
