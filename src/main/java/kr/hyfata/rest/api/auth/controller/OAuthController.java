package kr.hyfata.rest.api.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.hyfata.rest.api.auth.dto.OAuthTokenResponse;
import kr.hyfata.rest.api.auth.dto.PasswordResetRequest;
import kr.hyfata.rest.api.auth.dto.RegisterRequest;
import kr.hyfata.rest.api.auth.entity.Client;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.auth.service.AuthService;
import kr.hyfata.rest.api.auth.service.ClientService;
import kr.hyfata.rest.api.auth.service.OAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * OAuth 2.0 Authorization Code Flow 구현
 * Google OAuth, Discord OAuth와 유사한 구조
 *
 * /oauth/login, /oauth/authorize는 서버사이드 세션(Spring Session + Redis) 기반
 * /oauth/token은 기존 JWT 기반 유지
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
    private final AuthenticationManager authenticationManager;

    /**
     * 1단계: Authorization 요청
     * GET /oauth/authorize?client_id=xxx&redirect_uri=xxx&state=xxx&response_type=code&code_challenge=xxx&code_challenge_method=xxx
     *
     * 이미 Hyfata에 로그인된 사용자는 자동으로 authorization code를 발급받습니다.
     * (서버사이드 세션 기반)
     */
    @GetMapping("/authorize")
    public String authorize(
            @RequestParam String client_id,
            @RequestParam String redirect_uri,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "code") String response_type,
            @RequestParam(required = false) String code_challenge,
            @RequestParam(required = false) String code_challenge_method,
            @RequestParam(required = false) String scope,
            Model model,
            HttpServletRequest request) {

        try {
            // 클라이언트 검증
            var clientOpt = clientService.validateClient(client_id);
            if (clientOpt.isEmpty()) {
                model.addAttribute("error", "유효하지 않은 클라이언트입니다.");
                return "oauth/error";
            }

            Client client = clientOpt.get();

            // Redirect URI 검증
            if (!oAuthService.validateRedirectUri(client_id, redirect_uri)) {
                model.addAttribute("error", "유효하지 않은 redirect URI입니다.");
                return "oauth/error";
            }

            // response_type이 code가 아니면 거부
            if (!"code".equals(response_type)) {
                model.addAttribute("error", "지원하지 않는 response_type입니다. 'code'만 지원됩니다.");
                return "oauth/error";
            }

            // State 파라미터가 없으면 생성 (CSRF 방지)
            if (state == null || state.isEmpty()) {
                state = UUID.randomUUID().toString();
            }

            // Scope 처리: 미입력 시 클라이언트 기본 scope 사용
            if (scope == null || scope.isEmpty()) {
                scope = String.join(" ", client.getDefaultScopesSet());
            }

            // Scope 검증: 클라이언트의 allowedScopes 초과 여부 확인
            String validatedScope = validateScopes(client, scope);
            if (validatedScope == null) {
                model.addAttribute("error", "invalid_scope: 요청된 scope가 클라이언트의 허용 범위를 초과합니다.");
                return "oauth/error";
            }

            // 이미 로그인된 사용자 확인 (서버사이드 세션 기반)
            User autoUser = getAuthenticatedUser();
            if (autoUser != null) {
                if (!autoUser.isEnabled()) {
                    model.addAttribute("error", "비활성화된 계정입니다.");
                    return "oauth/error";
                }
                if (!autoUser.getEmailVerified()) {
                    model.addAttribute("error", "이메일 인증이 필요합니다.");
                    return "oauth/error";
                }

                String authCode;
                if (code_challenge != null && !code_challenge.isEmpty()) {
                    authCode = oAuthService.generateAuthorizationCode(client_id, autoUser.getEmail(), redirect_uri, state,
                            code_challenge, code_challenge_method, validatedScope);
                } else {
                    authCode = oAuthService.generateAuthorizationCode(client_id, autoUser.getEmail(), redirect_uri, state,
                            validatedScope);
                }

                String redirectUrl = redirect_uri + "?code=" + authCode + "&state=" + state;
                log.info("Auto-authorized already logged-in user: email={}, client_id={}", autoUser.getEmail(), client_id);
                return "redirect:" + redirectUrl;
            }

            // 로그인 페이지로 이동 (state와 클라이언트 정보 전달)
            model.addAttribute("client_id", client_id);
            model.addAttribute("client_name", client.getName());
            model.addAttribute("redirect_uri", redirect_uri);
            model.addAttribute("state", state);
            model.addAttribute("scope", validatedScope);

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
     * 서버사이드 세션(Redis)에 인증 정보를 저장합니다.
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
            @RequestParam(required = false) String scope,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response) {

        try {
            // 1. Spring Security AuthenticationManager로 인증
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

            // 2. SecurityContext에 인증 정보 설정
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 3. HttpSession에 SecurityContext 저장 (Spring Session → Redis)
            HttpSession session = request.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext()
            );
            // Spring Session 인덱싱용 principal 이름 저장 (세션 조회/무효화 지원)
            session.setAttribute(
                    FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                    email
            );

            // 4. 사용자 정보 조회
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new Exception("사용자를 찾을 수 없습니다."));

            // 5. 사용자 상태 확인
            if (!user.isEnabled()) {
                throw new Exception("비활성화된 계정입니다.");
            }
            if (!user.getEmailVerified()) {
                throw new Exception("이메일 인증이 필요합니다.");
            }

            // 6. Authorization Code 생성 (PKCE 파라미터 및 scope 포함)
            String authCode;
            if (code_challenge != null && !code_challenge.isEmpty()) {
                authCode = oAuthService.generateAuthorizationCode(client_id, email, redirect_uri, state,
                        code_challenge, code_challenge_method, scope);
                log.info("Authorization code generated with PKCE: email={}, client_id={}", email, client_id);
            } else {
                authCode = oAuthService.generateAuthorizationCode(client_id, email, redirect_uri, state, scope);
                log.info("Authorization code generated: email={}, client_id={}", email, client_id);
            }

            // 7. 리다이렉트 URL 구성: redirect_uri?code=xxx&state=xxx
            String redirectUrl = redirect_uri + "?code=" + authCode + "&state=" + state;

            log.info("User logged in and authorized: email={}, client_id={}", email, client_id);
            return "redirect:" + redirectUrl;

        } catch (BadCredentialsException e) {
            log.warn("Login error: {}", e.getMessage());
            model.addAttribute("error", "이메일 또는 비밀번호가 올바르지 않습니다.");
            model.addAttribute("email", email);
            model.addAttribute("client_id", client_id);
            model.addAttribute("client_name", clientService.validateClient(client_id)
                    .map(c -> c.getName()).orElse(client_id));
            model.addAttribute("redirect_uri", redirect_uri);
            model.addAttribute("state", state);
            model.addAttribute("scope", scope);
            model.addAttribute("code_challenge", code_challenge);
            model.addAttribute("code_challenge_method", code_challenge_method);
            return "oauth/login";
        } catch (Exception e) {
            log.warn("Login error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("client_id", client_id);
            model.addAttribute("client_name", clientService.validateClient(client_id)
                    .map(c -> c.getName()).orElse(client_id));
            model.addAttribute("redirect_uri", redirect_uri);
            model.addAttribute("state", state);
            model.addAttribute("scope", scope);
            model.addAttribute("code_challenge", code_challenge);
            model.addAttribute("code_challenge_method", code_challenge_method);
            return "oauth/login";
        }
    }

    /**
     * 3단계: Authorization Code를 Token으로 교환 또는 Refresh Token으로 갱신
     * POST /oauth/token
     *
     * 기존 JWT 기반 그대로 유지
     */
    @PostMapping("/token")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam String grant_type,
            @RequestParam(required = false) String code,
            @RequestParam String client_id,
            @RequestParam(required = false) String client_secret,
            @RequestParam(required = false) String redirect_uri,
            @RequestParam(required = false) String code_verifier,
            @RequestParam(required = false) String refresh_token,
            HttpServletRequest request) {

        try {
            OAuthTokenResponse tokenResponse;

            if ("authorization_code".equals(grant_type)) {
                if (code == null || code.isEmpty()) {
                    throw new BadCredentialsException("Authorization code is required");
                }
                if (redirect_uri == null || redirect_uri.isEmpty()) {
                    throw new BadCredentialsException("redirect_uri is required for authorization_code grant");
                }

                tokenResponse = oAuthService.exchangeCodeForToken(
                        code, client_id, client_secret, redirect_uri, code_verifier, request);

                if (code_verifier != null && !code_verifier.isEmpty()) {
                    log.info("Token issued with PKCE (Public Client): client_id={}", client_id);
                } else {
                    log.info("Token issued with client_secret (Confidential Client): client_id={}", client_id);
                }

            } else if ("refresh_token".equals(grant_type)) {
                if (refresh_token == null || refresh_token.isEmpty()) {
                    throw new BadCredentialsException("리프레시 토큰이 필요합니다.");
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
     * OAuth 로그아웃 (서버사이드 세션 무효화 및 토큰 블랙리스트)
     * POST /oauth/logout
     */
    @PostMapping("/logout")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> logout(
            @RequestParam(required = false) String refresh_token,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        try {
            String email = authentication.getName();

            String token = refresh_token;
            if ((token == null || token.isEmpty()) && body != null) {
                token = body.get("refresh_token");
            }

            if (token == null || token.isEmpty()) {
                throw new BadCredentialsException("refresh_token is required");
            }

            oAuthService.logout(email, token);

            // 서버사이드 세션 무효화 (Redis)
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
                log.info("Server-side session invalidated for user: {}", email);
            }

            // SecurityContext 클리어
            SecurityContextHolder.clearContext();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Logged out successfully");

            return ResponseEntity.ok(result);

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
                model.addAttribute("error", "유효하지 않은 클라이언트입니다.");
                return "oauth/error";
            }

            // Redirect URI 검증
            if (!oAuthService.validateRedirectUri(client_id, redirect_uri)) {
                model.addAttribute("error", "유효하지 않은 redirect URI입니다.");
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
                model.addAttribute("error", "유효하지 않은 클라이언트입니다.");
                return "oauth/error";
            }

            // Redirect URI 검증
            if (!oAuthService.validateRedirectUri(client_id, redirect_uri)) {
                model.addAttribute("error", "유효하지 않은 redirect URI입니다.");
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
     * 비밀번호 찾기 페이지
     * GET /oauth/forgot-password
     */
    @GetMapping("/forgot-password")
    public String forgotPasswordPage(
            @RequestParam String client_id,
            @RequestParam String redirect_uri,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code_challenge,
            @RequestParam(required = false) String code_challenge_method,
            Model model) {

        try {
            var clientOpt = clientService.validateClient(client_id);
            if (clientOpt.isEmpty()) {
                model.addAttribute("error", "유효하지 않은 클라이언트입니다.");
                return "oauth/error";
            }

            if (!oAuthService.validateRedirectUri(client_id, redirect_uri)) {
                model.addAttribute("error", "유효하지 않은 redirect URI입니다.");
                return "oauth/error";
            }

            model.addAttribute("client_id", client_id);
            model.addAttribute("client_name", clientOpt.get().getName());
            model.addAttribute("redirect_uri", redirect_uri);
            model.addAttribute("state", state);
            model.addAttribute("code_challenge", code_challenge);
            model.addAttribute("code_challenge_method", code_challenge_method);

            return "oauth/forgot-password";

        } catch (Exception e) {
            log.error("Forgot password page error: {}", e.getMessage());
            model.addAttribute("error", "Failed to load forgot password page");
            return "oauth/error";
        }
    }

    /**
     * 비밀번호 재설정 요청 처리
     * POST /oauth/forgot-password
     */
    @PostMapping("/forgot-password")
    public String forgotPassword(
            @RequestParam String email,
            @RequestParam String client_id,
            @RequestParam String redirect_uri,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code_challenge,
            @RequestParam(required = false) String code_challenge_method,
            Model model) {

        try {
            var clientOpt = clientService.validateClient(client_id);
            if (clientOpt.isEmpty()) {
                model.addAttribute("error", "유효하지 않은 클라이언트입니다.");
                return "oauth/error";
            }

            if (!oAuthService.validateRedirectUri(client_id, redirect_uri)) {
                model.addAttribute("error", "유효하지 않은 redirect URI입니다.");
                return "oauth/error";
            }

            authService.requestPasswordReset(email, client_id);

            model.addAttribute("client_id", client_id);
            model.addAttribute("client_name", clientOpt.get().getName());
            model.addAttribute("redirect_uri", redirect_uri);
            model.addAttribute("state", state);
            model.addAttribute("code_challenge", code_challenge);
            model.addAttribute("code_challenge_method", code_challenge_method);
            model.addAttribute("message", "If the email exists, a password reset link has been sent.");

            return "oauth/forgot-password";

        } catch (Exception e) {
            log.warn("Forgot password error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("client_id", client_id);
            model.addAttribute("client_name", clientService.validateClient(client_id)
                    .map(c -> c.getName()).orElse(client_id));
            model.addAttribute("redirect_uri", redirect_uri);
            model.addAttribute("state", state);
            model.addAttribute("code_challenge", code_challenge);
            model.addAttribute("code_challenge_method", code_challenge_method);
            return "oauth/forgot-password";
        }
    }

    /**
     * 에러 페이지
     */
    @GetMapping("/error")
    public String error(Model model) {
        return "oauth/error";
    }

    // ========== 유틸리티 메서드 ==========

    /**
     * Spring Security Context에서 인증된 사용자를 조회합니다.
     * 서버사이드 세션(Redis) 기반입니다.
     */
    /**
     * 클라이언트의 allowedScopes 대비 요청 scope 검증
     * @return 유효하면 정규화된 scope 문자열, 무효하면 null
     */
    private String validateScopes(Client client, String requestedScope) {
        if (requestedScope == null || requestedScope.isBlank()) {
            return String.join(" ", client.getDefaultScopesSet());
        }

        Set<String> allowed = client.getAllowedScopesSet();
        Set<String> requested = Set.of(requestedScope.split("[ +]"));

        // 요청된 모든 scope가 allowed에 포함되는지 확인
        for (String req : requested) {
            if (req.isBlank()) continue;
            if (!allowed.contains(req)) {
                log.warn("Scope validation failed: client_id={}, requested='{}', allowed='{}'",
                        client.getClientId(), req, String.join(" ", allowed));
                return null;
            }
        }

        return String.join(" ", requested);
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetails) {
            String email = authentication.getName();
            return userRepository.findByEmail(email).orElse(null);
        }
        return null;
    }
}
