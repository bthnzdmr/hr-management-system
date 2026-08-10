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
    @DisplayName("Generates an id when the request does not provide one")
    void generatesIdWhenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(generated).isNotBlank();
        assertThat(generated).matches("[A-Za-z0-9-]+");
    }

    @Test
    @DisplayName("Propagates a valid incoming id unchanged")
    void propagatesValidIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "order-4821");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("order-4821");
    }

    @Test
    @DisplayName("Rejects a malicious id and generates a new one (log injection)")
    void rejectsMaliciousId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "fake] FAKE LOG [x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String returned = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(returned).isNotEqualTo("fake] FAKE LOG [x");
        assertThat(returned).matches("[A-Za-z0-9-]+");
    }

    @Test
    @DisplayName("Id is present in MDC during the chain and cleared afterwards")
    void clearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> valueInsideChain = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                valueInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(valueInsideChain.get()).isEqualTo("test-id");

        // Thread havuzdan geldigi icin temizlenmezse sonraki istek bu kimligi devralir.
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Clears MDC even when the chain throws")
    void clearsMdcWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain throwingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalStateException("boom");
            }
        };

        try {
            filter.doFilter(request, response, throwingChain);
        } catch (Exception ignored) {
            // istisnanin yukari gecmesi beklenir
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
