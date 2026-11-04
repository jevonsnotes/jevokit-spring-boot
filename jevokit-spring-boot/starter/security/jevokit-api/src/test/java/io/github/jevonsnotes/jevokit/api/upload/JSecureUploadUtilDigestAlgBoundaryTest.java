package io.github.jevonsnotes.jevokit.api.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * normalizeDigestAlg 边界补充用例（测试工程师独立复核，覆盖现有用例未盖住的算法范围交互）：
 * <ul>
 *   <li>空白/null 归一 sha256（全集版与终端范围版）；</li>
 *   <li>大小写与首尾空格（报文侧 SM3/Sha256、配置侧 "  SM3  "）；</li>
 *   <li>终端配 [sm3] 时空白条目（归一成 sha256）应拒——空白不是"未声明"的逃生门；</li>
 *   <li>范围配置含非法名（[md5, sha1] / [sha-256]）时的行为与层间一致性（红用例见类内注释）。</li>
 * </ul>
 */
@DisplayName("normalizeDigestAlg 算法范围边界")
class JSecureUploadUtilDigestAlgBoundaryTest {

    // ------------------------------------------------------------------
    // 全集版（无终端范围）：空白归一 + 大小写/空格
    // ------------------------------------------------------------------

    @Test
    @DisplayName("全集版空白归一：null/空串/纯空格 均归一为 sha256")
    void fullSetBlankNormalizesToSha256() {
        assertThat(JSecureUploadUtil.normalizeDigestAlg(null)).isEqualTo("sha256");
        assertThat(JSecureUploadUtil.normalizeDigestAlg("")).isEqualTo("sha256");
        assertThat(JSecureUploadUtil.normalizeDigestAlg("   ")).isEqualTo("sha256");
    }

    @Test
    @DisplayName("全集版大小写与首尾空格：SM3/Sha256/带空格 均归一为小写算法名")
    void fullSetCaseAndWhitespaceTolerated() {
        assertThat(JSecureUploadUtil.normalizeDigestAlg("SM3")).isEqualTo("sm3");
        assertThat(JSecureUploadUtil.normalizeDigestAlg("Sha256")).isEqualTo("sha256");
        assertThat(JSecureUploadUtil.normalizeDigestAlg("  md5  ")).isEqualTo("md5");
    }

    @Test
    @DisplayName("全集版范围外算法必拒：sha1/sha-256/大写 SHA1 均抛协议异常")
    void fullSetUnsupportedAlgRejected() {
        assertThatThrownBy(() -> JSecureUploadUtil.normalizeDigestAlg("sha1"))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("摘要算法不在允许范围");
        assertThatThrownBy(() -> JSecureUploadUtil.normalizeDigestAlg("sha-256"))
                .isInstanceOf(JSecureApiException.class);
        assertThatThrownBy(() -> JSecureUploadUtil.normalizeDigestAlg("SHA1"))
                .isInstanceOf(JSecureApiException.class);
    }

    // ------------------------------------------------------------------
    // 终端范围版：空白条目在受限范围下应拒（不是逃生门）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("终端配 [sm3] 时空白条目应拒：空白归一成 sha256 后不在允许范围")
    void blankEntryUnderSm3OnlyScopeRejected() {
        // 空白条目先归一为 sha256 再查范围——若实现把"空白"当成"跳过范围检查"的暗门，
        // 终端 [sm3] 限定即可被空白 digestAlg 绕过
        assertThatThrownBy(() -> JSecureUploadUtil.normalizeDigestAlg(null, List.of("sm3")))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("摘要算法不在允许范围")
                .hasMessageContaining("sm3");
        assertThatThrownBy(() -> JSecureUploadUtil.normalizeDigestAlg("   ", List.of("sm3")))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("摘要算法不在允许范围");
    }

