package io.github.jevonsnotes.jevokit.security.xss.chain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.configuration.security.xss.XssProperties;
import io.github.jevonsnotes.jevokit.security.xss.cleaner.DefaultXssCleaner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XssStringPropertiesEditChain：表单字符串双向编辑链。
 * getTextEdit 恒等返回；setTextEdit 受 ThreadLocal 开关与 trimText 配置控制。
 */
class XssStringPropertiesEditChainTest {

    private final XssProperties properties = new XssProperties();
    private final XssStringPropertiesEditChain chain =
            new XssStringPropertiesEditChain(new DefaultXssCleaner(properties), properties);

    @AfterEach
    void tearDown() {
        ConfigHolder.removeXss();
    }

    @Test
    @DisplayName("getTextEdit 恒等返回（响应方向不做处理）")
    void getTextEditIsIdentity() {
        assertThat(chain.getTextEdit("<script>alert(1)</script>"))
                .as("getTextEdit 为响应方向，不应清洗")
                .isEqualTo("<script>alert(1)</script>");
        assertThat(chain.getTextEdit(null)).as("null 恒等返回").isNull();
    }

    @Test
    @DisplayName("[真] 开关开启 + clear 模式：setTextEdit 清洗攻击样本")
    void setTextEditCleansWhenEnabled() {
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setMode(XssProperties.Mode.clear);
        String newer = chain.setTextEdit("<script>alert(1)</script>文本");
        assertThat(newer.toLowerCase())
                .as("开关开启后表单入参必须被清洗: %s", newer)
                .doesNotContain("<script");
    }

    @Test
    @DisplayName("[假] 开关关闭：setTextEdit 原样返回")
    void setTextEditPassthroughWhenDisabled() {
        String source = "<script>alert(1)</script>";
        assertThat(chain.setTextEdit(source))
                .as("ThreadLocal 开关未装配时（未过拦截器）表单参数原样放行")
                .isEqualTo(source);
    }

    @Test
    @DisplayName("trimText=true 时入参去首尾空白（关闭态走 trim 分支）")
    void setTextEditTrimsWhenDisabledAndTrimOn() {
        properties.setTrimText(true);
        assertThat(chain.setTextEdit("  内容  "))
                .as("开关关闭 + trimText=true 应去首尾空白")
                .isEqualTo("内容");
    }

    @Test
    @DisplayName("trimText=true 时入参去首尾空白（开启态清洗前 trim）")
    void setTextEditTrimsWhenEnabledAndTrimOn() {
        properties.setTrimText(true);
        ConfigHolder.getXssMeta().setEnabled(true);
        ConfigHolder.getXssMeta().setMode(XssProperties.Mode.clear);
        assertThat(chain.setTextEdit("  内容  "))
                .as("开启态同样先 trim 再清洗")
                .isEqualTo("内容");
    }

    @Test
    @DisplayName("null 入参在关闭态不抛异常（trim 分支 StrUtil.trim(null) 返回 null）")
    void nullInputDoesNotThrowWhenDisabled() {
        properties.setTrimText(true);
        assertThat(chain.setTextEdit(null)).as("null 输入应返回 null").isNull();
    }
}
