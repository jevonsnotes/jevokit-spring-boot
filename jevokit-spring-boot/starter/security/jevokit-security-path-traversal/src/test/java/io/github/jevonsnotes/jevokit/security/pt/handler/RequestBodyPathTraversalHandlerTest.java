package io.github.jevonsnotes.jevokit.security.pt.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalProperties;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RequestBodyPathTraversalHandler：@RequestBody 请求体字符串字段清洗。
 */
class RequestBodyPathTraversalHandlerTest {

    static class FileBean {
        private String path;
        private List<String> includes = new ArrayList<>();
        private Integer size;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public List<String> getIncludes() {
            return includes;
        }

        public void setIncludes(List<String> includes) {
            this.includes = includes;
        }

        public Integer getSize() {
            return size;
        }

        public void setSize(Integer size) {
            this.size = size;
        }
    }

    static class Endpoint {
        public FileBean upload(FileBean bean) {
            return bean;
        }
    }

    private final PathTraversalProperties properties = new PathTraversalProperties();
    private final RequestBodyPathTraversalHandler handler = new RequestBodyPathTraversalHandler(properties);

    @AfterEach
    void tearDown() {
        ConfigHolder.removePt();
    }

    private MethodParameter parameter() throws NoSuchMethodException {
        Method method = Endpoint.class.getMethod("upload", FileBean.class);
        return new MethodParameter(method, 0);
    }

    @Test
    @DisplayName("supports：随 PT 元数据开关切换")
    void supportsFollowsMetaSwitch() throws NoSuchMethodException {
        ConfigHolder.getPtMeta().setEnabled(false);
        assertThat(handler.supports(parameter(), null, null)).as("开关关闭不启用").isFalse();
        ConfigHolder.getPtMeta().setEnabled(true);
        assertThat(handler.supports(parameter(), null, null)).as("开关开启应启用").isTrue();
    }

    @Test
    @DisplayName("[真] afterBodyRead 清洗 POJO 字符串字段（clear 模式）")
    void afterBodyReadCleansStringFields() throws NoSuchMethodException {
        ConfigHolder.getPtMeta().setEnabled(true);
        ConfigHolder.getPtMeta().setMode(PathTraversalProperties.Mode.clear);
        FileBean bean = new FileBean();
        bean.setPath("reports/../../etc/passwd");
        bean.getIncludes().add("../../../etc/hosts");
        bean.setSize(3);

        FileBean cleaned = (FileBean) handler.afterBodyRead(bean, null, parameter(), FileBean.class, null);

        assertThat(cleaned.getPath()).as("path 穿越片段必须清除").doesNotContain("../").doesNotContain("/etc/");
        assertThat(cleaned.getIncludes().get(0)).as("集合元素穿越片段必须清除").doesNotContain("../");
        assertThat(cleaned.getSize()).as("非字符串字段不受影响").isEqualTo(3);
    }

    @Test
    @DisplayName("[假] afterBodyRead 正常路径零误伤")
    void afterBodyReadKeepsBenign() throws NoSuchMethodException {
        ConfigHolder.getPtMeta().setEnabled(true);
        ConfigHolder.getPtMeta().setMode(PathTraversalProperties.Mode.clear);
        FileBean bean = new FileBean();
        bean.setPath("/data/files/report.pdf");

        FileBean cleaned = (FileBean) handler.afterBodyRead(bean, null, parameter(), FileBean.class, null);

        assertThat(cleaned.getPath()).as("正常路径必须原样保留").isEqualTo("/data/files/report.pdf");
    }

    @Test
    @DisplayName("handleEmptyBody 原样返回")
    void handleEmptyBodyPassthrough() throws NoSuchMethodException {
        assertThat(handler.handleEmptyBody(null, null, parameter(), FileBean.class, null)).isNull();
    }

    @Test
    @DisplayName("beforeBodyRead 原样返回")
    void beforeBodyReadPassthrough() throws Exception {
        assertThat(handler.beforeBodyRead(null, null, null, null)).isNull();
    }
}