    @Test
    @DisplayName("终端范围版大小写：条目 SM3 命中配置 [sm3]；配置 '  SM3  ' 同样归一后命中条目 sm3")
    void scopeCaseInsensitiveBothSides() {
        assertThat(JSecureUploadUtil.normalizeDigestAlg("SM3", List.of("sm3"))).isEqualTo("sm3");
        assertThat(JSecureUploadUtil.normalizeDigestAlg("sm3", List.of("  SM3  "))).isEqualTo("sm3");
        assertThat(JSecureUploadUtil.normalizeDigestAlg("Sha256", List.of("SHA256", "md5"))).isEqualTo("sha256");
    }

    @Test
    @DisplayName("空/null 范围 = 协议全集：sha256/sm3/md5 均放行")
    void emptyScopeMeansFullSet() {
        assertThat(JSecureUploadUtil.normalizeDigestAlg("sha256", null)).isEqualTo("sha256");
        assertThat(JSecureUploadUtil.normalizeDigestAlg("md5", List.of())).isEqualTo("md5");
        assertThat(JSecureUploadUtil.normalizeDigestAlg("sm3", null)).isEqualTo("sm3");
    }

    @Test
    @DisplayName("范围外算法必拒且消息带允许清单：终端 [sm3, md5] 下 sha256 被拒")
    void outOfScopeRejectedWithAllowList() {
        assertThatThrownBy(() -> JSecureUploadUtil.normalizeDigestAlg("sha256", List.of("sm3", "md5")))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("摘要算法不在允许范围")
                .hasMessageContaining("md5/sm3");
    }

    @Test
    @DisplayName("范围配置含拼写错误的合法名（[sha-256, sm3]）：sha256 不被放行（fail-closed，配置错误不静默扩权）")
    void typoedScopeEntryDoesNotGrantSha256() {
        assertThatThrownBy(() -> JSecureUploadUtil.normalizeDigestAlg("sha256", List.of("sha-256", "sm3")))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("摘要算法不在允许范围");
    }

    // ------------------------------------------------------------------
    // 红用例（缺陷证据）：范围配置含协议全集外的算法名时，元数据层不应放行
    // ------------------------------------------------------------------

    /**
     * 缺陷证据（转后端裁定）：终端范围版未与协议全集求交——配置 [sha1] 时，
     * 声明 sha1 的条目在元数据层被放行（本用例预期应拒，当前实现返回 "sha1" 而不抛异常）。
     * 后果：① digest-verify 开启时错误延后到内容层 digestHex 才爆，且消息自称"终端允许：md5/sha256/sm3"
     * （实为协议全集口径，误导排障）；② digest-verify 关闭时（verifyPushContents 提前 continue）
     * sha1 条目全链路受理，非法算法名进入业务数据。
     * 预期：normalizeDigestAlg("sha1", List.of("sha1")) 应抛 JSecureApiException
     * （算法须同时满足"协议全集"且"终端范围"，非法配置名视同拼写错误按 fail-closed 处理）。
     */
    @Test
    @DisplayName("红用例：范围配置含协议外算法名 [sha1] 时，sha1 条目应在归一化处被拒（当前实现在元数据层放行）")
    void bogusAlgInScopeShouldBeRejectedAtNormalize() {
        assertThatThrownBy(() -> JSecureUploadUtil.normalizeDigestAlg("sha1", List.of("sha1")))
                .as("协议外算法名即使被终端误配进范围，也不应通过归一化（与拼写错误同口径 fail-closed）")
                .isInstanceOf(JSecureApiException.class);
    }

    @Test
    @DisplayName("对照：范围 [md5, sha1] 下合法算法 md5 正常放行（非法名不应牵连同列表的合法名）")
    void validAlgCoexistingWithBogusEntryStillWorks() {
        assertThatCode(() -> JSecureUploadUtil.normalizeDigestAlg("md5", List.of("md5", "sha1")))
                .doesNotThrowAnyException();
        assertThat(JSecureUploadUtil.normalizeDigestAlg("md5", List.of("md5", "sha1"))).isEqualTo("md5");
    }
}
