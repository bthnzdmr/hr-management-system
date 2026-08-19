package com.proje.employee.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Her istek bu filtreden geciyor ve kapsam olcumu onu %34'te buldu.
 *
 * Filtrenin davranisi ustu kapali bir sekilde kritiktir: kimlik ATAMAMAK ile
 * ISTEGI REDDETMEK farkli seylerdir. Bu filtre asla reddetmez -- kimligi
 * atar ya da atamaz, karari `AuthorizationFilter` verir. Bu ayrim bozulursa
 * ya herkese acik uclar kapanir ya da yetkisiz istekler gecer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void clearContext() {
        // Guvenlik baglami IS PARCACIGINA baglidir; temizlenmezse bir sonraki
        // test onceki testin kimligiyle baslar ve yesil kalmasi tesaduf olur.
        SecurityContextHolder.clearContext();
    }

    private Claims claims(String subject) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(subject);
        return claims;
    }

    private Authentication current() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private void run() throws Exception {
        filter.doFilterInternal(request, response, chain);
    }

    @Test
    @DisplayName("Lets a request without a token through, unauthenticated")
    void passesThroughWithoutToken() throws Exception {
        // Filtre REDDETMEZ. Reddetseydi giris ucu ve saglik kontrolu de ayni
        // zincirde kapanirdi.
        run();

        assertThat(current()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Ignores an Authorization header that is not a bearer token")
    void ignoresNonBearerHeader() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        run();

        assertThat(current()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Lets an unparseable token through without authenticating")
    void ignoresUnparseableToken() throws Exception {
        // Bozuk jeton bir ISTEMCI durumudur ve burada sessizce yok sayilir;
        // sonucu 401'i yetkilendirme filtresi verir.
        request.addHeader("Authorization", "Bearer broken.token.value");
        when(jwtService.parse("broken.token.value")).thenReturn(Optional.empty());

        run();

        assertThat(current()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authenticates a valid token and prefixes every role")
    void authenticatesValidToken() throws Exception {
        Claims claims = claims("grace@example.com");
        request.addHeader("Authorization", "Bearer good");
        when(jwtService.parse("good")).thenReturn(Optional.of(claims));
        when(jwtService.rolesOf(claims)).thenReturn(List.of("HR_SPECIALIST", "MANAGER"));

        run();

        assertThat(current()).isNotNull();
        assertThat(current().getName()).isEqualTo("grace@example.com");
        // `ROLE_` oneki SART: Spring'in hasRole kontrolu onu bekler ve
        // eksik olsaydi butun rol kurallari sessizce eslesmezdi.
        assertThat(current().getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactlyInAnyOrder("ROLE_HR_SPECIALIST", "ROLE_MANAGER");
    }

    @Test
    @DisplayName("Refuses to authenticate a token that carries no roles")
    void refusesTokenWithoutRoles() throws Exception {
        // Bos yetki listesiyle kimlik atamak kullaniciyi "giris yapmis ama
        // hicbir seye yetkisi yok" durumunda birakir: 401 yerine 403 alir ve
        // arayuz onu oturum sorunu saymaz. Yaniltici.
        Claims claims = claims("nobody@example.com");
        request.addHeader("Authorization", "Bearer roleless");
        when(jwtService.parse("roleless")).thenReturn(Optional.of(claims));
        when(jwtService.rolesOf(claims)).thenReturn(List.of());

        run();

        assertThat(current()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Refuses to authenticate a token with no subject")
    void refusesTokenWithoutSubject() throws Exception {
        Claims claims = claims(null);
        request.addHeader("Authorization", "Bearer subjectless");
        when(jwtService.parse("subjectless")).thenReturn(Optional.of(claims));
        when(jwtService.rolesOf(claims)).thenReturn(List.of("EMPLOYEE"));

        run();

        assertThat(current()).isNull();
    }

    @Test
    @DisplayName("Does not overwrite an identity that is already in the context")
    void doesNotOverwriteExistingIdentity() throws Exception {
        var existing = new UsernamePasswordAuthenticationToken("first@example.com", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);

        Claims claims = claims("second@example.com");
        request.addHeader("Authorization", "Bearer good");
        when(jwtService.parse("good")).thenReturn(Optional.of(claims));
        when(jwtService.rolesOf(claims)).thenReturn(List.of("EMPLOYEE"));

        run();

        assertThat(current().getName()).isEqualTo("first@example.com");
    }

    @Test
    @DisplayName("Always continues the chain, whatever it decides")
    void alwaysContinuesTheChain() throws Exception {
        // Zincirin kirilmasi istegin SESSIZCE olmesi demektir: yanit govdesi
        // bos doner ve sebebi hicbir yerde yazmaz.
        Claims claims = claims("grace@example.com");
        request.addHeader("Authorization", "Bearer good");
        when(jwtService.parse("good")).thenReturn(Optional.of(claims));
        when(jwtService.rolesOf(claims)).thenReturn(List.of("EMPLOYEE"));

        run();

        verify(chain).doFilter(request, response);
    }
}
