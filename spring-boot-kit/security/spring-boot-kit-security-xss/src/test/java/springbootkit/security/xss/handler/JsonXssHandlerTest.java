package springbootkit.security.xss.handler;

import com.fasterxml.jackson.core.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.configuration.security.xss.XssProperties;
import springbootkit.security.xss.cleaner.DefaultXssCleaner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * JsonXssHandler：@RequestBody JSON 字符串属性反序列化时清洗。
 * 通过 mock JsonParser 直接驱动 deserialize（无需完整 ObjectMapper 管线）。
 */
class JsonXssHandlerTest {

    private final XssProperties properties = new XssProperties();
    private final JsonXssHandler handler =
            new JsonXssHandler(properties, new DefaultXssCleaner(properties));

    @AfterEach
    void tearDown() {
        ConfigHolder.removeXss();
    }

    private JsonParser parserFor(String value) throws java.io.IOException {
        JsonParser parser = Mockito.mock(JsonParser.class);
        when(parser.getValueAsString()).thenReturn(value);
        return parser;
    }

    @Test
    @DisplayName("null 文本直接返回 null")
    void nullTextReturnsNull() throws Exception {
        assertThat(handler.deserialize(parserFor(null), null))
                .as("JSON null 值不得进入清洗")
                .isNull();
    }

    @ParameterizedTest(name = "[真] 开关开启 clear：JSON 字符串值清洗: {0}")
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<a href=\"javascript:alert(1)\">x</a>"
    })
    void cleansPayloadWhenEnabled(String payload) throws Exception {
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setMode(XssProperties.Mode.clear);
        String value = handler.deserialize(parserFor(payload), null);
        assertThat(value.toLowerCase())
                .as("JSON 属性值中的载荷必须被清洗: %s", value)
                .doesNotContain("<script")
                .doesNotContain("onerror")
                .doesNotContain("javascript:");
    }

    @Test
    @DisplayName("[假] 开关开启 clear：普通中文 JSON 值零误伤")
    void keepsBenignValueWhenEnabled() throws Exception {
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setMode(XssProperties.Mode.clear);
        assertThat(handler.deserialize(parserFor("中文属性值 123"), null))
                .as("普通文本属性值必须原样返回")
                .isEqualTo("中文属性值 123");
    }

    @Test
    @DisplayName("[假] 开关关闭：载荷也原样返回（未过拦截器的直连调用）")
    void passthroughWhenDisabled() throws Exception {
        String payload = "<script>alert(1)</script>";
        assertThat(handler.deserialize(parserFor(payload), null))
                .as("ThreadLocal 开关未开启时不清洗")
                .isEqualTo(payload);
    }

    @Test
    @DisplayName("开关开启 + escape 模式：值被实体转义")
    void escapesPayloadWhenEnabled() throws Exception {
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setMode(XssProperties.Mode.escape);
        assertThat(handler.deserialize(parserFor("<b>加粗</b>"), null))
                .as("escape 模式下 JSON 字符串值整体实体转义")
                .isEqualTo("&lt;b&gt;加粗&lt;/b&gt;");
    }

    @Test
    @DisplayName("trimText=true 时清洗前先去首尾空白")
    void trimsBeforeCleanWhenTrimTextOn() throws Exception {
        properties.setTrimText(true);
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setMode(XssProperties.Mode.clear);
        assertThat(handler.deserialize(parserFor("  中文值  "), null))
                .as("trimText 开启时先 trim 后清洗")
                .isEqualTo("中文值");
    }

    @Test
    @DisplayName("trimText=true 且开关关闭：仅 trim")
    void trimsOnlyWhenDisabled() throws Exception {
        properties.setTrimText(true);
        assertThat(handler.deserialize(parserFor("  值  "), null))
                .isEqualTo("值");
    }
}
