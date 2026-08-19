package com.proje.notification.client;

import com.proje.notification.client.dto.LoginRequest;
import com.proje.notification.client.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Servis hesabinin token onbellegi.
 *
 * <p>Kapsam olcumu bu sinifi %31'de buldu ve sinif tam olarak bir seyi
 * yapiyor: <b>her cagrida yeniden giris yapmamak</b>. O davranis bozulursa
 * hicbir sey GORUNUR sekilde kirilmaz -- yalnizca her yonetici aramasi bir
 * BCrypt dogrulamasi (~100 ms) daha odetir. Yani gerileme sessizdir ve ancak
 * bir test tutabilir.
 */
@ExtendWith(MockitoExtension.class)
class ServiceTokenProviderTest {

    private static final String EMAIL = "notification-service@internal";
    private static final String PASSWORD = "service-secret";

    @Mock
    private AuthClient authClient;

    private ServiceTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ServiceTokenProvider(authClient, EMAIL, PASSWORD);
    }

    /**
     * {@code doReturn} kullaniliyor, {@code when} degil: {@code when(mock.x())}
     * metodu GERCEKTEN cagirir ve mock daha once firlatmaya ayarlandiysa
     * stub'lamanin kendisi patlar.
     */
    private void serverIssues(String token, long validForSeconds) {
        doReturn(new LoginResponse(token, validForSeconds)).when(authClient).login(any());
    }

    @Test
    @DisplayName("Signs in with the configured service account")
    void signsInWithTheServiceAccount() {
        serverIssues("t1", 900);

        provider.token();

        ArgumentCaptor<LoginRequest> sent = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authClient).login(sent.capture());
        assertThat(sent.getValue().email()).isEqualTo(EMAIL);
        assertThat(sent.getValue().password()).isEqualTo(PASSWORD);
    }

    @Test
    @DisplayName("Reuses a valid token instead of signing in again")
    void reusesAValidToken() {
        // SINIFIN VARLIK SEBEBI. Her cagrida giris yapilsaydi BCrypt maliyeti
        // her yonetici aramasinda odenirdi -- JWT'yi tam da bundan kacinmak
        // icin secmistik.
        serverIssues("t1", 900);

        assertThat(provider.token()).isEqualTo("t1");
        assertThat(provider.token()).isEqualTo("t1");
        assertThat(provider.token()).isEqualTo("t1");

        verify(authClient, times(1)).login(any());
        verifyNoMoreInteractions(authClient);
    }

    @Test
    @DisplayName("Renews the token before it actually expires")
    void renewsBeforeExpiry() {
        // Yenileme payi 30 sn. Sunucu 20 saniyelik bir token verirse onbellek
        // ZATEN gecmiste sona erer, yani token daima yenilenir.
        //
        // Pay olmasaydi tam bitis aninda yenilenirdi ve yolda olan bir istek
        // sunucuya SURESI DOLMUS token ile varabilirdi.
        serverIssues("short", 20);

        provider.token();
        provider.token();

        verify(authClient, times(2)).login(any());
    }

    @Test
    @DisplayName("Signs in again after the cache is invalidated")
    void signsInAgainAfterInvalidate() {
        // Sunucu token'i reddettiginde (401) cagiran taraf invalidate() cagirir
        // ve BIR KEZ yeniden dener. Onbellek bosalmasaydi ayni gecersiz token
        // sonsuza kadar sunulurdu.
        serverIssues("t1", 900);
        provider.token();

        provider.invalidate();
        serverIssues("t2", 900);

        assertThat(provider.token()).isEqualTo("t2");
        verify(authClient, times(2)).login(any());
    }

    @Test
    @DisplayName("Lets a failed sign-in surface instead of caching a broken state")
    void failedLoginIsNotCached() {
        // Basarisiz giris yutulup null token onbelleklenseydi, sonraki her
        // cagri sessizce null doner ve Feign yetkisiz istek atardi.
        doThrow(new IllegalStateException("employee-service down")).when(authClient).login(any());

        assertThatThrownBy(() -> provider.token())
                .isInstanceOf(IllegalStateException.class);

        // Ikinci cagri YENIDEN denemeli: bozuk durum saklanmamali.
        serverIssues("recovered", 900);
        assertThat(provider.token()).isEqualTo("recovered");
    }
}
