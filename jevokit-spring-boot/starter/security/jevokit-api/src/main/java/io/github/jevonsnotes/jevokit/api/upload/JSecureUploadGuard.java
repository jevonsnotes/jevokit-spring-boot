package io.github.jevonsnotes.jevokit.api.upload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;

/**
 * 上传裸奔检测防线（启动期）：扫描全部 handler 方法，发现 {@code @JSecureApi} 方法声明了
 * MultipartFile 参数却<b>没有</b> JSecureRequest 参数时启动即失败——文件 part 不走消息转换器，
 * 裸 MultipartFile 收参意味着文件既不在加密信封内、也无摘要校验（静默裸奔）。
 *
 * <p>修复指引（错误消息内同步给出）：改为 meta + files 双参数形态——
 * <pre>{@code
 * @JSecureApi
 * @PostMapping("/upload")
 * public JSecureResponse<String> upload(
 *         @RequestPart("meta") @Valid JSecureRequest<JSecureUploadDto<MyBiz, JSecureUploadFileDetailDto>> meta,
 *         @RequestPart("files") List<MultipartFile> files) { ... }
 * }</pre>
 * <p>实现说明：以 SmartInitializingSingleton 在单例装配完成后遍历
 * {@link RequestMappingHandlerMapping}（无 Web 环境——如纯工具上下文——无该 Bean 时跳过检测）。
 */
@Slf4j
@RequiredArgsConstructor
public class JSecureUploadGuard implements SmartInitializingSingleton {

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider;

    /** 启动期裸奔检测入口：任何违规方法直接抛 IllegalStateException 中断启动。 */
    @Override
    public void afterSingletonsInstantiated() {
        handlerMappingProvider.ifAvailable(handlerMapping -> checkHandlerMethods(handlerMapping.getHandlerMethods()));
    }

    /**
     * 校验全部 handler 方法（可见性设为包内以便单测直调）。
     *
     * @param handlerMethods 请求映射方法清单
     * @throws IllegalStateException 存在 @JSecureApi + 裸 MultipartFile 的方法时抛出（含修复指引）
     */
    void checkHandlerMethods(Map<?, ? extends HandlerMethod> handlerMethods) {
        handlerMethods.forEach((mapping, handlerMethod) -> {
            Method method = handlerMethod.getMethod();
            if (AnnotatedElementUtils.findMergedAnnotation(method, JSecureApi.class) == null) {
                return;
            }
            boolean hasSecureParam = Arrays.stream(method.getParameters())
                    .anyMatch(parameter -> JSecureRequest.class.isAssignableFrom(parameter.getType()));
            boolean hasFileParam = Arrays.stream(method.getParameters())
                    .anyMatch(parameter -> isMultipartFileParameter(parameter.getParameterizedType()));
            if (hasFileParam && !hasSecureParam) {
                throw new IllegalStateException(String.format(
                        "[jevokit-api] 裸奔检测不通过：%s#%s 声明了 @JSecureApi 但以裸 MultipartFile 收参——"
                                + "文件不在加密信封内且无摘要校验。请改为 meta+files 双参数形态："
                                + "@RequestPart(\"meta\") JSecureRequest<JSecureUploadDto<B, F>> meta + "
                                + "@RequestPart(\"files\") List<MultipartFile> files（meta 装加密信封与文件摘要清单，"
                                + "文件 part 明文传输、按摘要校验完整性，详见 jevokit-api README）",
                        method.getDeclaringClass().getName(), method.getName()));
            }
        });
    }

    /**
     * 判定参数类型是否 MultipartFile 形态：单文件、数组或集合元素。
     */
    private boolean isMultipartFileParameter(Type parameterizedType) {
        if (parameterizedType instanceof Class<?> clazz) {
            return MultipartFile.class.isAssignableFrom(clazz)
                    || (clazz.isArray() && MultipartFile.class.isAssignableFrom(clazz.getComponentType()));
        }
        if (parameterizedType instanceof ParameterizedType parameterized) {
            return Arrays.stream(parameterized.getActualTypeArguments())
                    .anyMatch(this::isMultipartFileParameter);
        }
        return false;
    }
}
