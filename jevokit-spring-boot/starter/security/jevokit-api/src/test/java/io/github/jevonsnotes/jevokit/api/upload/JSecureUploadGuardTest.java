package io.github.jevonsnotes.jevokit.api.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDetailDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 裸奔检测防线单元测试：@JSecureApi + 裸 MultipartFile（单值/List/数组）启动即失败，
 * meta+files 双参数与普通（非 JSecure）multipart 端点不受影响。
 */
@DisplayName("上传裸奔检测防线")
class JSecureUploadGuardTest {

    @Test
    @DisplayName("裸单值 MultipartFile 参数：启动失败并给出修复指引")
    void bareSingleFileParamFailsStartup() {
        assertThatThrownBy(() -> check(BareSingleFileController.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("裸奔检测不通过")
                .hasMessageContaining("meta+files");
    }

    @Test
    @DisplayName("裸 List<MultipartFile> 参数：启动失败")
    void bareListFileParamFailsStartup() {
        assertThatThrownBy(() -> check(BareListFileController.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("裸奔检测不通过");
    }

    @Test
    @DisplayName("裸 MultipartFile[] 参数：启动失败")
    void bareArrayFileParamFailsStartup() {
        assertThatThrownBy(() -> check(BareArrayFileController.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("裸奔检测不通过");
    }

    @Test
    @DisplayName("meta+files 双参数形态：通过（文件走加密信封 + 摘要校验）")
    void dualParamFormPasses() {
        assertThatCode(() -> check(DualParamController.class)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非 JSecure 的普通 multipart 端点：不受防线影响")
    void plainMultipartEndpointPasses() {
        assertThatCode(() -> check(PlainFileController.class)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("无 handler 映射（非 Web 环境）：跳过检测不报错")
    void skipsWhenNoHandlerMapping() {
        new JSecureUploadGuard(new DefaultListableBeanFactory()
                .getBeanProvider(RequestMappingHandlerMapping.class)).afterSingletonsInstantiated();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 以 StaticApplicationContext 构造真实 RequestMappingHandlerMapping 并执行检测。 */
    private void check(Class<?>... controllers) throws Exception {
        StaticApplicationContext context = new StaticApplicationContext();
        for (Class<?> controller : controllers) {
            context.registerBean(controller.getSimpleName(), controller);
        }
        context.refresh();
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        mapping.setApplicationContext(context);
        mapping.afterPropertiesSet();
        new JSecureUploadGuard(new SimpleProvider(mapping)).afterSingletonsInstantiated();
    }

    /** 简单 ObjectProvider 包装（仅单例）。 */
    private static final class SimpleProvider implements org.springframework.beans.factory.ObjectProvider<RequestMappingHandlerMapping> {
        private final RequestMappingHandlerMapping mapping;

        private SimpleProvider(RequestMappingHandlerMapping mapping) {
            this.mapping = mapping;
        }

        @Override
        public RequestMappingHandlerMapping getObject(Object... args) {
            return mapping;
        }

        @Override
        public RequestMappingHandlerMapping getObject() {
            return mapping;
        }

        @Override
        public RequestMappingHandlerMapping getIfAvailable() {
            return mapping;
        }

        @Override
        public RequestMappingHandlerMapping getIfUnique() {
            return mapping;
        }
    }

    // ------------------------------------------------------------------
    // 受测控制器形态
    // ------------------------------------------------------------------

    @RestController
    static class BareSingleFileController {
        @JSecureApi
        @PostMapping("/bare-single")
        public String handle(MultipartFile file) {
            return "bare";
        }
    }

    @RestController
    static class BareListFileController {
        @JSecureApi
        @PostMapping("/bare-list")
        public String handle(List<MultipartFile> files) {
            return "bare";
        }
    }

    @RestController
    static class BareArrayFileController {
        @JSecureApi
        @PostMapping("/bare-array")
        public String handle(MultipartFile[] files) {
            return "bare";
        }
    }

    @RestController
    static class DualParamController {
        @JSecureApi
        @PostMapping("/dual")
        public JSecureResponse<String> handle(
                @RequestPart("meta") JSecureRequest<JSecureUploadDto<String, JSecureUploadFileDetailDto>> meta,
                @RequestPart("files") List<MultipartFile> files) {
            return JSecureResponse.ok("ok");
        }
    }

    @RestController
    static class PlainFileController {
        @PostMapping("/plain")
        public String handle(List<MultipartFile> files) {
            return "plain";
        }
    }
}
