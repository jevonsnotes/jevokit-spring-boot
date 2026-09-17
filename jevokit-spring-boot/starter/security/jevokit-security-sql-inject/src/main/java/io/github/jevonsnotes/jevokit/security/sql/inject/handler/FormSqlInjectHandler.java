package io.github.jevonsnotes.jevokit.security.sql.inject.handler;

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
public class FormSqlInjectHandler {
    private final StringPropertiesEditor stringPropertiesEditor;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, stringPropertiesEditor);
    }

}
