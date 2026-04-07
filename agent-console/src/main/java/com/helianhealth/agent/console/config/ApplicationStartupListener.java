package com.helianhealth.agent.console.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * 应用启动监听器
 * 在应用启动完成后输出配置后台访问地址
 */
@Component
public class ApplicationStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationStartupListener.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("server.port", "8099");
        String contextPath = env.getProperty("server.servlet.context-path", "");

        String localUrl = "http://localhost:" + port + contextPath;
        String externalUrl = null;

        try {
            // 获取真实的网络IP地址（非回环地址）
            String hostAddress = null;
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                // 跳过未激活或回环接口
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                java.util.Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // 只获取IPv4地址，且不是回环地址
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        hostAddress = addr.getHostAddress();
                        break;
                    }
                }
                if (hostAddress != null) {
                    break;
                }
            }

            if (hostAddress != null) {
                externalUrl = "http://" + hostAddress + ":" + port + contextPath;
            }
        } catch (Exception e) {
            logger.warn("无法获取本机IP地址");
        }

        logger.info("\n" +
                "  ___       _             __                  ___                    _   \n" +
                " |_ _|_ __ | |_ ___ _ __ / _| __ _  ___ ___  / _ \\    __ _  ___ _ __ | |_ \n" +
                "  | || '_ \\| __/ _ \\ '__| |_ / _` |/ __/ _ \\/ /_\\/   / _` |/ _ \\ '_ \\| __|\n" +
                "  | || | | | ||  __/ |  |  _| (_| | (_|  __/ /_\\\\   | (_| |  __/ | | | |_ \n" +
                " |___|_| |_|\\__\\___|_|  |_|  \\__,_|\\___\\___\\____/    \\__, |\\___|_| |_|\\__|\n" +
                "                                                           |___/                \n");

        logger.info("\n----------------------------------------------------------\n" +
                "  配置后台访问地址：\n" +
                "    本地访问: {}\n" +
                (externalUrl != null ? "    外部访问: {}\n" : "") +
                "----------------------------------------------------------",
                localUrl,
                externalUrl != null ? externalUrl : "");
    }
}
