package com.proje.employee;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uygulama baglaminin GERCEKTEN acildigini dogrular.
 *
 * <p>Bu test bir arizadan dogdu: {@code LoginAttemptService}'e testler icin
 * ikinci bir kurucu eklendi ve Spring hangisini kullanacagini bilemedigi icin
 * baglam acilmaz oldu. <b>205 birim testin hicbiri bunu goremedi</b> --
 * {@code @WebMvcTest} yalnizca web katmanini, {@code @DataJpaTest} yalnizca
 * kalicilik katmanini yukler ve birim testler nesneleri dogrudan kurar. Ariza
 * ancak konteyner ayaga kalkmayinca fark edildi.
 *
 * <p>Dilimlenmis testler hizlidir ve dogru araclardir, ama hicbiri
 * <em>"butun parcalar birlikte kuruluyor mu"</em> sorusunu sormaz. Bu, o
 * soruyu soran tek testtir ve bir bilesen tanimi bozuldugunda kirilir --
 * derlemede degil, CI'da.
 */
@SpringBootTest
@DisplayName("Application context")
class ApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Starts with every bean definition resolved")
    void contextLoads() {
        assertThat(context).isNotNull();

        // Ariza tam olarak burada olmustu: bean tanimi vardi ama ornegi
        // uretilemiyordu. Adin varligi yetmez, NESNE istenmeli.
        assertThat(context.getBean(com.proje.employee.service.LoginAttemptService.class))
                .isNotNull();
    }
}
