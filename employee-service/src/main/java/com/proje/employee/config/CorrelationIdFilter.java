package com.proje.employee.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final int MAX_LENGTH = 64;

    // Disaridan gelen deger loglara yazilacagi icin dogrulanir: satir sonu
    // iceren bir deger sahte log satiri uretebilir (log injection).
    private static final Pattern GECERLI = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String correlationId = temizle(request.getHeader(HEADER));

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Thread havuzdan geldigi icin temizlenmezse sonraki istek bu
            // kimligi devralir.
            MDC.remove(MDC_KEY);
        }
    }

    private String temizle(String gelen) {
        if (gelen != null && gelen.length() <= MAX_LENGTH && GECERLI.matcher(gelen).matches()) {
            return gelen;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
