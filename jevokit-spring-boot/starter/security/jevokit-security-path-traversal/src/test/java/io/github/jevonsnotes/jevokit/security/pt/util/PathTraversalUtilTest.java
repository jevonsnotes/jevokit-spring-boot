package io.github.jevonsnotes.jevokit.security.pt.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalProperties;
import io.github.jevonsnotes.jevokit.security.pt.exception.PathTraversalException;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PathTraversalUtil 真假矩阵（默认污染表：../ 与 /etc/.*）。
 */
class PathTraversalUtilTest {

    private final PathTraversalProperties properties = new PathTraversalProperties();

    @AfterEach
    void tearDown() {
        ConfigHolder.removePt();
    }

    private void mode(PathTraversalProperties.Mode mode) {
        ConfigHolder.getPtMeta().setMode(mode);
    }

    @ParameterizedTest(name = "[真] clear 清除穿越样本: {0}")
    @ValueSource(strings = {
            "reports/../../etc/passwd",
            "../../../etc/shadow",
            "downloads/../../../../root/.ssh/id_rsa",
            "/etc/passwd",
            "etc/../../etc/hosts"
    })
    void clearModeCleansTraversalPayloads(String payload) {
        mode(PathTraversalProperties.Mode.clear);
        String cleaned = PathTraversalUtil.clean(payload, properties.getAllTaintedPatterns());
        assertThat(cleaned)
                .as("clear 模式必须移除 ../ 与 /etc/ 片段: [%s] -> [%s]", payload, cleaned)
                .doesNotContain("../")
                .doesNotContain("/etc/");
    }

    @ParameterizedTest(name = "[假] clear 正常路径零误伤: {0}")
    @ValueSource(strings = {
            "/data/files/report.pdf",
            "data/upload/2024/11/20/logo.png",
            "a..b.txt",
            "文件路径：第2页.docx",
            "https://example.com/files/a.png"
    })
    void clearModeKeepsBenignPaths(String content) {
        mode(PathTraversalProperties.Mode.clear);
        assertThat(PathTraversalUtil.clean(content, properties.getAllTaintedPatterns()))
                .as("正常路径必须原样返回: %s", content)
                .isEqualTo(content);
    }

    @ParameterizedTest(name = "[真] reject 拒绝穿越样本: {0}")
    @ValueSource(strings = {
            "reports/../../etc/passwd",
            "../../../root",
            "/etc/passwd"
    })
    void rejectModeThrowsOnTraversal(String payload) {
        mode(PathTraversalProperties.Mode.reject);
        assertThatThrownBy(() -> PathTraversalUtil.clean(payload, properties.getAllTaintedPatterns()))
                .as("reject 模式必须对穿越样本抛 PathTraversalException: %s", payload)
                .isInstanceOf(PathTraversalException.class);
    }

    @ParameterizedTest(name = "[假] reject 放行正常路径: {0}")
    @ValueSource(strings = {
            "/data/files/report.pdf",
            "a..b.txt",
            "文件路径：第2页.docx"
    })
    void rejectModePassesBenignPaths(String content) {
        mode(PathTraversalProperties.Mode.reject);
        assertThatCode(() -> assertThat(PathTraversalUtil.clean(content, properties.getAllTaintedPatterns()))
                .as("正常路径必须原样放行: %s", content)
                .isEqualTo(content))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[真] additionTaintedPatterns：绝对 Windows 路径形态被追加规则清除")
    void clearModeAppliesAdditionTaintedPatterns() {
        properties.setAdditionTaintedPatterns(java.util.List.of("(?i)[a-z]:\\\\{1,2}"));
        mode(PathTraversalProperties.Mode.clear);
        String cleaned = PathTraversalUtil.clean("C:\\Windows\\system32\\config", properties.getAllTaintedPatterns());
        assertThat(cleaned)
                .as("追加的盘符规则应被清除，只留路径体: %s", cleaned)
                .isEqualTo("Windows\\system32\\config");
    }

    @Test
    @DisplayName("空串/null/空白串原样返回")
    void blankContentIsReturnedAsIs() {
        for (PathTraversalProperties.Mode m : PathTraversalProperties.Mode.values()) {
            mode(m);
            assertThat(PathTraversalUtil.clean(null, properties.getAllTaintedPatterns()))
                    .as("null 在 %s 模式原样返回", m).isNull();
            assertThat(PathTraversalUtil.clean("", properties.getAllTaintedPatterns()))
                    .as("空串在 %s 模式原样返回", m).isEmpty();
        }
    }

    @Test
    @DisplayName("污染模式列表为空时原样返回（防御性）")
    void emptyPatternsPassthrough() {
        mode(PathTraversalProperties.Mode.clear);
        assertThat(PathTraversalUtil.clean("../../etc/passwd", Collections.emptyList()))
                .as("空规则列表时不得清洗")
                .isEqualTo("../../etc/passwd");
    }
}
