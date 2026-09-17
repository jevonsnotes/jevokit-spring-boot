package io.github.jevonsnotes.jevokit.configuration.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;

import java.net.*;
import java.util.Enumeration;

/**
 * 本机网络信息采集器：监听 WebServerInitializedEvent，在容器就绪后解析本服务对外的 IP 与端口，
 * 供审计日志、服务注册等需要上报自身地址的组件读取（getIp()/getPort()）。
 * <p>选网口规则偏向生产物理/虚拟网卡：仅认已启用、非回环、非点对点、非虚拟、名称以 ens 或 eth 开头的网口上的
 * 非 loopback IPv4 地址；一个都没有时退回 InetAddress.getLocalHost()，再失败返回 null。
 */
@Data
@Slf4j
public class IpConfiguration implements ApplicationListener<WebServerInitializedEvent> {
    private int port;
    private String ip;

    /**
     * Handle an application event.
     *
     * @param event the event to respond to
     */
    // 容器就绪回调：此时端口才真正确定，顺带解析本机对外 IP
    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.port = event.getWebServer().getPort();
        this.ip = getLocalIp();
    }

    private String getLocalIp() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            if (networkInterfaces != null) {
                while (networkInterfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = networkInterfaces.nextElement();
                    if (isValidNetworkInterface(networkInterface)) {
                        Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                        while (inetAddresses.hasMoreElements()) {
                            InetAddress inetAddress = inetAddresses.nextElement();
                            if (isValidAddress(inetAddress)) {
                                return inetAddress.getHostAddress();
                            }
                        }

                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException | SocketException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private boolean isValidNetworkInterface(NetworkInterface ni) throws SocketException {
        return ni != null && !ni.isVirtual() && ni.isUp() && !ni.isLoopback() && !ni.isPointToPoint()
                && (ni.getName().startsWith("ens") || ni.getName().startsWith("eth"));
    }

    private boolean isValidAddress(InetAddress ia) {
        return ia != null && ia instanceof Inet4Address && !ia.isLoopbackAddress();
    }
}
