package springbootkit.security.pt.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import springbootkit.core.editor.StringPropertiesEditor;

/**
 * @Description 表单提交处理器
 * @Date 2022/5/23 15:33
 * @Version 0.0.1
 */
@ControllerAdvice
@RequiredArgsConstructor
public class FormPathTraversalHandler {
    private final StringPropertiesEditor stringPropertiesEditor;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, stringPropertiesEditor);
    }

}
