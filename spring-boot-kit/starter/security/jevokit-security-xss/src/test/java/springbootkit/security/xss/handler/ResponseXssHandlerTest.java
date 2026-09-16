package springbootkit.security.xss.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.configuration.security.xss.XssProperties;
import springbootkit.security.xss.cleaner.DefaultXssCleaner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ResponseXssHandler：响应体字符串字段清洗（supports 受 enabled + ignoreResponse 双开关控制）。
 */
class ResponseXssHandlerTest {

    static class ResultBean {
        private String message;
        private List<String> notes = new ArrayList<>();

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<String> getNotes() {
            return notes;
        }

        public void setNotes(List<String> notes) {
            this.notes = notes;
        }
    }

    static class Endpoint {
        public ResultBean ok() {
            return new ResultBean();
        }
    }

    private final XssProperties properties = new XssProperties();
    private final ResponseXssHandler handler = new ResponseXssHandler(new DefaultXssCleaner(properties));

    @AfterEach
    void tearDown() {
        ConfigHolder.removeXss();
    }

    private MethodParameter parameter() throws NoSuchMethodException {
        Method method = Endpoint.class.getMethod("ok");
        return new MethodParameter(method, -1);
    }

    @Test
    @DisplayName("supports：开关关闭时 false")
    void supportsFalseWhenDisabled() throws NoSuchMethodException {
        ConfigHolder.getXssMeta().setEnabled(false);
        ConfigHolder.getXssMeta().setIgnoreResponse(false);
        assertThat(handler.supports(parameter(), String.class))
                .as("总开关关闭时响应体处理器不得生效")
                .isFalse();
    }

    @Test
    @DisplayName("supports：开关开启 + ignoreResponse=true 时 false（默认忽略响应）")
    void supportsFalseWhenIgnoreResponse() throws NoSuchMethodException {
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setIgnoreResponse(true);
        assertThat(handler.supports(parameter(), String.class))
                .as("ignoreResponse=true 时响应方向不处理")
                .isFalse();
    }

    @Test
    @DisplayName("supports：开关开启 + ignoreResponse=false 时 true")
    void supportsTrueWhenEnabledAndNotIgnored() throws NoSuchMethodException {
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setIgnoreResponse(false);
        assertThat(handler.supports(parameter(), String.class))
                .as("开启响应脱敏后处理器生效")
                .isTrue();
    }

    @Test
    @DisplayName("[真] beforeBodyWrite 清洗 POJO 字符串字段（clear 模式）")
    void beforeBodyWriteCleansStringFields() throws NoSuchMethodException {
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setMode(XssProperties.Mode.clear);
        ResultBean bean = new ResultBean();
        bean.setMessage("<script>alert(1)</script>消息");
        bean.getNotes().add("<img src=x onerror=alert(1)>");

        ResultBean cleaned = (ResultBean) handler.beforeBodyWrite(bean, parameter(), null, null, null, null);

        assertThat(cleaned.getMessage().toLowerCase()).as("message 载荷必须清除").doesNotContain("<script");
        assertThat(cleaned.getNotes().get(0).toLowerCase()).as("集合元素载荷必须清除").doesNotContain("onerror");
    }

    @Test
    @DisplayName("[假] beforeBodyWrite 普通中文零误伤")
    void beforeBodyWriteKeepsBenign() throws NoSuchMethodException {
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setMode(XssProperties.Mode.clear);
        ResultBean bean = new ResultBean();
        bean.setMessage("操作成功");

        ResultBean cleaned = (ResultBean) handler.beforeBodyWrite(bean, parameter(), null, null, null, null);

        assertThat(cleaned.getMessage()).as("普通中文响应不受影响").isEqualTo("操作成功");
    }

    @Test
    @DisplayName("beforeBodyWrite：null/空对象原样返回")
    void beforeBodyWriteKeepsEmpty() throws NoSuchMethodException {
        assertThat(handler.beforeBodyWrite(null, parameter(), null, null, null, null))
                .as("null 响应体原样返回")
                .isNull();
    }
}
