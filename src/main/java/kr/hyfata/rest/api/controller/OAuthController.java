package kr.hyfata.rest.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.dto.OAuthTokenResponse;
import kr.hyfata.rest.api.dto.RegisterRequest;
import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.repository.UserRepository;
import kr.hyfata.rest.api.service.AuthService;
import kr.hyfata.rest.api.service.ClientService;
import kr.hyfata.rest.api.service.OAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * OAuth 2.0 Authorization Code Flow 구현
 * Google OAuth, Discord OAuth와 유사한 구조
 */
@Controller
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Slf4j
public class OAuthController {

    private final OAuthService oAuthService;
    private final ClientService clientService;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 1단계: Authorization 요청
     * GET /oauth/authorize?client_id=xxx&redirect_uri=xxx&state=xxx&response_type=code&code_challenge=xxx&code_challenge_method=xxx
     *
     * 예시 (PKCE 없음):
     * https://api.hyfata.com/oauth/authorize?
     *   client_id=client_001&
     *   redirect_uri=https://site1.com/callback&
     *   state=random_state_123&
     *   response_type=code
     *
     * 예시 (PKCE 포함 - Flutter 앱):
     * https://api.hyfata.com/oauth/authorize?
     *   client_id=client_001&
     *   redirect_uri=https://site1.com/callback&
     *   state=random_state_123&
     *   response_type=code&
     *   code_challenge=E9Mrozoa2owUzA7VLHwAIAKllCOvtQyen8P0xWXomaQ&
     *   code_challenge_method=S256
     */
    @GetMapping("/authorize")
    public String authorize(
            @RequestParam String client_id,
            @RequestParam String redirect_uri,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "code") String response_type,
            @RequestParam(required = false) String code_challenge,
            @RequestParam(required = false) String code_challenge_method,
            Model model) {

        try {
            // 클라이언트 검증
            var clientOpt = clientService.validateClient(client_id);
            if (clientOpt.isEmpty()) {
                model.addAttribute("error", "Invalid client");
                return "oauth/error";
            }

            // Redirect URI 검증
            if (!oAuthService.validateRedirectUri(client_id, redirect_uri)) {
                model.addAttribute("error", "Invalid redirect URI");
                return "oauth/error";
            }

            // response_type이 code가 아니면 거부
            if (!"code".equals(response_type)) {
                model.addAttribute("error", "Unsupported response_type. Only 'code' is supported");
                return "oauth/error";
            }

            // State 파라미터가 없으면 생성 (CSRF 방지)
            if (state == null || state.isEmpty()) {
                state = UUID.randomUUID().toString();
            }

            // 로그인 페이지로 이동 (state와 클라이언트 정보 전달)
            model.addAttribute("client_id", client_id);
            model.addAttribute("client_name", clientOpt.get().getName());
            model.addAttribute("redirect_uri", redirect_uri);
            model.addAttribute("state", state);

            // PKCE 파라미터 전달
            if (code_challenge != null && !code_challenge.isEmpty()) {
                model.addAttribute("code_challenge", code_challenge);
                model.addAttribute("code_challenge_method", code_challenge_method != null ? code_challenge_method : "S256");
                log.info("Authorization request with PKCE: client_id={}, method={}", client_id, code_challenge_method);
            } else {
                log.info("Authorization request: client_id={}, redirect_uri={}", client_id, redirect_uri);
            }

            return "oauth/login";  // Thymeleaf 템플릿

        } catch (Exception e) {
            log.error("Authorization error: {}", e.getMessage());
            model.addAttribute("error", "Authorization failed: " + e.getMessage());
            return "oauth/error";
        }
    }

    /**
     * 2단계: 로그인 처리 및 Authorization Code 생성
     * POST /oauth/login
     *
     * 요청 (PKCE 없음):
     * {
     *   "email": "user@example.com",
     *   "password": "password123",
     *   "client_id": "client_001",
     *   "redirect_uri": "https://site1.com/callback",
     *   "state": "random_state_123"
     * }
     *
     * 요청 (PKCE 포함):
     * {
     *   "email": "user@example.com",
     *   "password": "password123",
     *   "client_id": "client_001",
     *   "redirect_uri": "https://site1.com/callback",
     *   "state": "random_state_123",
     *   "code_challenge": "E9Mrozoa2owUzA7VLHwAIAKllCOvtQyen8P0xWXomaQ",
     *   "code_challenge_method": "S256"
     * }
     */
    @PostMapping("/login")
    public String login(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String client_id,
            @RequestParam String redirect_uri,
            @RequestParam String state,
            @RequestParam(required = false) String code_challenge,
            @RequestParam(required = false) String code_challenge_method,
            Model model) {

        try {
            // 1. 사용자 조회
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new Exception("Invalid email or password"));

            // 2. 비밀번호 검증
            if (!passwordEncoder.matches(password, user.getPassword())) {
                throw new Exception("Invalid email or password");
            }

            // 3. 사용자 활성화 상태 확인
            if (!user.isEnabled()) {
                throw new Exception("Account is disabled");
            }

            // 4. 이메일 검증 여부 확인
            if (!user.getEmailVerified()) {
                throw new Exception("Email verification required");
            }

            // 5. Authorization Code 생성 (PKCE 파라미터 포함)
            String authCode;
            if (code_challenge != null && !code_challenge.isEmpty()) {
                authCode = oAuthService.generateAuthorizationCode(client_id, email, redirect_uri, state,
                                                                    code_challenge, code_challenge_method);
                log.info("Authorization code generated with PKCE: email={}, client_id={}", email, client_id);
            } else {
                authCode = oAuthService.generateAuthorizationCode(client_id, email, redirect_uri, state);
                log.info("Authorization code generated: email={}, client_id={}", email, client_id);
            }

            // 6. 리다이렉트 URL 구성: redirect_uri?code=xxx&state=xxx
            String redirectUrl = redirect_uri + "?code=" + authCode + "&state=" + state;

            log.info("User logged in and authorized: email={}, client_id={}", email, client_id);
            return "redirect:" + redirectUrl;

        } catch (Exception e) {
            log.warn("Login error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("client_id", client_id);
            model.addAttribute("redirect_uri", redirect_uri);
            model.addAttribute("state", state);
            model.addAttribute("code_challenge", code_challenge);
            model.addAttribute("code_challenge_method", code_challenge_method);
            return "oauth/login";
        }
    }

    /**
     * 3단계: Authorization Code를 Token으로 교환 또는 Refresh Token으로 갱신
     * POST /oauth/token
     *
     * 요청 - Authorization Code (Confidential Client - client_secret 사용):
     * grant_type=authorization_code&
     * code=xxx&
     * client_id=client_001&
     * client_secret=secret_001&
     * redirect_uri=https://site1.com/callback
     *
     * 요청 - Authorization Code (Public Client - PKCE 사용, client_secret 불필요):
     * grant_type=authorization_code&
     * code=xxx&
     * client_id=client_001&
     * redirect_uri=https://site1.com/callback&
     * code_verifier=xxxxxx...
     *
     * 요청 - Refresh Token (Confidential Client):
     * grant_type=refresh_token&
     * refresh_token=xxx&
     * client_id=client_001&
     * client_secret=secret_001
     *
     * 요청 - Refresh Token (Public Client - PKCE 기반 세션):
     * grant_type=refresh_token&
     * refresh_token=xxx&
     * client_id=client_001
     *
     * 응답:
     * {
     *   "access_token": "eyJhbGc...",
     *   "refresh_token": "eyJhbGc...",
     *   "token_type": "Bearer",
     *   "expires_in": 86400000,
     *   "scope": "user:email user:profile"
     * }
     */
    @PostMapping("/token")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam String grant_type,
            @RequestParam(required = false) String code,
            @RequestParam String client_id,
            @RequestParam(required = false) String client_secret,  // PKCE 사용 시 불필요
            @RequestParam(required = false) String redirect_uri,
            @RequestParam(required = false) String code_verifier,
            @RequestParam(required = false) String refresh_token,
            HttpServletRequest request) {

        try {
            OAuthTokenResponse tokenResponse;

            if ("authorization_code".equals(grant_type)) {
                // Authorization Code Grant
                if (code == null || code.isEmpty()) {
                    throw new BadCredentialsException("Authorization code is required");
                }
                if (redirect_uri == null || redirect_uri.isEmpty()) {
                    throw new BadCredentialsException("redirect_uri is required for authorization_code grant");
                }

                // Authorization Code를 Token으로 교환
                tokenResponse = oAuthService.exchangeCodeForToken(
                        code, client_id, client_secret, redirect_uri, code_verifier, request);

                if (code_verifier != null && !code_verifier.isEmpty()) {
                    log.info("Token issued with PKCE (Public Client): client_id={}", client_id);
                } else {
                    log.info("Token issued with client_secret (Confidential Client): client_id={}", client_id);
                }

            } else if ("refresh_token".equals(grant_type)) {
                // Refresh Token Grant
                if (refresh_token == null || refresh_token.isEmpty()) {
                    throw new BadCredentialsException("refresh_token is required");
                }

                tokenResponse = oAuthService.refreshAccessToken(
                        refresh_token, client_id, client_secret, request);
                log.info("Token refreshed: client_id={}", client_id);

            } else {
                throw new BadCredentialsException("Unsupported grant_type. Supported: 'authorization_code', 'refresh_token'");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("access_token", tokenResponse.getAccessToken());
            response.put("refresh_token", tokenResponse.getRefreshToken());
            response.put("token_type", tokenResponse.getTokenType());
            response.put("expires_in", tokenResponse.getExpiresIn());
            response.put("scope", tokenResponse.getScope());

            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            log.warn("Token error: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "invalid_grant");
            error.put("error_description", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            log.error("Token error: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "server_error");
            error.put("error_description", "Token exchange failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * OAuth 로그아웃 (세션 무효화 및 토큰 블랙리스트)
     * POST /oauth/logout
     *
     * 요청:
     * {
     *   "refresh_token": "eyJhbGc..."
     * }
     *
     * 응답:
     * {
     *   "success": true,
     *   "message": "Logged out successfully"
     * }
     */
    @PostMapping("/logout")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> logout(
            @RequestParam(required = false) String refresh_token,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {

        try {
            String email = authentication.getName();

            // refresh_token은 요청 파라미터 또는 body에서 가져옴
            String token = refresh_token;
            if ((token == null || token.isEmpty()) && body != null) {
                token = body.get("refresh_token");
            }

            if (token == null || token.isEmpty()) {
                throw new BadCredentialsException("refresh_token is required");
            }

            oAuthService.logout(email, token);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Logged out successfully");

            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            log.warn("Logout error: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Logout failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 회원가입 페이지
     * GET /oauth/register
     */
    @GetMapping("/register")
    public String registerPage(
            @RequestParam String client_id,
            @RequestParam String redirect_uri,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code_challenge,
            @RequestParam(required = false) String code_challenge_method,
            Model model) {

        try {
            // 클라이언트 검증
            var clientOpt = clientService.validateClient(client_id);
            if (clientOpt.isEmpty()) {
                model.addAttribute("error", "Invalid client");
                return "oauth/error";
            }

            model.addAttribute("client_id", client_id);
            model.addAttribute("client_name", clientOpt.get().getName());
            model.addAttribute("redirect_uri", redirect_uri);
            model.addAttribute("state", state);
            model.addAttribute("code_challenge", code_challenge);
            model.addAttribute("code_challenge_method", code_challenge_method);

            return "oauth/register";

        } catch (Exception e) {
            log.error("Register page error: {}", e.getMessage());
            model.addAttribute("error", "Failed to load register page");
            return "oauth/error";
        }
    }

    /**
     * 회원가입 처리
     * POST /oauth/register
     */
    @PostMapping("/register")
    public String register(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String username,
            @RequestParam String client_id,
            @RequestParam String redirect_uri,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code_challenge,
            @RequestParam(required = false) String code_challenge_method,
            Model model) {

        try {
            // 클라이언트 검증
            var clientOpt = clientService.validateClient(client_id);
            if (clientOpt.isEmpty()) {
                model.addAttribute("error", "Invalid client");
                return "oauth/error";
            }

            // 회원가입 처리
            RegisterRequest registerRequest = new RegisterRequest();
            registerRequest.setEmail(email);
            registerRequest.setPassword(password);
            registerRequest.setUsername(username);
            registerRequest.setClientId(client_id);

            authService.register(registerRequest);

            log.info("User registered via OAuth flow: email={}", email);

            // 회원가입 성공 - 이메일 확인 페이지로 이동 (파라미터 유지)
            model.addAttribute("email", email);
            model.addAttribute("client_id", client_id);
            model.addAttribute("client_name", clientOpt.get().getName());
            model.addAttribute("redirect_uri", redirect_uri);
            model.addAttribute("state", state);
            model.addAttribute("code_challenge", code_challenge);
            model.addAttribute("code_challenge_method", code_challenge_method);

            return "oauth/verify-email";

        } catch (Exception e) {
            log.warn("Registration error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("username", username);
            model.addAttribute("client_id", client_id);
            model.addAttribute("client_name", clientService.validateClient(client_id)
                    .map(c -> c.getName()).orElse(client_id));
            model.addAttribute("redirect_uri", redirect_uri);
            model.addAttribute("state", state);
            model.addAttribute("code_challenge", code_challenge);
            model.addAttribute("code_challenge_method", code_challenge_method);
            return "oauth/register";
        }
    }

    /**
     * 에러 페이지
     */
    @GetMapping("/error")
    public String error(Model model) {
        return "oauth/error";
    }
}
