package com.proje.employee.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Kimlik gonderilmezse sunucu uretir ve cevap header'ina yazar")
    void kimlikYoksaUretilir() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String uretilen = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(uretilen).isNotBlank();
        assertThat(uretilen).matches("[A-Za-z0-9-]+");
    }

    @Test
    @DisplayName("Gecerli kimlik gonderilirse oldugu gibi tasinir")
    void gecerliKimlikTasinir() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "siparis-4821");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("siparis-4821");
    }

    @Test
    @DisplayName("Zararli kimlik reddedilir ve yerine yenisi uretilir (log injection)")
    void zararliKimlikReddedilir() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "sahte] FAKE LOG [x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String donen = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(donen).isNotEqualTo("sahte] FAKE LOG [x");
        assertThat(donen).matches("[A-Za-z0-9-]+");
    }

    @Test
    @DisplayName("Kimlik zincir sirasinda MDC'de bulunur, istek bitince TEMIZLENIR")
    void mdcIstekSonundaTemizlenir() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "test-kimlik");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> zincirdekiDeger = new AtomicReference<>();
        MockFilterChain zincir = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                zincirdekiDeger.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        };

        filter.doFilter(request, response, zincir);

        assertThat(zincirdekiDeger.get()).isEqualTo("test-kimlik");

        // Thread havuzdan geldigi icin temizlenmezse sonraki istek bu kimligi devralir.
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Zincirde istisna firlasa bile MDC temizlenir")
    void istisnadaDaTemizlenir() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain patlayanZincir = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalStateException("patladi");
            }
        };

        try {
            filter.doFilter(request, response, patlayanZincir);
        } catch (Exception ignored) {
            // istisnanin yukari gecmesi beklenir
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
