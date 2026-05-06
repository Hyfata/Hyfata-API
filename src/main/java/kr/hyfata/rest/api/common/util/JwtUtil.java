package kr.hyfata.rest.api.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kr.hyfata.rest.api.auth.entity.User;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;

@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret:hyfata-secret-key-min-32-characters-required-for-security}")
    private String jwtSecret;

    @Getter
    @Value("${jwt.expiration:900000}") // 15 minutes
    private long jwtExpiration;

    @Getter
    @Value("${jwt.refresh-expiration:1209600000}") // 14 days
    private long refreshTokenExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Access Token 생성 결과 (토큰 + JTI)
     */
    public record TokenResult(String token, String jti) {}

    /**
     * Access Token 생성 (JTI 포함, 기본)
     */
    public TokenResult generateAccessTokenWithJti(UserDetails userDetails) {
        return generateAccessTokenWithJti(userDetails, null, null);
    }

    /**
     * Access Token 생성 (JTI + client_id + scope 포함)
     */
    public TokenResult generateAccessTokenWithJti(UserDetails userDetails, String clientId, Set<String> scopes) {
        Map<String, Object> claims = new HashMap<>();

        String email = (userDetails instanceof User)
            ? ((User) userDetails).getEmail()
            : userDetails.getUsername();

        String jti = UUID.randomUUID().toString().replace("-", "");
        claims.put("email", email);
        claims.put("jti", jti);

        if (clientId != null && !clientId.isEmpty()) {
            claims.put("client_id", clientId);
        }

        if (scopes != null && !scopes.isEmpty()) {
            claims.put("scope", String.join(" ", scopes));
        }

        String token = createToken(claims, email, jwtExpiration);
        return new TokenResult(token, jti);
    }

    /**
     * Access Token 생성 (기존 호환성 유지)
     */
    public String generateAccessToken(UserDetails userDetails) {
        return generateAccessTokenWithJti(userDetails).token();
    }

    /**
     * Refresh Token 생성
     */
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();

        String email = (userDetails instanceof User)
            ? ((User) userDetails).getEmail()
            : userDetails.getUsername();

        return createToken(claims, email, refreshTokenExpiration);
    }

    /**
     * 토큰 생성
     */
    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 토큰에서 이메일 추출
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * 토큰에서 JTI 추출
     */
    public String extractJti(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("jti", String.class);
        } catch (Exception e) {
            log.error("Error extracting JTI: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토큰에서 client_id 추출
     */
    public String extractClientId(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("client_id", String.class);
        } catch (Exception e) {
            log.error("Error extracting client_id: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토큰에서 scope 추출
     */
    public Set<String> extractScopes(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String scopeStr = claims.get("scope", String.class);
            if (scopeStr == null || scopeStr.isEmpty()) {
                return Set.of();
            }
            return new HashSet<>(Arrays.asList(scopeStr.split(" ")));
        } catch (Exception e) {
            log.error("Error extracting scopes: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * 토큰에서 만료 날짜 추출
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * 토큰의 남은 유효 시간(초) 반환
     */
    public long getRemainingSeconds(String token) {
        try {
            Date expiration = extractExpiration(token);
            long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(0, remaining);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 토큰에서 특정 클레임 추출
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 토큰에서 모든 클레임 추출
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 토큰 만료 여부 확인
     */
    private Boolean isTokenExpired(String token) {
        try {
            final Date expiration = extractExpiration(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            log.error("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 토큰 유효성 검증
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String email = extractEmail(token);
            return (email.equals(userDetails.getUsername()) && !isTokenExpired(token));
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 토큰 유효성 검증 (기본)
     */
    public Boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
}
