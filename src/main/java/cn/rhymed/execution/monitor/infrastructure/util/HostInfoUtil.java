package cn.rhymed.execution.monitor.infrastructure.util;

import cn.hutool.core.net.NetUtil;

/**
 * 主机信息工具类
 * 用于获取主机IP、主机名等信息
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class HostInfoUtil {

    private static final String LOCAL_IP = initLocalIp();
    private static final String HOST_NAME = initHostName();

    /**
     * 获取本机IP
     */
    public static String getLocalIp() {
        return LOCAL_IP;
    }

    /**
     * 获取主机名
     */
    public static String getHostName() {
        return HOST_NAME;
    }

    /**
     * 获取主机标识(格式: hostname/ip)
     */
    public static String getHostIdentifier() {
        return HOST_NAME + "/" + LOCAL_IP;
    }

    private static String initLocalIp() {
        try {
            return NetUtil.getLocalhostStr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String initHostName() {
        try {
            return NetUtil.getLocalHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
