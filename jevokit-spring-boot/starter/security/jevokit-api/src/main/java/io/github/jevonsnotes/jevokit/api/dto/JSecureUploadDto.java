package io.github.jevonsnotes.jevokit.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * JSecure 上传协议的业务体：业务数据 + 文件明细清单（装在 {@link JSecureRequest} 信封内，
 * 经 SM4 加密为 meta part 随 multipart 传输）。
 *
 * <p>字段命名口径：本类的 {@code fileDetails} 是信封明文里的**文件明细清单**（元数据），
 * 与 multipart 的**文件 part**（part 名固定 {@code files}，承载文件字节）是两个概念——
 * 前者按 {@code fileId} 与后者逐条对齐。
 *
 * <p>双泛型形态：{@code B} 为业务数据类型（任意 DTO 或 Map 零类接入），{@code F} 为文件条目类型
 * （须继承 {@link JSecureUploadFileDetailDto}，业务可扩展自有字段；无扩展需求时直接用
 * {@code JSecureUploadDto<MyBiz, JSecureUploadFileDetailDto>}）。
 *
 * <p>Controller 声明形态（meta + files 双参数）：
 * <pre>{@code
 * @JSecureApi
 * @PostMapping("/upload")
 * public JSecureResponse<String> upload(
 *         @RequestPart("meta") @Valid JSecureRequest<JSecureUploadDto<OrderBiz, JSecureUploadFileDetailDto>> meta,
 *         @RequestPart("files") List<MultipartFile> files) {
 *     // meta.getTarget().getFileDetails() 是已通过协议校验的文件明细清单
 * }
 * }</pre>
 * 服务端在解密 advice 的 afterBodyRead 阶段无感完成元数据层 + 内容层校验，业务进入时校验已通过。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JSecureUploadDto<B, F extends JSecureUploadFileDetailDto> {

    /** 业务数据（任意类型，与文件明细清单一并走 SM4 加密信封）。 */
    private B businessData;

    /** 文件明细清单：push 条目须与 multipart 文件 part（part 名 files）按 filename=fileId 一一对齐。 */
    private List<F> fileDetails;
}
