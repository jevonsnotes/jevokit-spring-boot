package io.github.jevonsnotes.jevokit.okhttp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import okhttp3.RequestBody;

import java.io.File;

/**
 * @Description TODO
 * @Date 2024/9/4
 * @Version 0.0.1
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
