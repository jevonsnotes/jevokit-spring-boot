package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.core.util.StrUtil;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP 服务端工具，在 hutool HttpUtil 基础上补充请求侧信息提取。
 * <p>常用入口：{@link #getRemoteIp(HttpServletRequest)}，经反向代理/CDN 的请求取客户端真实 IP。
 * <p>取值顺序（信任级别依次降低）：X-Forwarded-For（多级代理取第一段）→ Proxy-Client-IP →
 * WL-Proxy-Client-IP → HTTP_CLIENT_IP → HTTP_X_FORWARDED_FOR → X-Real-IP → remoteAddr；
 * 空值与 "unknown" 视为无效继续下探。注意各转发头可被客户端伪造，仅适合日志/展示类用途，勿直接用于鉴权决策。
 */
public class HttpUtil extends cn.hutool.http.HttpUtil {
    /**
     * 获取请求主机IP地址,如果通过代理进来，则透过防火墙获取真实IP地址
     *
     * @param request HTTP 请求；null 时返回空串（防御性降级）
     * @return 客户端 IP；全部探测头无效时返回 remoteAddr，request 为 null 时返回空串
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
    /**
     * 判断探测到的 IP 是否无效（空白或占位值 "unknown"）
     *
     * @param ip 候选 IP
     * @return 无效返回 true（继续尝试下一个探测头）
     */
    private static boolean isInvalidIP(String ip) {
        return StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip);
    }
}
