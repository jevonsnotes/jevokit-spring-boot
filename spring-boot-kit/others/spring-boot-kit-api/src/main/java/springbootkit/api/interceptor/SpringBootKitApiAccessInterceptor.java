package springbootkit.api.interceptor;

import cn.hutool.core.text.AntPathMatcher;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import springbootkit.api.config.SpringBootKitApiAccessMapperProperties;
import springbootkit.api.config.SpringBootKitApiProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RequiredArgsConstructor
@Slf4j
public class SpringBootKitApiAccessInterceptor implements AsyncHandlerInterceptor {
    private AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final SpringBootKitApiProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 非控制层跳过
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // token校验

        for(SpringBootKitApiAccessMapperProperties mapper : properties.getAccess().getTokens()){
            if(antPathMatcher.match(mapper.getPath(), request.getServletPath())){
                String header = request.getHeader(properties.getAccess().getHeader());
                if( StrUtil.isBlank(header) || !header.equals(mapper.getToken())) {
                    log.info("{} 无访问权限,请配置访问密钥，并携带请求头{}访问  ！", request.getServletPath(),properties.getAccess().getHeader());
                    response.sendError(HttpStatus.FORBIDDEN.value());
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    }
}
