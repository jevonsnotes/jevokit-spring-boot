package io.github.jevonsnotes.jevokit.okhttp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import okhttp3.RequestBody;

import java.io.File;

 /**
 * multipart/form-data 单个分片的描述（文件或字段）：name 为分片字段名，file 与 mediaType
 * 成对用于文件分片（mediaType 缺省由 OkHttp 依文件名推断）。经 postFormDataPart 系列提交。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class FormDataPart {
    private String name;
    private String fileName;
    private File file;
    private String mediaType;
}
