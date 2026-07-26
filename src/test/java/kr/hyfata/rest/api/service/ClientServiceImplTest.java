package kr.hyfata.rest.api.service;

import kr.hyfata.rest.api.auth.dto.ClientRegistrationRequest;
import kr.hyfata.rest.api.auth.dto.ClientResponse;
import kr.hyfata.rest.api.auth.entity.ClientMetadata;
import kr.hyfata.rest.api.auth.entity.ClientType;
import kr.hyfata.rest.api.auth.repository.ClientMetadataRepository;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.auth.service.impl.ClientServiceImpl;
import kr.hyfata.rest.api.common.util.TokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientServiceImplTest {

    @Mock
    private RegisteredClientRepository registeredClientRepository;

    @Mock
    private ClientMetadataRepository clientMetadataRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ClientServiceImpl clientService;

    private ClientRegistrationRequest request;

    @BeforeEach
    void setUp() {
        request = ClientRegistrationRequest.builder()
                .name("Test Client")
                .frontendUrl("https://test.com")
                .redirectUris(List.of("https://test.com/callback"))
                .allowedScopes("profile email account:manage 2fa:manage")
                .build();

        when(tokenGenerator.generatePasswordResetToken()).thenReturn("plain_secret");
        when(passwordEncoder.encode(any())).thenReturn("hashed_secret");
        when(registeredClientRepository.findByClientId(any())).thenReturn(null);
        when(clientMetadataRepository.save(any(ClientMetadata.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("관리자가 클라이언트 등록 시 요청한 scope 그대로 적용")
    void registerClient_admin_allowsCustomScopes() {
        // given
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // when
        ClientResponse response = clientService.registerClient(request, adminAuth);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAllowedScopes().split(" "))
                .containsExactlyInAnyOrder("profile", "email", "account:manage", "2fa:manage");
    }

    @Test
    @DisplayName("비관리자가 클라이언트 등록 시 scope가 profile email로 강제 제한")
    void registerClient_nonAdmin_forcesDefaultScopes() {
        // given
        Authentication userAuth = new UsernamePasswordAuthenticationToken(
                "user@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        // when
        ClientResponse response = clientService.registerClient(request, userAuth);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAllowedScopes().split(" ")).containsExactlyInAnyOrder("profile", "email");
    }

    @Test
    @DisplayName("익명 사용자가 클라이언트 등록 시도 시 scope가 profile email로 강제 제한")
    void registerClient_anonymous_forcesDefaultScopes() {
        // when
        ClientResponse response = clientService.registerClient(request, null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAllowedScopes().split(" ")).containsExactlyInAnyOrder("profile", "email");
    }

    @Test
    @DisplayName("관리자가 scope를 지정하지 않으면 기본값 적용")
    void registerClient_adminWithoutScopes_usesDefault() {
        // given
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        request.setAllowedScopes(null);

        // when
        ClientResponse response = clientService.registerClient(request, adminAuth);

        // then
        assertThat(response.getAllowedScopes().split(" ")).containsExactlyInAnyOrder("profile", "email");
    }

    @Test
    @DisplayName("API로 등록된 클라이언트는 항상 THIRD_PARTY 타입")
    void registerClient_alwaysThirdPartyType() {
        // given
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // when
        ClientResponse response = clientService.registerClient(request, adminAuth);

        // then
        assertThat(response.getClientType()).isEqualTo(ClientType.THIRD_PARTY);
    }

    @Test
    @DisplayName("RegisteredClient 저장 — confidential, consent 필요, PKCE, TokenSettings")
    void registerClient_savesRegisteredClientWithProtocolSettings() {
        // given
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // when
        ClientResponse response = clientService.registerClient(request, adminAuth);

        // then
        ArgumentCaptor<RegisteredClient> captor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(registeredClientRepository).save(captor.capture());
        RegisteredClient saved = captor.getValue();

        assertThat(saved.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(saved.getClientSecret()).isEqualTo("hashed_secret");
        assertThat(saved.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(saved.getClientSettings().isRequireAuthorizationConsent()).isTrue();  // third-party
        assertThat(saved.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(15));
        assertThat(saved.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(14));
        assertThat(saved.getTokenSettings().isReuseRefreshTokens()).isFalse();
        assertThat(saved.getRedirectUris()).containsExactly("https://test.com/callback");

        // 생성 시에만 평문 시크릿 반환
        assertThat(response.getClientSecret()).isEqualTo("plain_secret");
    }

    @Test
    @DisplayName("메타데이터 저장 — frontendUrl, description, THIRD_PARTY")
    void registerClient_savesMetadata() {
        // given
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // when
        ClientResponse response = clientService.registerClient(request, adminAuth);

        // then
        ArgumentCaptor<ClientMetadata> captor = ArgumentCaptor.forClass(ClientMetadata.class);
        verify(clientMetadataRepository).save(captor.capture());
        ClientMetadata saved = captor.getValue();

        assertThat(saved.getClientId()).isEqualTo(response.getClientId());
        assertThat(saved.getFrontendUrl()).isEqualTo("https://test.com");
        assertThat(saved.getClientType()).isEqualTo(ClientType.THIRD_PARTY);
    }

    @Test
    @DisplayName("getClient — RegisteredClient + 메타데이터 병합, 시크릿 미포함")
    void getClient_mergesRegisteredClientAndMetadata() {
        // given
        RegisteredClient registeredClient = RegisteredClient.withId("client_1")
                .clientId("client_1")
                .clientName("Test Client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://test.com/callback")
                .scope("profile")
                .build();
        ClientMetadata metadata = ClientMetadata.builder()
                .clientId("client_1")
                .frontendUrl("https://test.com")
                .description("desc")
                .clientType(ClientType.THIRD_PARTY)
                .build();

        when(registeredClientRepository.findByClientId("client_1")).thenReturn(registeredClient);
        when(clientMetadataRepository.findById("client_1")).thenReturn(java.util.Optional.of(metadata));

        // when
        var result = clientService.getClient("client_1");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getClientSecret()).isNull();  // 조회 시 시크릿 미포함
        assertThat(result.get().getFrontendUrl()).isEqualTo("https://test.com");
        assertThat(result.get().getRedirectUris()).containsExactly("https://test.com/callback");
    }

    @Test
    @DisplayName("getClient — 없으면 empty")
    void getClient_notFound() {
        // given
        when(registeredClientRepository.findByClientId("unknown")).thenReturn(null);

        // when & then
        assertThat(clientService.getClient("unknown")).isEmpty();
        assertThat(clientService.existsClient("unknown")).isFalse();
    }
}
