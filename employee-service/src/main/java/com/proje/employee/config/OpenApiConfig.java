package com.proje.employee.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI tanimi ve kimlik dogrulama semasi.
 *
 * <p>Bunlar olmadan Swagger UI'da <b>"Authorize" dugmesi hic cikmiyordu</b>.
 * {@code /api/auth/login} disindaki her uc {@code Authorization: Bearer} istedigi
 * icin, README'nin iki kez tanittigi etkilesimli dokumantasyon pratikte
 * <em>tek bir ucu</em> cagirabiliyordu; gerisi 401 donuyordu. Yani sayfa bir
 * konsol degil, salt okunur bir sema goruntuleyicisiydi.
 *
 * <p><b>Global {@code @SecurityRequirement}:</b> her uca tek tek yazmak yerine
 * belge seviyesinde tanimlanir. Acik uclar ({@code /api/auth/**}) zaten
 * {@code SecurityConfig}'te {@code permitAll} ve Swagger'in jetonu gondermesi
 * onlari bozmaz -- filtre gecersiz jetonu yok sayar, reddetmez.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Employee Service API",
                version = "1.0.0",
                description = """
                        Personel yonetimi mikroservisinin disariya acik tek API'si.

                        Yetkilendirme okuma/yazma ayrimina dayanir: okumak icin kimlik
                        dogrulamasi yeterli, veri degistiren her uc rol ister. Kapsam
                        disindaki kayit 403 degil 404 doner -- 403 kaydin var oldugunu
                        sizdirir ve id denenerek personel sayisi ogrenilebilirdi.
                        """),
        servers = @Server(url = "/", description = "Bu sunucu"),
        // Belge seviyesinde: her uca tek tek yazmaya gerek kalmaz.
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
@SecurityScheme(
        name = OpenApiConfig.BEARER_SCHEME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "POST /api/auth/login cevabindaki \"token\" degeri.")
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearer-jwt";
}
