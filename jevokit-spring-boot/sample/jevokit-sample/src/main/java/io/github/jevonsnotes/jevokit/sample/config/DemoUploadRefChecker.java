package io.github.jevonsnotes.jevokit.sample.config;

import org.springframework.stereotype.Component;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadFileRefChecker;

import java.util.Set;

/**
 * refer 模式存在性钩子演示实现：容器内存在该 Bean 后，refer 条目按它判定存在性
 * （演示口径：archived-001/ archived-002 视为已归档存在）。
 * 生产实现应替换为按 fileId 查询真实存储（数据库/对象存储索引）。
 */
@Component
public class DemoUploadRefChecker implements JSecureUploadFileRefChecker {

    /** 演示已归档文件标识（生产必须替换为真实存储查询） */
    private static final Set<String> ARCHIVED_FILE_IDS = Set.of("archived-001", "archived-002");

    @Override
    public boolean exists(io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDetailDto file) {
        return ARCHIVED_FILE_IDS.contains(file.getFileId());
    }
}
