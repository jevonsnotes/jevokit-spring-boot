package io.github.jevonsnotes.jevokit.api.upload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.util.unit.DataSize;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 上传配置启动期顾问：把"评估后关闭"的弱化决策与配平失当留痕成启动 WARN（不阻断启动）。
 * 检查项（逐终端）：
 * <ul>
 *   <li>{@code upload-digest-verify=false} / {@code upload-size-verify=false}——内容校验弱化，
 *       双关时提示"无任何内容完整性校验"；</li>
 *   <li>{@code upload-digest-algorithms} 含协议全集外算法名——笔误使允许集静默收窄（fail-closed
 *       方向无安全洞，但该终端上传会莫名被拒，排障代价高）；</li>
 *   <li>容器 {@code spring.servlet.multipart.max-file-size} &gt; 终端 {@code upload-max-size}——
 *       配平失当：容器先把超限字节全额落临时盘、协议层才拒绝，资源消耗面=容器限值而非协议上限
 *       （原则：max-file-size ≤ upload-max-size）。</li>
 * </ul>
 * 校验逻辑抽为 {@link #collectWarnings}（纯函数），便于单测断言。
 */
@Slf4j
@RequiredArgsConstructor
public class JSecureUploadStartupAdvisor implements SmartInitializingSingleton {

    private final JevokitApiProperties properties;
    private final ObjectProvider<MultipartProperties> multipartProperties;

    @Override
    public void afterSingletonsInstantiated() {
        DataSize containerMaxFileSize = multipartProperties.getIfAvailable() != null
                ? multipartProperties.getIfAvailable().getMaxFileSize()
                : null;
        for (String warning : collectWarnings(properties.getClient(), containerMaxFileSize)) {
            log.warn("JSecure 上传配置告警：{}", warning);
        }
    }

    /**
     * 收集全部启动告警文案（纯函数，单测入口）。
     *
     * @param clients             终端配置列表
     * @param containerMaxFileSize 容器 multipart 单文件上限（无 servlet 环境为 null，跳过该项）
     * @return 告警文案列表（空 = 无告警）
     */
    static List<String> collectWarnings(List<JevokitApiClientProperties> clients, DataSize containerMaxFileSize) {
        List<String> warnings = new ArrayList<>();
        if (clients == null) {
            return warnings;
        }
        for (JevokitApiClientProperties client : clients) {
            String id = client.getId();
            boolean digestOff = !client.isUploadDigestVerify();
            boolean sizeOff = !client.isUploadSizeVerify();
            if (digestOff && sizeOff) {
                warnings.add(String.format(
                        "终端[%s]：upload-digest-verify 与 upload-size-verify 均为 false——文件内容无任何完整性校验（协议层仅剩元数据/结构/上限防线），确认这是评估后的决策", id));
            } else if (digestOff) {
                warnings.add(String.format(
                        "终端[%s]：upload-digest-verify=false——跳过 push 摘要比对、pull 拉取复核与 digest 必填（upload-max-size 上限不受豁免）", id));
            } else if (sizeOff) {
                warnings.add(String.format(
                        "终端[%s]：upload-size-verify=false——跳过 size 必填与\"声明=实际\"比对（upload-max-size 上限不受豁免）", id));
            }
            List<String> bogusAlgs = client.getUploadDigestAlgorithms() == null ? List.of()
                    : client.getUploadDigestAlgorithms().stream()
                            .filter(alg -> !JSecureUploadUtil.SUPPORTED_DIGEST_ALGS.contains(
                                    alg == null ? "" : alg.trim().toLowerCase(java.util.Locale.ROOT)))
                            .toList();
            if (!bogusAlgs.isEmpty()) {
                warnings.add(String.format(
                        "终端[%s]：upload-digest-algorithms 含协议全集外算法名 %s（全集 %s）——笔误会使允许集静默收窄，该终端相关上传将被拒",
                        id, bogusAlgs, JSecureUploadUtil.SUPPORTED_DIGEST_ALGS));
            }
            if (containerMaxFileSize != null && containerMaxFileSize.toBytes() > client.getUploadMaxSize()) {
                warnings.add(String.format(
                        "终端[%s]：容器 spring.servlet.multipart.max-file-size(%s) > upload-max-size(%s)——容器会先把超限字节全额落临时盘、协议层才拒绝（资源消耗面=容器限值），应配平为 max-file-size ≤ upload-max-size",
                        id, humanize(containerMaxFileSize.toBytes()), humanize(client.getUploadMaxSize())));
            }
        }
        return warnings;
    }

    /** 字节数人性化（≥1MB 以 MB 计，保留必要精度；不足 1MB 以 B 计）。 */
    private static String humanize(long bytes) {
        return bytes >= 1024 * 1024
                ? String.format("%.0fMB", bytes / 1024.0 / 1024.0)
                : bytes + "B";
    }
}
