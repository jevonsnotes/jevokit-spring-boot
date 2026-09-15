package springbootkit.security.sql.inject.chain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.configuration.security.sql.inject.SqlInjectProperties;
import springbootkit.security.sql.inject.util.SqlInjectUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SqlInjectStringPropertiesEditChain：表单字符串编辑链。
 * 修复后语义（BUG-9）：setTextEdit 读取 SI 自己的元数据开关（getSIMeta），
 * 与 path-traversal 拦截器解耦——PT 关闭时 SI 表单清洗独立生效。
 */
class SqlInjectStringPropertiesEditChainTest {

    private final SqlInjectProperties properties = new SqlInjectProperties();
    private final SqlInjectStringPropertiesEditChain chain = new SqlInjectStringPropertiesEditChain(properties);

    @AfterEach
    void tearDown() {
        ConfigHolder.removeSI();
        ConfigHolder.removePt();
    }

    @Test
    @DisplayName("getTextEdit 恒等返回（响应方向不处理）")
    void getTextEditIsIdentity() {
        assertThat(chain.getTextEdit("a OR 1=1"))
                .as("getTextEdit 为响应方向，不应清洗")
                .isEqualTo("a OR 1=1");
    }

    @Test
    @DisplayName("[真] SI 元数据开启 + clear 模式：setTextEdit 清洗（不再依赖 PT 开关，BUG-9 修复锁死）")
    void setTextEditCleansWhenSiMetaEnabled() {
        ConfigHolder.getSIMeta().setEnabled(true);
        ConfigHolder.getSIMeta().setMode(SqlInjectProperties.Mode.clear);
        String newer = chain.setTextEdit("name' OR 1=1 --");
        assertThat(newer)
                .as("SI 开关开启即清洗（PT 元数据未装配）: %s", newer)
                .isEqualTo("name' --");
    }

    @Test
    @DisplayName("[真] PT 关闭态下 SI 独立生效：两开关解耦回归（BUG-9 修复锁死）")
    void siCleaningIndependentOfPtSwitch() {
        ConfigHolder.getPtMeta().setEnabled(false);
        ConfigHolder.getSIMeta().setEnabled(true);
        ConfigHolder.getSIMeta().setMode(SqlInjectProperties.Mode.clear);
        assertThat(chain.setTextEdit("x WHERE a=1"))
                .as("PT 显式关闭时 SI 表单清洗仍独立生效")
                .isEqualTo("x");
    }

    @Test
    @DisplayName("[假] PT 开启但 SI 未开启：SI 链不得清洗（反向解耦，防再次读错元数据）")
    void noCleaningWhenOnlyPtMetaEnabled() {
        ConfigHolder.getPtMeta().setEnabled(true);
        String source = "name' OR 1=1 --";
        assertThat(chain.setTextEdit(source))
                .as("SI 元数据未开启时（即使 PT 开启）本链必须直通")
                .isEqualTo(source);
    }

    @Test
    @DisplayName("[假] 两个开关都未开启：setTextEdit 原样返回")
    void setTextEditPassthroughWhenBothDisabled() {
        String source = "name' OR 1=1 --";
        assertThat(chain.setTextEdit(source))
                .as("ThreadLocal 开关未装配时表单参数原样放行")
                .isEqualTo(source);
    }

    @Test
    @DisplayName("[真] SI reject 模式：setTextEdit 抛异常语义由 clean 承担")
    void setTextEditRejectPath() {
        ConfigHolder.getSIMeta().setEnabled(true);
        ConfigHolder.getSIMeta().setMode(SqlInjectProperties.Mode.reject);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> chain.setTextEdit("name' OR 1=1"))
                .as("SI 开关开启 + reject 模式必须拒绝")
                .isInstanceOf(springbootkit.security.sql.inject.exception.SqlInjectException.class);
        // 与 SqlInjectUtil.clean 同参验证链与工具一致
        ConfigHolder.getSIMeta().setMode(SqlInjectProperties.Mode.clear);
        assertThat(SqlInjectUtil.clean("x WHERE a=1", properties.getAllTaintedPatterns()))
                .as("链内委托 clean 的行为与直接调用一致")
                .isEqualTo("x");
    }
}
