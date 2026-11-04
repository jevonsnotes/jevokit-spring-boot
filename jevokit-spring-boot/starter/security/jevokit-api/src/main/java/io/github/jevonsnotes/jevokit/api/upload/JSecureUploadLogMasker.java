package io.github.jevonsnotes.jevokit.api.upload;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDetailDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 上传协议日志脱敏器：文件摘要清单（JSecureUploadDto）出日志前遮蔽敏感字段——
 * fetchToken 前 4 后 4 明文、中间掩码；url 保留 scheme://host/path、查询串整体遮蔽
 * （查询串常含签名/临时凭证）。由 {@code DecryptRequestAdviceAdapter} 的明文打印路径调用，
 * 业务对象本身不受影响（脱敏仅发生在日志副本上）。
 */
public final class JSecureUploadLogMasker {

    private JSecureUploadLogMasker() {
    }

    /**
     * 生成脱敏后的摘要清单 JSON（仅日志展示用，不回写业务对象）。
     *
     * @param upload 上传业务体
     * @return 脱敏副本的 JSON 串；入参为 null 返回 null
     */
    public static String maskedJson(JSecureUploadDto<?, ?> upload) {
        return upload == null ? null : JSON.toJSONString(maskedCopy(upload));
    }

    /**
     * 构造脱敏副本（逐条复制文件元数据，敏感字段遮蔽；业务数据对象原样引用——业务侧自负其脱敏）。
     *
     * @param upload 上传业务体
     * @return 脱敏后的新 DTO（原对象不变）
     */
    public static JSecureUploadDto<Object, JSecureUploadFileDetailDto> maskedCopy(JSecureUploadDto<?, ?> upload) {
        JSecureUploadDto<Object, JSecureUploadFileDetailDto> copy = new JSecureUploadDto<>();
        copy.setBusinessData(upload.getBusinessData());
        if (upload.getFileDetails() != null) {
            List<JSecureUploadFileDetailDto> files = new ArrayList<>(upload.getFileDetails().size());
            for (JSecureUploadFileDetailDto file : upload.getFileDetails()) {
                files.add(maskFile(file));
            }
            copy.setFileDetails(files);
        }
        return copy;
    }

    /**
     * 单条文件元数据脱敏副本。
     */
    private static JSecureUploadFileDetailDto maskFile(JSecureUploadFileDetailDto file) {
        JSecureUploadFileDetailDto copy = new JSecureUploadFileDetailDto();
        copy.setFileId(file.getFileId());
        copy.setFileType(file.getFileType());
        copy.setUsage(file.getUsage());
        copy.setFileName(file.getFileName());
        copy.setFormat(file.getFormat());
        copy.setSize(file.getSize());
        copy.setDigest(file.getDigest());
        copy.setDigestAlg(file.getDigestAlg());
        copy.setFetchMode(file.getFetchMode());
        copy.setUrl(maskUrl(file.getUrl()));
        copy.setFetchToken(maskToken(file.getFetchToken()));
        copy.setRemark(file.getRemark());
        copy.setExtra(file.getExtra());
        return copy;
    }

    /**
     * 令牌脱敏：前 4 后 4 明文、中间掩码；短值仅保留前 2 位防全文泄露。
     *
     * @param token 原始令牌
     * @return 脱敏值（空白原样返回）
     */
    public static String maskToken(String token) {
        if (StrUtil.isBlank(token)) {
            return token;
        }
        if (token.length() <= 8) {
            return StrUtil.subPre(token, 2) + "****";
        }
        return StrUtil.subPre(token, 4) + "****" + StrUtil.subSuf(token, token.length() - 4);
    }

    /**
     * URL 脱敏：保留 scheme://host/path，查询串（常含签名/临时凭证）整体遮蔽。
     *
     * @param url 原始 URL
     * @return 遮蔽查询串后的 URL（无查询串原样返回；非法 URL 返回占位符防原文外泄）
     */
    public static String maskUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return url;
        }
        int queryIndex = url.indexOf('?');
        if (queryIndex >= 0) {
            return url.substring(0, queryIndex) + "?***";
        }
        return url;
    }
}
