package springbootkit.security.pt.interceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import springbootkit.configuration.annotation.PtClean;
import springbootkit.configuration.security.pathtraversal.PathTraversalMeta;
import springbootkit.configuration.security.pathtraversal.PathTraversalProperties;
import springbootkit.configuration.holder.ConfigHolder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RequiredArgsConstructor
public class PathTraversalInterceptor implements AsyncHandlerInterceptor {
    private final PathTraversalProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 非控制层跳过
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // 开关校验
        if (!properties.isEnabled()) {
            return true;
        }
        // 忽略（方法级覆盖类级，Spring 惯例；方法无注解时回退类级）
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        PtClean ptClean = handlerMethod.getMethodAnnotation(PtClean.class);
        if (ptClean == null) {
            ptClean = handlerMethod.getMethod().getDeclaringClass().getAnnotation(PtClean.class);
        }

        if (ptClean == null || !ptClean.disabled()) {
            PathTraversalMeta meta = ConfigHolder.getPtMeta();
            meta.setMode(properties.getMode());
            meta.setEnabled(Boolean.TRUE);

            if (ptClean != null){
                if(ptClean.mode().length>0) {
                    ConfigHolder.getPtMeta().setMode(ptClean.mode()[0]);
                }
            }

        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        ConfigHolder.removePt();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        ConfigHolder.removePt();
    }
}
