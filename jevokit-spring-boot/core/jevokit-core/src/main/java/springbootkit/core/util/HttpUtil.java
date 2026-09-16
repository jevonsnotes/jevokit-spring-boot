package springbootkit.core.util;

import cn.hutool.core.util.StrUtil;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @Description TODO
 * @Date 2024/12/27
 * @Version 0.0.1
 */
public class HttpUtil extends cn.hutool.http.HttpUtil {
    /**
     * 获取请求主机IP地址,如果通过代理进来，则透过防火墙获取真实IP地址
     *
     * @param request
     * @return
     */
    public static String getRemoteIp(HttpServletRequest request) {
        if(request != null) {
            String ip = request.getHeader("X-Forwarded-For");
            // 提取第一个ip
            if(StrUtil.isNotBlank(ip)){
                ip = ip.split(",")[0];
            }

            if (isInvalidIP(ip)) {
                if (isInvalidIP(ip)) {
                    ip = request.getHeader("Proxy-Client-IP");
                }
                if (isInvalidIP(ip)) {
                    ip = request.getHeader("WL-Proxy-Client-IP");
                }
                if (isInvalidIP(ip)) {
                    ip = request.getHeader("HTTP_CLIENT_IP");
                }
                if (isInvalidIP(ip)) {
                    ip = request.getHeader("HTTP_X_FORWARDED_FOR");
                }

                if (isInvalidIP(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }

                if (isInvalidIP(ip)) {
                    ip = request.getRemoteAddr();
                }
            } else if (ip.length() > 15) {
                String[] ips = ip.split(",");
                for (String strIp : ips) {
                    if (!isInvalidIP(strIp)) {
                        ip = strIp;
                        break;
                    }
                }
            }
            return ip;
        }
        return StrUtil.EMPTY;
    }
    private static boolean isInvalidIP(String ip) {
        return StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip);
    }
}
