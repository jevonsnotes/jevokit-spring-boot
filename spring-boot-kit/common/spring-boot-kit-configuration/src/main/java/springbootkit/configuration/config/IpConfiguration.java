package springbootkit.configuration.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;

import java.net.*;
import java.util.Enumeration;

/**
 * @Description TODO
 * @Date 2022/6/29 16:54
 * @Version 0.0.1
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
