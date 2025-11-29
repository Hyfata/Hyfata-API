package kr.hyfata.rest.api.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IpUtilTest {

    private IpUtil ipUtil;

    @BeforeEach
    void setUp() {
        ipUtil = new IpUtil();
    }

    @Test
    @DisplayName("X-Forwarded-For 헤더에서 IP 추출")
    void getClientIp_fromXForwardedFor() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195");

        // when
        String result = ipUtil.getClientIp(request);

        // then
        assertThat(result).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("X-Forwarded-For 헤더 - 여러 IP가 있는 경우 첫 번째 IP 추출")
    void getClientIp_fromXForwardedFor_multipleIps() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");

        // when
        String result = ipUtil.getClientIp(request);

        // then
        assertThat(result).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("X-Real-IP 헤더에서 IP 추출")
    void getClientIp_fromXRealIp() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "192.168.1.100");

        // when
        String result = ipUtil.getClientIp(request);

        // then
        assertThat(result).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("헤더가 없는 경우 remoteAddr 반환")
    void getClientIp_fromRemoteAddr() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        // when
        String result = ipUtil.getClientIp(request);

        // then
        assertThat(result).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("null request는 'unknown' 반환")
    void getClientIp_nullRequest_returnsUnknown() {
        // when
        String result = ipUtil.getClientIp(null);

        // then
        assertThat(result).isEqualTo("unknown");
    }

    @Test
    @DisplayName("헤더 값이 'unknown'인 경우 다음 헤더 확인")
    void getClientIp_unknownHeader_checksNextHeader() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("X-Real-IP", "192.168.1.50");

        // when
        String result = ipUtil.getClientIp(request);

        // then
        assertThat(result).isEqualTo("192.168.1.50");
    }

    @Test
    @DisplayName("빈 헤더 값은 다음 헤더 확인")
    void getClientIp_emptyHeader_checksNextHeader() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "");
        request.addHeader("X-Real-IP", "192.168.1.75");

        // when
        String result = ipUtil.getClientIp(request);

        // then
        assertThat(result).isEqualTo("192.168.1.75");
    }

    @Test
    @DisplayName("IPv6 로컬호스트 정규화 (0:0:0:0:0:0:0:1)")
    void normalizeIp_ipv6Localhost_full() {
        // when
        String result = ipUtil.normalizeIp("0:0:0:0:0:0:0:1");

        // then
        assertThat(result).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("IPv6 로컬호스트 정규화 (::1)")
    void normalizeIp_ipv6Localhost_short() {
        // when
        String result = ipUtil.normalizeIp("::1");

        // then
        assertThat(result).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("일반 IPv4 주소는 그대로 반환")
    void normalizeIp_ipv4_unchanged() {
        // when
        String result = ipUtil.normalizeIp("192.168.1.100");

        // then
        assertThat(result).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("일반 IPv6 주소는 그대로 반환")
    void normalizeIp_ipv6_unchanged() {
        // given
        String ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";

        // when
        String result = ipUtil.normalizeIp(ipv6);

        // then
        assertThat(result).isEqualTo(ipv6);
    }

    @Test
    @DisplayName("Proxy-Client-IP 헤더에서 IP 추출")
    void getClientIp_fromProxyClientIp() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Proxy-Client-IP", "172.16.0.50");

        // when
        String result = ipUtil.getClientIp(request);

        // then
        assertThat(result).isEqualTo("172.16.0.50");
    }
}
