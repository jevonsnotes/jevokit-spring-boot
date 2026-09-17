package io.github.jevonsnotes.jevokit.security.xss.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import io.github.jevonsnotes.jevokit.core.editor.StringPropertiesEditor;
import io.github.jevonsnotes.jevokit.security.xss.cleaner.XssCleaner;
/**
 * 表单提交处理器
 */
@ControllerAdvice
@ConditionalOnBean(XssCleaner.class)
@RequiredArgsConstructor
public class FormXssHandler {

    private final StringPropertiesEditor editor;
    /**
     * 为全部 String 属性注册清洗编辑器（表单参数 → Bean 绑定时的 XSS 清洗入口）。
     *
     * @param binder 当前请求的数据绑定器
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, editor);
    }

}
