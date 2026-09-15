package springbootkit.core.util;

import cn.hutool.core.lang.func.Func1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import springbootkit.core.lang.Entry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CollectionUtil} 单元测试：元素抽取、Map 抽取、缺省填充、迭代、Set 转 List。
 */
@DisplayName("CollectionUtil 集合工具")
class CollectionUtilTest {

    private static final List<String> NAMES = Arrays.asList("alice", "bob", "carol");

    // 提示：本类继承了 hutool CollectionUtil（其又继承 CollUtil），父类亦有静态 extract(Iterable, Editor) 重载，
    // 裸 lambda 会因两个函数式接口均可适配而产生歧义——测试中一律用显式类型的 Func1 局部变量消歧。

    static class User {
        final String name;
        final Integer age;

        User(String name, Integer age) {
            this.name = name;
            this.age = age;
        }
    }

    private static final Func1<User, String> NAME_OF = u -> u.name;
    private static final Func1<User, Integer> AGE_OF = u -> u.age;

    // ------------------------------------------------------------------
    // extract
    // ------------------------------------------------------------------

    @Test
    @DisplayName("extract：抽取属性为 List")
    void extractMapsToFieldList() {
        // given
        List<User> users = Arrays.asList(new User("alice", 30), new User("bob", 25));
        // when
        List<String> names = CollectionUtil.extract(users, NAME_OF);
        // then
        assertThat(names).as("应按序抽取 name 字段").containsExactly("alice", "bob");
    }

    @Test
    @DisplayName("extract：ignoreNull=true 跳过 null 结果")
    void extractIgnoreNull() {
        // given：bob 的 age 为 null
        List<User> users = Arrays.asList(new User("alice", 30), new User("bob", null));
        // when
        List<Integer> withNull = CollectionUtil.extract(users, AGE_OF);
        List<Integer> ignored = CollectionUtil.extract(users, AGE_OF, true, false);
        // then
        assertThat(withNull).as("默认保留 null 元素").containsExactly(30, null);
        assertThat(ignored).as("ignoreNull 后应只剩非空").containsExactly(30);
    }

    @Test
    @DisplayName("extract：unique=true 去重")
    void extractUnique() {
        // given
        List<User> users = Arrays.asList(new User("alice", 30), new User("bob", 30), new User("carol", 20));
        // when
        List<Integer> unique = CollectionUtil.extract(users, AGE_OF, false, true);
        // then
        assertThat(unique).as("重复的 30 应只保留一次").containsExactly(30, 20);
    }

    // ------------------------------------------------------------------
    // extractToMap
    // ------------------------------------------------------------------

    @Test
    @DisplayName("extractToMap：抽取 Entry 流构建 Map")
    void extractToMapBuildsMap() {
        // given
        List<User> users = Arrays.asList(new User("alice", 30), new User("bob", 25));
        // when
        Map<String, Integer> ageByName = CollectionUtil.extractToMap(
                users, u -> new Entry<>(u.name, u.age), false);
        // then
        assertThat(ageByName).as("name→age 映射应正确").containsOnlyKeys("alice", "bob")
                .containsEntry("alice", 30).containsEntry("bob", 25);
    }

    @Test
    @DisplayName("extractToMap：ignoreNull=false 时 null value 覆盖旧值；ignoreNull=true 时跳过该条目")
    void extractToMapNullHandling() {
        // given：同 key 两条，后者 value 为 null
        List<User> users = Arrays.asList(new User("alice", 30), new User("alice", null));
        // when
        Map<String, Integer> keepNull = CollectionUtil.extractToMap(users, u -> new Entry<>(u.name, u.age), false);
        Map<String, Integer> skipNull = CollectionUtil.extractToMap(users, u -> new Entry<>(u.name, u.age), true);
        // then
        assertThat(keepNull).as("ignoreNull=false：null value 也会被 put（覆盖旧值）").containsEntry("alice", null);
        assertThat(skipNull).as("ignoreNull=true：null value 条目被跳过，先前非空条目保留")
                .containsEntry("alice", 30);
    }

