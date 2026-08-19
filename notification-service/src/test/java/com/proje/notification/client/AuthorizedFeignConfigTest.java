package com.proje.notification.client;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Donus cagrisina eklenen iki baslik.
 *
 * <p>Kapsam olcumu bu sinifi %21,6'da buldu ve sebebi acik: iki metot da
 * yalnizca bir lambda DONDURUYOR, lambdanin govdesi ise hicbir testte
 * calismiyordu. Yani "bean olusuyor mu" olculuyordu, "baslik gercekten
 * konuyor mu" degil.
 *
 * <p>Ikisinin de bozulmasi SESSIZDIR: token gitmezse yonetici aramasi 401
 * doner ve mail CC'siz gider (zenginlestirme, on kosul degil); korelasyon
 * kimligi gitmezse zincirin ikinci yarisi birincisiyle iliskilendirilemez --
 * tam da izlemek istedigimiz yerde iz kopar.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizedFeignConfigTest {

    private static final String MDC_KEY = "correlationId";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Mock
    private ServiceTokenProvider tokenProvider;

    private final AuthorizedFeignConfig config = new AuthorizedFeignConfig();

    @AfterEach
    void clearContext() {
        MDC.remove(MDC_KEY);
    }

    private RequestTemplate applyBearer() {
        RequestTemplate template = new RequestTemplate();
        config.bearerTokenInterceptor(tokenProvider).apply(template);
        return template;
    }

    private RequestTemplate applyCorrelation() {
        RequestTemplate template = new RequestTemplate();
        config.correlationIdInterceptor().apply(template);
        return template;
    }

    @Test
    @DisplayName("Sends the service token as a bearer credential")
    void sendsTheTokenAsABearerCredential() {
        when(tokenProvider.token()).thenReturn("jwt-abc");

        assertThat(applyBearer().headers().get(HttpHeaders.AUTHORIZATION))
                .containsExactly("Bearer jwt-abc");
    }

    @Test
    @DisplayName("Asks the provider on every call so a renewed token is picked up")
    void readsTheTokenOnEveryCall() {
        // Token kurulum aninda okunup SAKLANSAYDI, yenilenen token hicbir
        // zaman kullanilmaz ve servis 15 dakika sonra kalici olarak 401
        // almaya baslardi. Onbellek ServiceTokenProvider'in isi, burasinin
        // degil.
        when(tokenProvider.token()).thenReturn("first", "second");

        assertThat(applyBearer().headers().get(HttpHeaders.AUTHORIZATION))
                .containsExactly("Bearer first");
        assertThat(applyBearer().headers().get(HttpHeaders.AUTHORIZATION))
                .containsExactly("Bearer second");
    }

    @Test
    @DisplayName("Carries the correlation id of the message being processed")
    void carriesTheCorrelationId() {
        MDC.put(MDC_KEY, "trace-42");

        assertThat(applyCorrelation().headers().get(CORRELATION_HEADER))
                .containsExactly("trace-42");
    }

    @Test
    @DisplayName("Sends no correlation header at all when there is nothing to carry")
    void sendsNoHeaderWhenThereIsNoId() {
        // Bos bir baslik gondermek "null" metnini tasiyabilir ve uretici
        // tarafta dogrulama desenine ([A-Za-z0-9-]{1,64}) uydugu icin GECERLI
        // bir kimlik gibi kabul edilirdi -- yani iz, olmayan bir kimlige
        // baglanirdi.
        assertThat(applyCorrelation().headers()).doesNotContainKey(CORRELATION_HEADER);
    }

    @Test
    @DisplayName("The two interceptors stay independent of each other")
    void theInterceptorsAreIndependent() {
        // Korelasyon interceptor'i token saglayicisina HIC dokunmamali:
        // dokunsaydi her istek gereksiz bir giris denemesi tetikleyebilirdi.
        MDC.put(MDC_KEY, "trace-42");

        applyCorrelation();

        verifyNoInteractions(tokenProvider);
    }
}
