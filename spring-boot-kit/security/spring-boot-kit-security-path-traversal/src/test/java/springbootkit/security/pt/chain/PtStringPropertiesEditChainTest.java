package springbootkit.security.pt.chain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.configuration.security.pathtraversal.PathTraversalProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PtStringPropertiesEditChain：表单字符串编辑链（开关注解 PT 元数据，与 SI 链不同，此处正确）。
 */
class PtStringPropertiesEditChainTest {

    private final PathTraversalProperties properties = new PathTraversalProperties();
    private final PtStringPropertiesEditChain chain = new PtStringPropertiesEditChain(properties);

    @AfterEach
    void tearDown() {
        ConfigHolder.removePt();
    }

    @Test
    @DisplayName("getTextEdit 恒等返回（响应方向不处理）")
    void getTextEditIsIdentity() {
        assertThat(chain.getTextEdit("../../etc/passwd"))
                .as("getTextEdit 为响应方向，不应清洗")
                .isEqualTo("../../etc/passwd");
    }

    @Test
    @DisplayName("[真] 开关开启 + clear 模式：setTextEdit 清除穿越片段")
    void setTextEditCleansWhenEnabled() {
        ConfigHolder.getPtMeta().setEnabled(true);
        ConfigHolder.getPtMeta().setMode(PathTraversalProperties.Mode.clear);
        assertThat(chain.setTextEdit("reports/../../etc/passwd"))
                .as("开启后 ../ 与 /etc/ 片段必须被清除")
                .doesNotContain("../")
                .doesNotContain("/etc/");
    }

    @Test
    @DisplayName("[真] 开关开启 + reject 模式（全局默认）：setTextEdit 抛 PathTraversal 异常")
    void setTextEditRejectsWhenEnabled() {
        ConfigHolder.getPtMeta().setEnabled(true);
        ConfigHolder.getPtMeta().setMode(PathTraversalProperties.Mode.reject);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> chain.setTextEdit("../../etc/passwd"))
                .as("reject 模式下穿越样本必须抛异常")
                .isInstanceOf(springbootkit.security.pt.exception.PathTraversalException.class);
    }

    @Test
    @DisplayName("[假] 开关关闭：setTextEdit 原样返回")
    void setTextEditPassthroughWhenDisabled() {
        String source = "../../etc/passwd";
        assertThat(chain.setTextEdit(source))
                .as("ThreadLocal 开关未装配时表单参数原样放行")
                .isEqualTo(source);
    }

    @Test
    @DisplayName("[假] 开关开启 + clear：正常路径零误伤")
    void setTextEditKeepsBenignPath() {
        ConfigHolder.getPtMeta().setEnabled(true);
        ConfigHolder.getPtMeta().setMode(PathTraversalProperties.Mode.clear);
        assertThat(chain.setTextEdit("/data/files/report.pdf"))
                .as("正常路径必须原样返回")
                .isEqualTo("/data/files/report.pdf");
    }
}
