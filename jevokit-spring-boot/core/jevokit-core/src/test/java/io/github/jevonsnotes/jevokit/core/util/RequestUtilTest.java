package io.github.jevonsnotes.jevokit.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RequestUtil} 单元测试：查询串解析为多值参数 Map。
 */
@DisplayName("RequestUtil 参数解析")
class RequestUtilTest {

    @Test
    @DisplayName("decodeParams：常规键值对解析")
    void decodeSimpleParams() {
        // when
        Map<String, List<String>> params = RequestUtil.decodeParams("a=1&b=2", "UTF-8");
        // then
        assertThat(params).as("应解析出全部键值对")
                .containsEntry("a", List.of("1"))
                .containsEntry("b", List.of("2"));
    }

    @Test
    @DisplayName("decodeParams：同名参数多值按出现顺序聚合")
    void decodeMultiValueParams() {
        // when
        Map<String, List<String>> params = RequestUtil.decodeParams("f=1&f=2&f=3", StandardCharsets.UTF_8);
        // then
        assertThat(params.get("f")).as("多值应保序聚合").containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("decodeParams：URL 编码的值按指定字符集解码（中文）")
    void decodeUrlEncodedValue() {
        // given："name=" + URLEncoder.encode("张三", UTF-8)
        String encoded = "name=" + java.net.URLEncoder.encode("张三", StandardCharsets.UTF_8);
        // when
        Map<String, List<String>> params = RequestUtil.decodeParams(encoded, "UTF-8");
        // then
        assertThat(params.get("name")).as("UTF-8 编码值应正确解码").containsExactly("张三");
    }

    @Test
    @DisplayName("decodeParams：空串/null 返回空 Map")
    void decodeEmptyReturnsEmptyMap() {
        assertThat(RequestUtil.decodeParams("", "UTF-8")).as("空串应返回空 Map").isEmpty();
        assertThat(RequestUtil.decodeParams(null, "UTF-8")).as("null 应返回空 Map").isEmpty();
        assertThat(RequestUtil.decodeParams("", StandardCharsets.UTF_8)).as("Charset 重载：空串空 Map").isEmpty();
    }

    @Test
    @DisplayName("decodeParams：无等号的裸键以 null 值入 Map（当前行为固化）")
    void decodeBareKeyYieldsNullValue() {
        // when："flag"（无 = 号）
        Map<String, List<String>> params = RequestUtil.decodeParams("flag", "UTF-8");
        // then：hutool UrlQuery 将裸键以 null value 放入 queryMap，StrUtil.str(null) 保持 null
        assertThat(params).as("裸键应存在且唯一")
                .containsOnlyKeys("flag");
        assertThat(params.get("flag")).as("裸键的值列表应为 [null]")
                .hasSize(1).containsNull();
    }
}
