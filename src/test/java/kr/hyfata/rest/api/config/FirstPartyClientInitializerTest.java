package kr.hyfata.rest.api.config;

import kr.hyfata.rest.api.client.entity.ClientMetadata;
import kr.hyfata.rest.api.client.entity.ClientType;
import kr.hyfata.rest.api.client.repository.ClientMetadataRepository;
import kr.hyfata.rest.api.oauth.config.FirstPartyClientProperties;
import kr.hyfata.rest.api.oauth.config.FirstPartyClientInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirstPartyClientInitializerTest {

    @Mock
    private FirstPartyClientProperties firstPartyClientProperties;

    @Mock
    private RegisteredClientRepository registeredClientRepository;

    @Mock
    private ClientMetadataRepository clientMetadataRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private FirstPartyClientInitializer initializer;

    private FirstPartyClientProperties.ClientConfig baseConfig() {
        FirstPartyClientProperties.ClientConfig config = new FirstPartyClientProperties.ClientConfig();
        config.setClientId("hyfata-official-web");
        config.setClientSecret("secret");
        config.setName("Hyfata Official Web");
        config.setFrontendUrl("https://hyfata.kr");
        config.setRedirectUris("https://hyfata.kr/oauth/callback");
        config.setAllowedScopes("profile email sessions:manage");
        config.setEnabled(true);
        return config;
    }

    @Test
    @DisplayName("설정된 First-Party 클라이언트 등록 — confidential, consent 생략, FIRST_PARTY 메타데이터")
    void run_newClient_registersFirstPartyClient() throws Exception {
        // given
        when(firstPartyClientProperties.getClients()).thenReturn(List.of(baseConfig()));
        when(passwordEncoder.encode(any())).thenReturn("hashed_secret");
        when(clientMetadataRepository.findById("hyfata-official-web")).thenReturn(Optional.empty());
        when(clientMetadataRepository.save(any(ClientMetadata.class))).thenAnswer(i -> i.getArgument(0));

        // when
        initializer.run(null);

        // then
        ArgumentCaptor<RegisteredClient> rcCaptor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(registeredClientRepository).save(rcCaptor.capture());
        RegisteredClient saved = rcCaptor.getValue();

        assertThat(saved.getClientId()).isEqualTo("hyfata-official-web");
        assertThat(saved.getClientSecret()).isEqualTo("hashed_secret");
        assertThat(saved.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        // FIRST_PARTY는 consent 생략
        assertThat(saved.getClientSettings().isRequireAuthorizationConsent()).isFalse();
        assertThat(saved.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(saved.getScopes()).containsExactlyInAnyOrder("profile", "email", "sessions:manage");

        ArgumentCaptor<ClientMetadata> mdCaptor = ArgumentCaptor.forClass(ClientMetadata.class);
        verify(clientMetadataRepository).save(mdCaptor.capture());
        assertThat(mdCaptor.getValue().getClientType()).isEqualTo(ClientType.FIRST_PARTY);
    }

    @Test
    @DisplayName("기존 메타데이터가 있으면 FIRST_PARTY로 갱신 (upsert 동기화)")
    void run_existingClient_updatesToFirstParty() throws Exception {
        // given
        ClientMetadata existing = ClientMetadata.builder()
                .clientId("hyfata-official-web")
                .clientType(ClientType.THIRD_PARTY)
                .build();

        when(firstPartyClientProperties.getClients()).thenReturn(List.of(baseConfig()));
        when(passwordEncoder.encode(any())).thenReturn("hashed_secret");
        when(clientMetadataRepository.findById("hyfata-official-web")).thenReturn(Optional.of(existing));
        when(clientMetadataRepository.save(any(ClientMetadata.class))).thenAnswer(i -> i.getArgument(0));

        // when
        initializer.run(null);

        // then
        assertThat(existing.getClientType()).isEqualTo(ClientType.FIRST_PARTY);
        assertThat(existing.getFrontendUrl()).isEqualTo("https://hyfata.kr");
        verify(registeredClientRepository).save(any(RegisteredClient.class));  // save = upsert
        verify(clientMetadataRepository).save(existing);
    }

    @Test
    @DisplayName("clientSecret이 없으면 public 클라이언트로 등록 (NONE 인증)")
    void run_missingSecret_registersPublicClient() throws Exception {
        // given
        FirstPartyClientProperties.ClientConfig config = baseConfig();
        config.setClientSecret(null);

        when(firstPartyClientProperties.getClients()).thenReturn(List.of(config));
        when(clientMetadataRepository.findById("hyfata-official-web")).thenReturn(Optional.empty());
        when(clientMetadataRepository.save(any(ClientMetadata.class))).thenAnswer(i -> i.getArgument(0));

        // when
        initializer.run(null);

        // then
        ArgumentCaptor<RegisteredClient> captor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(registeredClientRepository).save(captor.capture());
        RegisteredClient saved = captor.getValue();

        assertThat(saved.getClientSecret()).isNull();
        assertThat(saved.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("enabled=false이면 등록 스킵")
    void run_disabledClient_skips() throws Exception {
        // given
        FirstPartyClientProperties.ClientConfig config = baseConfig();
        config.setEnabled(false);

        when(firstPartyClientProperties.getClients()).thenReturn(List.of(config));

        // when
        initializer.run(null);

        // then
        verify(registeredClientRepository, never()).save(any());
        verify(clientMetadataRepository, never()).save(any());
    }
}
