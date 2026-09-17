package io.github.jevonsnotes.jevokit.security.pt.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import io.github.jevonsnotes.jevokit.core.editor.StringPropertiesEditor;

/**
 * 表单提交处理器
 */
@ControllerAdvice
@RequiredArgsConstructor
public class FormPathTraversalHandler {
    private final StringPropertiesEditor stringPropertiesEditor;

    /**
     * 为全部 String 属性注册清洗编辑器（表单参数 → Bean 绑定时的路径穿越清洗入口）。
     *
     * @param binder 当前请求的数据绑定器
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, stringPropertiesEditor);
    }

}
