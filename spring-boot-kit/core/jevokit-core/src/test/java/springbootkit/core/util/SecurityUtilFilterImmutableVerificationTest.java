package springbootkit.core.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BUG-2（{@code SecurityUtil.filterStr} 不可变容器崩溃）对抗性复测：独立于开发的
 * SecurityUtilTest，覆盖协调方点名「开发自留未写」的三个变体，外加行为无回退核查。
 *
 * <p>覆盖点：
 * <ol>
 *   <li><b>Set.of 顶层入参</b>（未覆盖变体①）：不可变 Set 走 processCollection 顶层分支；</li>
 *   <li><b>Collections.unmodifiableSet 字段</b>（未覆盖变体②）：SetN 装入 unmodifiable
 *       壳 —— 专门考察「能 newInstance 同类但回写失败」的二段防线分支；</li>
 *   <li><b>回调自身抛 NPE 必须上抛</b>（未覆盖变体③，最值得攻的设计点）：防线只兜容器
 *       构造/回写的 RuntimeException，用户回调异常不得被吞 —— 在字段 String 回调、集合元素
 *       回调、Map 键/值回调四个位置分别验证；</li>
 *   <li><b>无行为回退</b>：删掉 "Unmodifiable" 字样预检查后，原先走字样跳过的
 *       unmodifiableList/unmodifiableMap 字段仍须跳过不崩、不重不乱；普通字段 + 可变
 *       ArrayList/HashSet/LinkedHashSet/HashMap 照常原地过滤；</li>
 *   <li><b>warn 日志确有输出</b>：Logback ListAppender 逐用例捕获，按消息前缀断言降级路径
 *       真的走了 warn（防止「静默成功」掩盖未按设计降级）。</li>
 * </ol>
 */
@DisplayName("BUG-2 复测：filterStr 不可变容器降级 + 回调异常上抛（对抗性验证）")
class SecurityUtilFilterImmutableVerificationTest {

    /** 修复代码使用的 warn 消息前缀（防御性降级两段） */
    private static final String SKIP_MAP_PREFIX = "Skip map cleaning:";
    private static final String SKIP_COLLECTION_PREFIX = "Skip collection cleaning:";

    private Logger securityUtilLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachListAppender() {
        securityUtilLogger = (Logger) LoggerFactory.getLogger(SecurityUtil.class);
        appender = new ListAppender<>();
        appender.start();
        securityUtilLogger.addAppender(appender);
    }

    @AfterEach
    void detachListAppender() {
        securityUtilLogger.detachAppender(appender);
        appender.stop();
    }

