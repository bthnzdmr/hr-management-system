package com.proje.employee.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionTimeAspectTest {

    private ExecutionTimeAspect aspect;
    private ProceedingJoinPoint joinPoint;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        aspect = new ExecutionTimeAspect();

        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("EmployeeService.create(..)");

        joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);

        // Logback'in ListAppender'i: uretilen log kayitlarini bellekte tutar,
        // boylece log ciktisi uzerinde dogrulama yapilabilir.
        logger = (Logger) LoggerFactory.getLogger(ExecutionTimeAspect.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("Gercek metot cagrilir ve donen deger degistirilmeden gecirilir")
    void donenDegerDegismez() throws Throwable {
        when(joinPoint.proceed()).thenReturn("sonuc");

        Object sonuc = aspect.measure(joinPoint);

        assertThat(sonuc).isEqualTo("sonuc");
        verify(joinPoint, times(1)).proceed();
        assertThat(appender.list).hasSize(1);
    }

    @Test
    @DisplayName("Istisna firlasa bile olcum kaydedilir ve istisna yukari gecer")
    void istisnadaDaOlculur() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("patladi"));

        assertThatThrownBy(() -> aspect.measure(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("patladi");

        // finally blogu calismasaydi bu liste bos olurdu.
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("EmployeeService.create(..)");
    }

    @Test
    @DisplayName("Esigin altindaki cagri debug, ustundeki warn seviyesinde loglanir")
    void yavasCagriWarnSeviyesinde() throws Throwable {
        when(joinPoint.proceed()).thenAnswer(i -> {
            Thread.sleep(510);
            return "sonuc";
        });

        aspect.measure(joinPoint);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("YAVAS");
    }
}
