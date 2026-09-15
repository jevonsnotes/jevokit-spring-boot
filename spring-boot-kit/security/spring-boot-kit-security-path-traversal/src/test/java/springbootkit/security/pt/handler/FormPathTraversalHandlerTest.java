package springbootkit.security.pt.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.configuration.security.pathtraversal.PathTraversalProperties;
import springbootkit.core.editor.StringPropertiesEditor;
import springbootkit.security.pt.chain.PtStringPropertiesEditChain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FormPathTraversalHandler：@InitBinder 注册 String 编辑器，表单绑定阶段完成 PT 清洗。
 */
class FormPathTraversalHandlerTest {

    static class FormBean {
        private String path;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    private final PathTraversalProperties properties = new PathTraversalProperties();
    private final FormPathTraversalHandler handler = new FormPathTraversalHandler(
            new StringPropertiesEditor(List.of(new PtStringPropertiesEditChain(properties))));

    @AfterEach
    void tearDown() {
        ConfigHolder.removePt();
    }

    @Test
    @DisplayName("[真] 表单绑定（clear 模式）：穿越片段被清除")
    void formBindingCleansPayload() {
        ConfigHolder.getPtMeta().setEnabled(true);
        ConfigHolder.getPtMeta().setMode(PathTraversalProperties.Mode.clear);

        WebDataBinder binder = new WebDataBinder(new FormBean());
        handler.initBinder(binder);
        MutablePropertyValues pvs = new MutablePropertyValues();
        pvs.add("path", "reports/../../etc/passwd");
        binder.bind(pvs);

        String bound = ((FormBean) binder.getTarget()).getPath();
        assertThat(bound)
                .as("表单 path 的 ../ 与 /etc/ 片段必须被清除: %s", bound)
                .doesNotContain("../")
                .doesNotContain("/etc/");
    }

    @Test
    @DisplayName("[真] 表单绑定（reject 模式，全局默认）：编辑器直接调用时抛 PathTraversalException（DataBinder 绑定会吞掉编辑器异常仅记日志）")
    void formBindingRejectsPayload() throws Exception {
        ConfigHolder.getPtMeta().setEnabled(true);
        ConfigHolder.getPtMeta().setMode(PathTraversalProperties.Mode.reject);

        WebDataBinder binder = new WebDataBinder(new FormBean());
        handler.initBinder(binder);

        java.beans.PropertyEditor editor = binder.findCustomEditor(String.class, null);
        assertThat(editor).as("InitBinder 应已注册 String 自定义编辑器").isNotNull();
        assertThatThrownBy(() -> editor.setAsText("../../etc/passwd"))
                .as("reject 模式下编辑器处理穿越文本必须抛 PathTraversalException（DataBinder.bind 捕获后仅记录绑定错误，此处直连编辑器断言拒绝语义）")
                .isInstanceOf(springbootkit.security.pt.exception.PathTraversalException.class);
    }

    @Test
    @DisplayName("[假] 表单绑定（clear 模式）：正常路径零误伤")
    void formBindingKeepsBenign() {
        ConfigHolder.getPtMeta().setEnabled(true);
        ConfigHolder.getPtMeta().setMode(PathTraversalProperties.Mode.clear);

        WebDataBinder binder = new WebDataBinder(new FormBean());
        handler.initBinder(binder);
        MutablePropertyValues pvs = new MutablePropertyValues();
        pvs.add("path", "/data/files/report.pdf");
        binder.bind(pvs);

        assertThat(((FormBean) binder.getTarget()).getPath())
                .as("正常路径必须原样绑定")
                .isEqualTo("/data/files/report.pdf");
    }

    @Test
    @DisplayName("开关未装配：表单值原样绑定")
    void formBindingPassthroughWhenMetaNotSet() {
        WebDataBinder binder = new WebDataBinder(new FormBean());
        handler.initBinder(binder);
        MutablePropertyValues pvs = new MutablePropertyValues();
        pvs.add("path", "../../etc/passwd");
        binder.bind(pvs);

        assertThat(((FormBean) binder.getTarget()).getPath())
                .as("ThreadLocal 开关未开启时表单编辑器链直通")
                .isEqualTo("../../etc/passwd");
    }
}
