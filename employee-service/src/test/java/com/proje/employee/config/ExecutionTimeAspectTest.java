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
    @DisplayName("Calls the real method and passes the return value through unchanged")
    void returnsResultUnchanged() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.measure(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(joinPoint, times(1)).proceed();
        assertThat(appender.list).hasSize(1);
    }

    @Test
    @DisplayName("Records the measurement even when the method throws, and rethrows")
    void measuresEvenWhenMethodThrows() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.measure(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        // finally blogu calismasaydi bu liste bos olurdu.
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("EmployeeService.create(..)");
    }

    @Test
    @DisplayName("Logs calls above the threshold at WARN level")
    void logsSlowCallsAtWarnLevel() throws Throwable {
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(510);
            return "result";
        });

        aspect.measure(joinPoint);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("SLOW");
    }
}
