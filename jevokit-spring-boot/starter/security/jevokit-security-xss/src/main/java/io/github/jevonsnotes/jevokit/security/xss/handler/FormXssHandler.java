package io.github.jevonsnotes.jevokit.security.xss.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import io.github.jevonsnotes.jevokit.core.editor.StringPropertiesEditor;
import io.github.jevonsnotes.jevokit.security.xss.cleaner.XssCleaner;
/**
 * @Description 表单提交处理器
 * @Date 2022/5/23 15:33
 * @Version 0.0.1
 */
@ControllerAdvice
@ConditionalOnBean(XssCleaner.class)
@RequiredArgsConstructor
public class FormXssHandler {

    private final StringPropertiesEditor editor;
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, editor);
    }

}
