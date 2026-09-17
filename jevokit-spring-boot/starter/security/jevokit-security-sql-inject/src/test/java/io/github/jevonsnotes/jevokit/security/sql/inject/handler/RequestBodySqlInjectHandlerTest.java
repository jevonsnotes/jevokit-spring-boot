package io.github.jevonsnotes.jevokit.security.sql.inject.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.configuration.security.sql.inject.SqlInjectProperties;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RequestBodySqlInjectHandler：@RequestBody 请求体字符串字段清洗。
 */
class RequestBodySqlInjectHandlerTest {

    static class QueryBean {
        private String keyword;
        private List<String> filters = new ArrayList<>();
        private Integer limit;

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public List<String> getFilters() {
            return filters;
        }

        public void setFilters(List<String> filters) {
            this.filters = filters;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }
    }

    static class Endpoint {
        public QueryBean search(QueryBean bean) {
            return bean;
        }
    }

    private final SqlInjectProperties properties = new SqlInjectProperties();
    private final RequestBodySqlInjectHandler handler = new RequestBodySqlInjectHandler(properties);

    @AfterEach
    void tearDown() {
        ConfigHolder.removeSI();
    }

    private MethodParameter parameter() throws NoSuchMethodException {
        Method method = Endpoint.class.getMethod("search", QueryBean.class);
        return new MethodParameter(method, 0);
    }

    @Test
    @DisplayName("supports：随 SI 元数据开关切换")
    void supportsFollowsMetaSwitch() throws NoSuchMethodException {
        ConfigHolder.getSIMeta().setEnabled(false);
        assertThat(handler.supports(parameter(), null, null)).as("开关关闭不启用").isFalse();
        ConfigHolder.getSIMeta().setEnabled(true);
        assertThat(handler.supports(parameter(), null, null)).as("开关开启应启用").isTrue();
    }

    @Test
    @DisplayName("[真] afterBodyRead 清洗 POJO 字符串字段（clear 模式）")
    void afterBodyReadCleansStringFields() throws NoSuchMethodException {
        ConfigHolder.getSIMeta().setMode(SqlInjectProperties.Mode.clear);
        QueryBean bean = new QueryBean();
        bean.setKeyword("name' OR 1=1 --");
        bean.getFilters().add("DELETE FROM users WHERE id=1");
        bean.setLimit(10);

        QueryBean cleaned = (QueryBean) handler.afterBodyRead(bean, null, parameter(), QueryBean.class, null);

        assertThat(cleaned.getKeyword())
                .as("keyword 中 OR 1=1 片段必须被清除")
                .isEqualTo("name' --");
        assertThat(cleaned.getFilters().get(0))
                .as("集合元素中的 DELETE FROM 片段必须被清除")
                .doesNotContain("DELETE FROM");
        assertThat(cleaned.getLimit()).as("非字符串字段不受影响").isEqualTo(10);
    }

    @Test
    @DisplayName("[假] afterBodyRead 普通中文零误伤")
    void afterBodyReadKeepsBenign() throws NoSuchMethodException {
        ConfigHolder.getSIMeta().setMode(SqlInjectProperties.Mode.clear);
        QueryBean bean = new QueryBean();
        bean.setKeyword("张三的订单");

        QueryBean cleaned = (QueryBean) handler.afterBodyRead(bean, null, parameter(), QueryBean.class, null);

        assertThat(cleaned.getKeyword()).as("普通关键词必须原样保留").isEqualTo("张三的订单");
    }

    @Test
    @DisplayName("handleEmptyBody 原样返回")
    void handleEmptyBodyPassthrough() throws NoSuchMethodException {
        assertThat(handler.handleEmptyBody(null, null, parameter(), QueryBean.class, null)).isNull();
    }

    @Test
    @DisplayName("beforeBodyRead 原样返回")
    void beforeBodyReadPassthrough() throws Exception {
        assertThat(handler.beforeBodyRead(null, null, null, null)).isNull();
    }
}
