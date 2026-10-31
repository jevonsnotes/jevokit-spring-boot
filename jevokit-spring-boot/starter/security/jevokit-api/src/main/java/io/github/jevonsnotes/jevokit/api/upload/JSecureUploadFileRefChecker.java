package io.github.jevonsnotes.jevokit.api.upload;

import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDto;

/**
 * refer 模式文件存在性校验钩子：业务实现并注册为 Spring Bean 即被上传校验器发现
 * （ObjectProvider 按类型获取，无需额外配置）。
 *
 * <p>判定与返回语义：返回 true 表示该 refer 条目引用的文件在业务侧真实存在（如已入库的
 * fileId），放行；返回 false 拒绝该请求。容器中无任何实现 Bean 时，凡出现 refer 条目一律拒绝
 * （fail-fast，防止 refer 模式静默裸奔）。
 *
 * <p>实现示例（发票系统按 fileId 查附件表）：
 * <pre>{@code
 * @Component
 * public class InvoiceFileRefChecker implements JSecureUploadFileRefChecker {
 *     @Override
 *     public boolean exists(JSecureUploadFileDto file) {
 *         return invoiceAttachmentMapper.selectCount(file.getFileId()) > 0;
 *     }
 * }
 * }</pre>
 */
public interface JSecureUploadFileRefChecker {

    /**
     * 校验 refer 条目引用的文件是否存在。
     *
     * @param file 上传文件元数据条目（fileId 为业务侧引用键）
     * @return true 存在放行；false 不存在拒绝
     */
    boolean exists(JSecureUploadFileDto file);
}
