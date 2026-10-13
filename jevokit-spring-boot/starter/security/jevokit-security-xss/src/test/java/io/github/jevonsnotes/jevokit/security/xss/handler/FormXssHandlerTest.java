package io.github.jevonsnotes.jevokit.security.xss.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.configuration.security.xss.XssProperties;
import io.github.jevonsnotes.jevokit.core.editor.StringPropertiesEditor;
import io.github.jevonsnotes.jevokit.security.xss.chain.XssStringPropertiesEditChain;
import io.github.jevonsnotes.jevokit.security.xss.cleaner.DefaultXssCleaner;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FormXssHandler：@InitBinder 注册 String 编辑器，表单绑定阶段完成清洗。
 * 用 DataBinder 全链路驱动（等价于表单提交语义）。
 */
class FormXssHandlerTest {

    static class FormBean {
        private String remark;

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    private final XssProperties properties = new XssProperties();
    private final FormXssHandler handler = new FormXssHandler(
            new StringPropertiesEditor(Arrays.asList(new XssStringPropertiesEditChain(
                    new DefaultXssCleaner(properties), properties))));

    @AfterEach
    void tearDown() {
        ConfigHolder.removeXss();
    }

    @Test
    @DisplayName("[真] 表单绑定：攻击载荷被清洗后写入 bean")
    void formBindingCleansPayload() {
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setMode(XssProperties.Mode.clear);

        WebDataBinder binder = new WebDataBinder(new FormBean());
        handler.initBinder(binder);
        MutablePropertyValues pvs = new MutablePropertyValues();
        pvs.add("remark", "<script>alert(1)</script>表单备注");
        binder.bind(pvs);

        String bound = ((FormBean) binder.getTarget()).getRemark();
        assertThat(bound.toLowerCase())
                .as("表单字段经 InitBinder 编辑器后不得残留载荷: %s", bound)
                .doesNotContain("<script");
    }

    @Test
    @DisplayName("[假] 表单绑定：普通中文零误伤")
    void formBindingKeepsBenign() {
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setMode(XssProperties.Mode.clear);

        WebDataBinder binder = new WebDataBinder(new FormBean());
        handler.initBinder(binder);
        MutablePropertyValues pvs = new MutablePropertyValues();
        pvs.add("remark", "正常的表单内容");
        binder.bind(pvs);

        assertThat(((FormBean) binder.getTarget()).getRemark())
                .as("普通表单值必须原样绑定")
                .isEqualTo("正常的表单内容");
    }

    @Test
    @DisplayName("开关未装配（未过拦截器）：表单值原样绑定")
    void formBindingPassthroughWhenMetaNotSet() {
        WebDataBinder binder = new WebDataBinder(new FormBean());
        handler.initBinder(binder);
        MutablePropertyValues pvs = new MutablePropertyValues();
        pvs.add("remark", "<script>alert(1)</script>");
        binder.bind(pvs);

        assertThat(((FormBean) binder.getTarget()).getRemark())
                .as("ThreadLocal 未开启时表单编辑器链直通")
                .isEqualTo("<script>alert(1)</script>");
    }
}
