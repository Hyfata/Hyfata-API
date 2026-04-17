package kr.hyfata.rest.api.common.util;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ua_parser.Client;
import ua_parser.Parser;

/**
 * User-Agent 파싱을 통한 디바이스 정보 추출
 */
@Component
@Slf4j
public class DeviceDetector {

    private final Parser uaParser;

    public DeviceDetector() {
        this.uaParser = new Parser();
    }

    /**
     * 디바이스 정보 결과
     */
    @Data
    @Builder
    public static class DeviceInfo {
        private String deviceType;   // Desktop, Mobile, Tablet
        private String deviceName;   // "Chrome on Windows", "Safari on iPhone"
        private String browser;      // Chrome, Safari, Firefox
        private String browserVersion;
        private String os;           // Windows, macOS, iOS, Android
        private String osVersion;
    }

    /**
     * User-Agent 문자열을 파싱하여 디바이스 정보 반환
     */
    public DeviceInfo parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return DeviceInfo.builder()
                    .deviceType("Unknown")
                    .deviceName("Unknown Device")
                    .browser("Unknown")
                    .os("Unknown")
                    .build();
        }

        try {
            Client client = uaParser.parse(userAgent);

            String browser = client.userAgent.family;
            String browserVersion = formatVersion(
                    client.userAgent.major,
                    client.userAgent.minor
            );

            String os = client.os.family;
            String osVersion = formatVersion(
                    client.os.major,
                    client.os.minor
            );

            String deviceType = detectDeviceType(userAgent, client);
            String deviceName = formatDeviceName(browser, os, client.device.family);

            return DeviceInfo.builder()
                    .deviceType(deviceType)
                    .deviceName(deviceName)
                    .browser(browser)
                    .browserVersion(browserVersion)
                    .os(os)
                    .osVersion(osVersion)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse User-Agent: {}", e.getMessage());
            return DeviceInfo.builder()
                    .deviceType("Unknown")
                    .deviceName("Unknown Device")
                    .browser("Unknown")
                    .os("Unknown")
                    .build();
        }
    }

    /**
     * 디바이스 타입 감지 (Desktop, Mobile, Tablet)
     */
    private String detectDeviceType(String userAgent, Client client) {
        String ua = userAgent.toLowerCase();
        String device = client.device.family;

        // Tablet 감지
        if (ua.contains("tablet") || ua.contains("ipad") ||
                (ua.contains("android") && !ua.contains("mobile"))) {
            return "Tablet";
        }

        // Mobile 감지
        if (ua.contains("mobile") || ua.contains("iphone") ||
                ua.contains("android") || ua.contains("windows phone") ||
                "iPhone".equalsIgnoreCase(device) ||
                "Android".equalsIgnoreCase(device)) {
            return "Mobile";
        }

        // 기본값은 Desktop
        return "Desktop";
    }

    /**
     * 디바이스 이름 포맷팅 (예: "Chrome on Windows")
     */
    private String formatDeviceName(String browser, String os, String device) {
        if ("Other".equals(browser) || browser == null) {
            browser = "Unknown Browser";
        }
        if ("Other".equals(os) || os == null) {
            os = "Unknown OS";
        }

        // 특정 디바이스명이 있는 경우 (예: iPhone, iPad)
        if (device != null && !"Other".equals(device) && !"Spider".equals(device)) {
            return String.format("%s on %s", browser, device);
        }

        return String.format("%s on %s", browser, os);
    }

    /**
     * 버전 문자열 포맷팅
     */
    private String formatVersion(String major, String minor) {
        if (major == null) {
            return "";
        }
        if (minor == null || minor.isEmpty()) {
            return major;
        }
        return major + "." + minor;
    }
}