    // ------------------------------------------------------------------
    // getAndSetIfNull
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getAndSetIfNull(默认值)：已有值返回原值，缺失时填充默认值")
    void getAndSetIfNullDefaultValue() {
        // given
        Map<String, Integer> map = new HashMap<>(Map.of("a", 1));
        // when
        Integer existing = CollectionUtil.getAndSetIfNull(map, "a", 99);
        Integer absent = CollectionUtil.getAndSetIfNull(map, "b", 99);
        // then
        assertThat(existing).as("已有键应返回原值").isEqualTo(1);
        assertThat(absent).as("缺失键应返回默认值").isEqualTo(99);
        assertThat(map).as("缺失键的默认值应被写回 Map").containsEntry("b", 99);
    }

    @Test
    @DisplayName("getAndSetIfNull(类型)：缺失时以无参构造创建实例并写回")
    @SuppressWarnings("unchecked")
    void getAndSetIfNullByClass() {
        // given：泛型 Class 只能经原始类型桥接（与调用方真实用法一致）
        Class<List<String>> listClass = (Class<List<String>>) (Class<?>) ArrayList.class;
        Map<String, List<String>> map = new HashMap<>();
        // when
        List<String> created = CollectionUtil.getAndSetIfNull(map, "k", listClass);
        created.add("elem");
        // then
        assertThat(created).as("应返回新建集合并可操作").containsExactly("elem");
        assertThat(map.get("k")).as("新建实例应写回原 Map").isSameAs(created);
        assertThat(CollectionUtil.getAndSetIfNull(map, "k", listClass))
                .as("已存在时返回既有实例").isSameAs(created);
    }

    // ------------------------------------------------------------------
    // iterate
    // ------------------------------------------------------------------

    @Test
    @DisplayName("iterate(Collection)：逐元素回调")
    void iterateCollection() {
        // given
        AtomicInteger seen = new AtomicInteger();
        StringBuilder sb = new StringBuilder();
        // when
        CollectionUtil.iterate(NAMES, s -> {
            seen.incrementAndGet();
            sb.append(s);
        });
        // then
        assertThat(seen.get()).as("每个元素都应回调一次").isEqualTo(3);
        assertThat(sb.toString()).as("回调顺序与集合一致").isEqualTo("alicebobcarol");
    }

    @Test
    @DisplayName("iterate(Map)：逐 entry 回调")
    void iterateMap() {
        // given
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        Map<String, Integer> collected = new HashMap<>();
        // when
        CollectionUtil.iterate(map, e -> collected.put(e.getKey(), e.getValue()));
        // then
        assertThat(collected).as("全部 entry 应被回调").containsExactlyInAnyOrderEntriesOf(map);
    }

    @Test
    @DisplayName("iterate：null/空集合不回调、不抛异常")
    void iterateNullOrEmptySafe() {
        // given：空集合与 null 都不应触发回调
        CollectionUtil.iterate(new ArrayList<String>(), s -> { throw new IllegalStateException("不应回调"); });
        CollectionUtil.iterate((List<String>) null, s -> { throw new IllegalStateException("不应回调"); });
        CollectionUtil.iterate(new HashMap<String, String>(), e -> { throw new IllegalStateException("不应回调"); });
        CollectionUtil.iterate((Map<String, String>) null, e -> { throw new IllegalStateException("不应回调"); });
        // then：到达此处即通过
        assertThat(true).as("空/null 入参应安全直通").isTrue();
    }

    // ------------------------------------------------------------------
    // toList
    // ------------------------------------------------------------------

    @Test
    @DisplayName("toList(Set)：元素保序转入 List；空 Set 返回空 List")
    void toListFromSet() {
        // given
        Set<String> set = new LinkedHashSet<>(Arrays.asList("a", "b", "c"));
        // when
        List<String> list = CollectionUtil.toList(set);
        // then
        assertThat(list).as("LinkedHashSet 的迭代序应保留").containsExactly("a", "b", "c");
        assertThat(CollectionUtil.toList(new HashSet<String>())).as("空 Set 应返回空 List").isEmpty();
        assertThat(CollectionUtil.toList((Set<String>) null)).as("null Set 应返回空 List").isEmpty();
    }

    // ------------------------------------------------------------------
    // 异常包装
    // ------------------------------------------------------------------

    @Test
    @DisplayName("extract：回调抛出的业务异常被包装为 RuntimeException 抛出")
    void extractWrapsCallbackException() {
        // given：显式类型 Func1，绕开与父类 extract(Editor) 的重载歧义
        Func1<String, Object> boom = s -> {
            throw new IllegalArgumentException("boom");
        };
        // when / then
        assertThatThrownBy(() -> CollectionUtil.extract(NAMES, boom))
                .as("回调异常应上抛且被包装")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");
    }
}