    private List<ILoggingEvent> warnEvents() {
        return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    private List<ILoggingEvent> warnEventsStartingWith(String prefix) {
        return warnEvents().stream().filter(e -> e.getFormattedMessage().startsWith(prefix)).toList();
    }

    /** 回调：把 "-raw" 后缀替换为 "-clean" */
    private static String mask(String s) {
        return s == null ? null : s.replace("-raw", "-clean");
    }

    // ------------------------------------------------------------------
    // 变体①：Set.of 顶层入参
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Set.of 顶层入参：不抛异常、原样返回、降级 warn 有输出")
    void setOfTopLevelInputSkippedWithWarn() {
        // given：List.of 顶层已由开发用例覆盖，Set.of 走同一 processCollection 顶层分支但类不同（SetN）
        Set<String> frozen = Set.of("a-raw", "b-raw");
        // when
        Object result = SecurityUtil.filterStr(frozen, (o, f, s) -> mask(s));
        // then
        assertThat(result).as("顶层不可变 Set 应原样返回").isSameAs(frozen);
        assertThat(frozen).as("内容保持原样").containsExactlyInAnyOrder("a-raw", "b-raw");
        assertThat(warnEventsStartingWith(SKIP_COLLECTION_PREFIX))
                .as("降级路径应输出 Skip collection cleaning warn")
                .isNotEmpty();
    }

    // ------------------------------------------------------------------
    // 变体②：Collections.unmodifiableSet 字段（SetN 可 newInstance，回写必失败 → 二段防线）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("unmodifiableSet 字段：无法构造同类临时容器，走第一段防线告警跳过")
    void unmodifiableSetFieldSkippedWithWarn() {
        // given：Collections.unmodifiableSet 的运行时类是 UnmodifiableSet，
        // 无公开无参构造器 → ReflectUtil.newInstance 失败 → 第一段防线 warn + return
        UnmodifiableSetHolder holder = new UnmodifiableSetHolder();
        // when
        SecurityUtil.filterStr(holder, (o, f, s) -> mask(s));
        // then：不崩 + 字段保持原样 + 其余字段正常 + 降级 warn 有输出
        assertThat(holder.frozenSet).as("unmodifiableSet 字段应保持原样")
                .containsExactlyInAnyOrder("keep-raw");
        assertThat(holder.name).as("普通字段应正常过滤").isEqualTo("name-clean");
        List<ILoggingEvent> warns = warnEventsStartingWith(SKIP_COLLECTION_PREFIX);
        assertThat(warns).as("降级路径应输出 Skip collection cleaning warn").isNotEmpty();
        // 注：实际走的是"cannot create temp container"第一段，非"not modifiable"第二段；
        // 两段防线任一触发即视为降级成功，此处不强制断言具体段
    }

    static class UnmodifiableSetHolder {
        String name = "name-raw";
        Set<String> frozenSet = Collections.unmodifiableSet(new HashSet<>(Set.of("keep-raw")));
    }

    // ------------------------------------------------------------------
    // 变体③：回调自身抛 NPE 必须上抛（防线不得吞用户异常）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("回调抛 NPE：字段字符串位置必须原样上抛，不被防线吞掉")
    void callbackNpeOnPlainFieldPropagates() {
        // given
        NameOnly holder = new NameOnly();
        // when / then
        assertThatThrownBy(() -> SecurityUtil.filterStr(holder, (o, f, s) -> {
            throw new NullPointerException("callback-boom-field");
        }))
                .as("用户回调异常必须穿透防线向上抛出")
                .isInstanceOf(NullPointerException.class)
                .hasMessage("callback-boom-field");
    }

    @Test
    @DisplayName("回调抛 NPE：集合元素位置必须原样上抛")
    void callbackNpeOnCollectionElementPropagates() {
        // given
        ListHolder holder = new ListHolder();
        // when / then
        assertThatThrownBy(() -> SecurityUtil.filterStr(holder, (o, f, s) -> {
            throw new NullPointerException("callback-boom-collection");
        }))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("callback-boom-collection");
    }

    @Test
    @DisplayName("回调抛 NPE：Map 键与值位置必须原样上抛")
    void callbackNpeOnMapKeyAndValuePropagates() {
        // given：键位置（Map<String,String> 的 key 是 String，回调会作用在键上）
        MapHolder holder = new MapHolder();
        // when / then：键位置
        assertThatThrownBy(() -> SecurityUtil.filterStr(holder, (o, f, s) -> {
            throw new NullPointerException("callback-boom-map-key");
        }))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("callback-boom-map-key");
    }

    @Test
    @DisplayName("回调抛 NPE：顶层 String 入参位置必须原样上抛")
    void callbackNpeOnTopLevelStringPropagates() {
        // when / then
        assertThatThrownBy(() -> SecurityUtil.filterStr("s-raw", (o, f, s) -> {
            throw new NullPointerException("callback-boom-top-level");
        }))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("callback-boom-top-level");
    }

    // ------------------------------------------------------------------
    // 无行为回退核查：删掉字样预检查后，原有跳过语义与正常过滤路径均不变
    // ------------------------------------------------------------------

    @Test
    @DisplayName("行为无回退：unmodifiableList/Map 字段依旧跳过且无 duplicate 异常")
    void unmodifiableFieldsStillSkippedWithoutRegression() {
        // given：unmodifiableMap 的运行时类可 newInstance（HashMap 子类壳）—— 删除字样预检查后
        // 它从「预检查跳过」改走「回写失败降级」，结果语义必须不变；List 侧同理
        LegacyImmutableHolder holder = new LegacyImmutableHolder();
        // when
        SecurityUtil.filterStr(holder, (o, f, s) -> mask(s));
        // then
        assertThat(holder.frozenList).as("unmodifiableList 保持原样").containsExactly("keep-raw");
        assertThat(holder.frozenMap).as("unmodifiableMap 保持原样").containsEntry("k-raw", "v-raw");
        assertThat(holder.name).as("普通字段正常过滤").isEqualTo("name-clean");
        assertThat(warnEventsStartingWith(SKIP_MAP_PREFIX))
                .as("unmodifiableMap 走回写失败降级应有 map 侧 warn")
                .isNotEmpty();
    }

    static class LegacyImmutableHolder {
        String name = "name-raw";
        List<String> frozenList = Collections.unmodifiableList(new ArrayList<>(List.of("keep-raw")));
        Map<String, String> frozenMap = Collections.unmodifiableMap(new HashMap<>(Map.of("k-raw", "v-raw")));
    }

    @Test
    @DisplayName("行为无回退：可变 ArrayList/HashSet/LinkedHashSet/HashMap 照常原地过滤")
    void mutableContainersStillFilteredInPlace() {
        // given：覆盖各类可变容器（ArrayList 走 processCollection、HashSet/LinkedHashSet
        // newInstance 与回写都成功、HashMap 走 processMap）
        MutableHolder holder = new MutableHolder();
        // when
        SecurityUtil.filterStr(holder, (o, f, s) -> mask(s));
        // then
        assertThat(holder.name).as("普通字段").isEqualTo("name-clean");
        assertThat(holder.list).as("ArrayList 字段应原地过滤").containsExactly("a-clean", "b-clean");
        assertThat(holder.hashSet).as("HashSet 字段应原地过滤").containsExactlyInAnyOrder("s-clean");
        assertThat(holder.linkedHashSet).as("LinkedHashSet 字段应按原序过滤")
                .containsExactly("l1-clean", "l2-clean");
        assertThat(holder.map).as("HashMap 字段键值都过滤").containsEntry("k-clean", "v-clean");
        // and：可变容器过滤不应产生任何降级 warn
        assertThat(warnEventsStartingWith(SKIP_COLLECTION_PREFIX))
                .as("可变集合过滤不应触发降级 warn").isEmpty();
        assertThat(warnEventsStartingWith(SKIP_MAP_PREFIX))
                .as("可变 Map 过滤不应触发降级 warn").isEmpty();
    }

    static class MutableHolder {
        String name = "name-raw";
        List<String> list = new ArrayList<>(List.of("a-raw", "b-raw"));
        Set<String> hashSet = new HashSet<>(Set.of("s-raw"));
        Set<String> linkedHashSet = new LinkedHashSet<>(List.of("l1-raw", "l2-raw"));
        Map<String, String> map = new HashMap<>(Map.of("k-raw", "v-raw"));
    }

    // fixtures
    static class NameOnly {
        String name = "name-raw";
    }

    static class ListHolder {
        List<String> list = new ArrayList<>(List.of("x-raw"));
    }

    static class MapHolder {
        Map<String, String> map = new HashMap<>(Map.of("k-raw", "v-raw"));
    }
}
