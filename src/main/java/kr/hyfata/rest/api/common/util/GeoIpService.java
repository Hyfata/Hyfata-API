package kr.hyfata.rest.api.common.util;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetAddress;

/**
 * GeoIP를 사용한 IP → 위치 변환 서비스
 * MaxMind GeoLite2-City 데이터베이스 사용
 */
@Component
@Slf4j
public class GeoIpService {

    @Value("${geoip.database-path:./GeoLite2-City.mmdb}")
    private String databasePath;

    @Value("${geoip.enabled:false}")
    private boolean enabled;

    private DatabaseReader databaseReader;
    private boolean initialized = false;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("GeoIP service is disabled");
            return;
        }

        try {
            File database = new File(databasePath);
            if (!database.exists()) {
                log.warn("GeoIP database not found at: {}. Location resolution will be disabled.", databasePath);
                return;
            }

            databaseReader = new DatabaseReader.Builder(database).build();
            initialized = true;
            log.info("GeoIP service initialized successfully with database: {}", databasePath);
        } catch (Exception e) {
            log.error("Failed to initialize GeoIP service: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (databaseReader != null) {
            try {
                databaseReader.close();
            } catch (Exception e) {
                log.error("Error closing GeoIP database: {}", e.getMessage());
            }
        }
    }

    /**
     * IP 주소로부터 위치 정보 조회
     * @param ipAddress IP 주소
     * @return 위치 문자열 (예: "Seoul, South Korea") 또는 null
     */
    public String resolveLocation(String ipAddress) {
        if (!initialized || !enabled) {
            return null;
        }

        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }

        // 로컬 IP는 조회하지 않음
        if (isLocalIp(ipAddress)) {
            return "Local";
        }

        try {
            InetAddress inetAddress = InetAddress.getByName(ipAddress);
            CityResponse response = databaseReader.city(inetAddress);

            String city = response.getCity().getName();
            String country = response.getCountry().getName();

            if (city != null && country != null) {
                return city + ", " + country;
            } else if (country != null) {
                return country;
            } else if (city != null) {
                return city;
            }

            return null;
        } catch (Exception e) {
            log.debug("Could not resolve location for IP {}: {}", ipAddress, e.getMessage());
            return null;
        }
    }

    /**
     * 로컬 IP 주소인지 확인
     */
    private boolean isLocalIp(String ip) {
        return ip.equals("127.0.0.1") ||
                ip.equals("0:0:0:0:0:0:0:1") ||
                ip.equals("::1") ||
                ip.startsWith("192.168.") ||
                ip.startsWith("10.") ||
                ip.startsWith("172.16.") ||
                ip.startsWith("172.17.") ||
                ip.startsWith("172.18.") ||
                ip.startsWith("172.19.") ||
                ip.startsWith("172.20.") ||
                ip.startsWith("172.21.") ||
                ip.startsWith("172.22.") ||
                ip.startsWith("172.23.") ||
                ip.startsWith("172.24.") ||
                ip.startsWith("172.25.") ||
                ip.startsWith("172.26.") ||
                ip.startsWith("172.27.") ||
                ip.startsWith("172.28.") ||
                ip.startsWith("172.29.") ||
                ip.startsWith("172.30.") ||
                ip.startsWith("172.31.");
    }

    /**
     * GeoIP 서비스가 사용 가능한지 확인
     */
    public boolean isAvailable() {
        return initialized && enabled;
    }
}
