package kr.hyfata.rest.api.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceDetectorTest {

    private DeviceDetector deviceDetector;

    @BeforeEach
    void setUp() {
        deviceDetector = new DeviceDetector();
    }

    @Test
    @DisplayName("Chrome on Windows 파싱")
    void parse_chromeOnWindows() {
        // given
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

        // when
        DeviceDetector.DeviceInfo result = deviceDetector.parse(userAgent);

        // then
        assertThat(result.getDeviceType()).isEqualTo("Desktop");
        assertThat(result.getBrowser()).isEqualTo("Chrome");
        assertThat(result.getOs()).isEqualTo("Windows");
    }

    @Test
    @DisplayName("Safari on macOS 파싱")
    void parse_safariOnMacOS() {
        // given
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Safari/605.1.15";

        // when
        DeviceDetector.DeviceInfo result = deviceDetector.parse(userAgent);

        // then
        assertThat(result.getDeviceType()).isEqualTo("Desktop");
        assertThat(result.getBrowser()).isEqualTo("Safari");
        assertThat(result.getOs()).isEqualTo("Mac OS X");
    }

    @Test
    @DisplayName("Safari on iPhone 파싱 (Mobile)")
    void parse_safariOnIphone() {
        // given
        String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Mobile/15E148 Safari/604.1";

        // when
        DeviceDetector.DeviceInfo result = deviceDetector.parse(userAgent);

        // then
        assertThat(result.getDeviceType()).isEqualTo("Mobile");
        assertThat(result.getBrowser()).isEqualTo("Mobile Safari");
        assertThat(result.getOs()).isEqualTo("iOS");
    }

    @Test
    @DisplayName("Chrome on Android Mobile 파싱")
    void parse_chromeOnAndroidMobile() {
        // given
        String userAgent = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36";

        // when
        DeviceDetector.DeviceInfo result = deviceDetector.parse(userAgent);

        // then
        assertThat(result.getDeviceType()).isEqualTo("Mobile");
        assertThat(result.getBrowser()).isEqualTo("Chrome Mobile");
        assertThat(result.getOs()).isEqualTo("Android");
    }

    @Test
    @DisplayName("Safari on iPad 파싱 (Tablet)")
    void parse_safariOnIpad() {
        // given
        String userAgent = "Mozilla/5.0 (iPad; CPU OS 17_2_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Mobile/15E148 Safari/604.1";

        // when
        DeviceDetector.DeviceInfo result = deviceDetector.parse(userAgent);

        // then
        assertThat(result.getDeviceType()).isEqualTo("Tablet");
        assertThat(result.getBrowser()).isEqualTo("Mobile Safari");
    }

    @Test
    @DisplayName("Android Tablet 파싱")
    void parse_androidTablet() {
        // given
        String userAgent = "Mozilla/5.0 (Linux; Android 13; SM-X700) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.210 Safari/537.36";

        // when
        DeviceDetector.DeviceInfo result = deviceDetector.parse(userAgent);

        // then
        assertThat(result.getDeviceType()).isEqualTo("Tablet");
        assertThat(result.getOs()).isEqualTo("Android");
    }

    @Test
    @DisplayName("Firefox on Linux 파싱")
    void parse_firefoxOnLinux() {
        // given
        String userAgent = "Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0";

        // when
        DeviceDetector.DeviceInfo result = deviceDetector.parse(userAgent);

        // then
        assertThat(result.getDeviceType()).isEqualTo("Desktop");
        assertThat(result.getBrowser()).isEqualTo("Firefox");
        assertThat(result.getOs()).isEqualTo("Linux");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("null 또는 빈 User-Agent는 Unknown 반환")
    void parse_nullOrEmptyUserAgent_returnsUnknown(String userAgent) {
        // when
        DeviceDetector.DeviceInfo result = deviceDetector.parse(userAgent);

        // then
        assertThat(result.getDeviceType()).isEqualTo("Unknown");
        assertThat(result.getDeviceName()).isEqualTo("Unknown Device");
        assertThat(result.getBrowser()).isEqualTo("Unknown");
        assertThat(result.getOs()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("디바이스 이름 포맷 확인 (Browser on OS)")
    void parse_deviceNameFormat() {
        // given
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

        // when
        DeviceDetector.DeviceInfo result = deviceDetector.parse(userAgent);

        // then
        assertThat(result.getDeviceName()).contains("Chrome");
        assertThat(result.getDeviceName()).contains(" on ");
    }

    @Test
    @DisplayName("브라우저 버전 파싱")
    void parse_browserVersion() {
        // given
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

        // when
        DeviceDetector.DeviceInfo result = deviceDetector.parse(userAgent);

        // then
        assertThat(result.getBrowserVersion()).isNotEmpty();
        assertThat(result.getBrowserVersion()).startsWith("120");
    }

    @Test
    @DisplayName("OS 버전 파싱")
    void parse_osVersion() {
        // given
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

        // when
        DeviceDetector.DeviceInfo result = deviceDetector.parse(userAgent);

        // then
        assertThat(result.getOsVersion()).isNotEmpty();
    }
}
