package kr.hyfata.rest.api.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * WebSocket(STOMP) CONNECT 시 JWT를 검증하는 인터셉터.
 * Resource Server와 동일한 JwtDecoder(RS256, SAS RSA 공개키)로 검증한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // WebSocket CONNECT 시 JWT 검증 및 Principal 설정
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = null;
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            if (token == null) {
                log.warn("No JWT token provided for WebSocket connection");
                throw new IllegalArgumentException("JWT token is required");
            }

            // JWT 토큰 검증 (서명 + 만료)
            try {
                Jwt jwt = jwtDecoder.decode(token);
                // email 클레임 우선, 없으면 sub (둘 다 사용자 이메일)
                String email = jwt.getClaimAsString("email");
                if (email == null) {
                    email = jwt.getSubject();
                }
                log.info("WebSocket connection authenticated for user: {}", email);

                // 검증된 사용자 정보를 Authentication에 설정
                // 이 Principal은 세션 전체에서 유지됨
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        new ArrayList<>()
                );
                accessor.setUser(authentication);
            } catch (JwtException e) {
                log.warn("Invalid JWT token provided for WebSocket connection: {}", e.getMessage());
                throw new IllegalArgumentException("Invalid JWT token");
            }
        }

        return message;
    }
}
