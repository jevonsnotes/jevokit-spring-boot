package springbootkit.security.sql.inject.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.configuration.security.sql.inject.SqlInjectProperties;
import springbootkit.core.editor.StringPropertiesEditor;
import springbootkit.security.sql.inject.chain.SqlInjectStringPropertiesEditChain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FormSqlInjectHandler：@InitBinder 注册 String 编辑器，表单绑定阶段完成 SI 清洗。
 * 修复后语义（BUG-9）：链的开关注解 SI 元数据（getSIMeta），PT 开关不再影响本链。
 */
class FormSqlInjectHandlerTest {

    static class FormBean {
        private String keyword;

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }
    }

    private final SqlInjectProperties properties = new SqlInjectProperties();
    private final FormSqlInjectHandler handler = new FormSqlInjectHandler(
            new StringPropertiesEditor(List.of(new SqlInjectStringPropertiesEditChain(properties))));

    @AfterEach
    void tearDown() {
        ConfigHolder.removeSI();
        ConfigHolder.removePt();
    }

    @Test
    @DisplayName("[真] 表单绑定：SI 开关开启 + clear 模式下攻击片段被清除（PT 未装配，BUG-9 修复锁死）")
    void formBindingCleansPayload() {
        ConfigHolder.getSIMeta().setEnabled(true);
        ConfigHolder.getSIMeta().setMode(SqlInjectProperties.Mode.clear);

        WebDataBinder binder = new WebDataBinder(new FormBean());
        handler.initBinder(binder);
        MutablePropertyValues pvs = new MutablePropertyValues();
        pvs.add("keyword", "name' OR 1=1 --");
        binder.bind(pvs);

        String bound = ((FormBean) binder.getTarget()).getKeyword();
        assertThat(bound)
                .as("表单 keyword 的 OR 1=1 片段必须被清除: %s", bound)
                .isEqualTo("name' --");
    }

    @Test
    @DisplayName("[BUG-9 解耦锁] 表单绑定：PT 关闭态下 SI 清洗独立生效")
    void formBindingIndependentOfPtSwitch() {
        ConfigHolder.getPtMeta().setEnabled(false);
        ConfigHolder.getSIMeta().setEnabled(true);
        ConfigHolder.getSIMeta().setMode(SqlInjectProperties.Mode.clear);

        WebDataBinder binder = new WebDataBinder(new FormBean());
        handler.initBinder(binder);
        MutablePropertyValues pvs = new MutablePropertyValues();
        pvs.add("keyword", "x WHERE a=1");
        binder.bind(pvs);

        assertThat(((FormBean) binder.getTarget()).getKeyword())
                .as("PT 显式关闭时 SI 表单清洗仍独立生效")
                .isEqualTo("x");
    }

    @Test
    @DisplayName("[BUG-9 反向解耦] 表单绑定：PT 开启但 SI 未开启时不清洗")
    void formBindingPassthroughWhenOnlyPtEnabled() {
        ConfigHolder.getPtMeta().setEnabled(true);
        WebDataBinder binder = new WebDataBinder(new FormBean());
        handler.initBinder(binder);
        MutablePropertyValues pvs = new MutablePropertyValues();
        pvs.add("keyword", "name' OR 1=1 --");
        binder.bind(pvs);

        assertThat(((FormBean) binder.getTarget()).getKeyword())
                .as("SI 元数据未开启时（即使 PT 开启）表单编辑器链直通")
                .isEqualTo("name' OR 1=1 --");
    }

    @Test
    @DisplayName("[假] 表单绑定：普通中文零误伤")
    void formBindingKeepsBenign() {
        ConfigHolder.getSIMeta().setEnabled(true);
        ConfigHolder.getSIMeta().setMode(SqlInjectProperties.Mode.clear);

        WebDataBinder binder = new WebDataBinder(new FormBean());
        handler.initBinder(binder);
        MutablePropertyValues pvs = new MutablePropertyValues();
        pvs.add("keyword", "张三的订单");
        binder.bind(pvs);

        assertThat(((FormBean) binder.getTarget()).getKeyword())
                .as("普通表单值必须原样绑定")
                .isEqualTo("张三的订单");
    }

    @Test
    @DisplayName("开关未装配：表单值原样绑定")
    void formBindingPassthroughWhenMetaNotSet() {
        WebDataBinder binder = new WebDataBinder(new FormBean());
        handler.initBinder(binder);
        MutablePropertyValues pvs = new MutablePropertyValues();
        pvs.add("keyword", "name' OR 1=1 --");
        binder.bind(pvs);

        assertThat(((FormBean) binder.getTarget()).getKeyword())
                .as("ThreadLocal 开关未开启时表单编辑器链直通")
                .isEqualTo("name' OR 1=1 --");
    }
}
