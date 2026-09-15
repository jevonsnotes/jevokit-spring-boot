package springbootkit.security.xss.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.configuration.security.xss.XssProperties;
import springbootkit.security.xss.cleaner.DefaultXssCleaner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XmlXssHandler：XML 报文（Jaxb2 转换器）请求体字符串字段清洗。
 */
class XmlXssHandlerTest {

    static class OrderBean {
        private String remark;
        private List<String> tags = new ArrayList<>();
        private Integer count;

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }

    static class Endpoint {
        @SuppressWarnings("unused")
        public OrderBean post(OrderBean bean) {
            return bean;
        }
    }

    private final XssProperties properties = new XssProperties();
    private final XmlXssHandler handler = new XmlXssHandler(new DefaultXssCleaner(properties));

    @AfterEach
    void tearDown() {
        ConfigHolder.removeXss();
    }

    private MethodParameter parameter() throws NoSuchMethodException {
        Method method = Endpoint.class.getMethod("post", OrderBean.class);
        return new MethodParameter(method, 0);
    }

    @Test
    @DisplayName("supports：仅 Jaxb2 转换器 + 开关开启时生效")
    void supportsOnlyForJaxbAndEnabled() throws NoSuchMethodException {
        HttpMessageConverter<?> jaxb = new Jaxb2RootElementHttpMessageConverter();
        HttpMessageConverter<?> jackson = new MappingJackson2HttpMessageConverter();

        ConfigHolder.getXssMeta().setEnabled(false);
        assertThat(handler.supports(parameter(), null, converterClass(jaxb)))
                .as("开关关闭时不得启用")
                .isFalse();

        ConfigHolder.getXssMeta().setEnabled(true);
        assertThat(handler.supports(parameter(), null, converterClass(jaxb)))
                .as("Jaxb2 转换器 + 开关开启应启用")
                .isTrue();
        assertThat(handler.supports(parameter(), null, converterClass(jackson)))
                .as("非 Jaxb2 转换器（JSON 通道）不走 XML 处理器")
                .isFalse();
    }

    @SuppressWarnings("unchecked")
    private Class<? extends HttpMessageConverter<?>> converterClass(HttpMessageConverter<?> converter) {
        return (Class<? extends HttpMessageConverter<?>>) converter.getClass();
    }

    @Test
    @DisplayName("[真] afterBodyRead 清洗 POJO 字符串字段中的载荷")
    void afterBodyReadCleansStringFields() throws NoSuchMethodException {
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setMode(XssProperties.Mode.clear);
        OrderBean bean = new OrderBean();
        bean.setRemark("<script>alert(1)</script>备注");
        bean.getTags().add("<img src=x onerror=alert(1)>");
        bean.setCount(7);

        OrderBean cleaned = (OrderBean) handler.afterBodyRead(bean, null, parameter(), OrderBean.class, null);
        assertThat(cleaned.getRemark().toLowerCase()).as("remark 载荷必须清除").doesNotContain("<script");
        assertThat(cleaned.getTags().get(0).toLowerCase()).as("集合内字符串载荷必须清除").doesNotContain("onerror");
        assertThat(cleaned.getCount()).as("非字符串字段不受影响").isEqualTo(7);
    }

    @Test
    @DisplayName("[假] afterBodyRead 普通中文字段零误伤")
    void afterBodyReadKeepsBenignFields() throws NoSuchMethodException {
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setMode(XssProperties.Mode.clear);
        OrderBean bean = new OrderBean();
        bean.setRemark("正常中文备注");

        OrderBean cleaned = (OrderBean) handler.afterBodyRead(bean, null, parameter(), OrderBean.class, null);

        assertThat(cleaned.getRemark()).as("无载荷字段必须原样保留").isEqualTo("正常中文备注");
    }

    @Test
    @DisplayName("handleEmptyBody 原样返回")
    void handleEmptyBodyPassthrough() throws NoSuchMethodException {
        assertThat(handler.handleEmptyBody(null, null, parameter(), OrderBean.class, null))
                .as("空体原样返回")
                .isNull();
    }

    @Test
    @DisplayName("beforeBodyRead 原样返回（清洗在 afterBodyRead 完成）")
    void beforeBodyReadPassthrough() throws Exception {
        assertThat(handler.beforeBodyRead(null, null, null, null))
                .as("beforeBodyRead 不做包装")
                .isNull();
    }
}
