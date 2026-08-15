package com.proje.employee.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Yanlis parola BEKLENEN bir istemci durumudur, sunucu arizasi degil.
 *
 * Yigin izi basildiginda tek bir basarisiz giris yuzlerce satir uretiyordu:
 * parola tarayan biri log hacmini kendi silahina cevirebilirdi.
 */
class AuthenticationLogTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("A failed login is logged without a stack trace")
    void logsFailedLoginWithoutStackTrace() {
        new GlobalExceptionHandler()
                .handleAuthentication(new BadCredentialsException("Bad credentials"));

        assertThat(appender.list).hasSize(1);

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        // Sebep hala loga giriyor; kaybolan sey yalnizca yigin izi.
        assertThat(event.getFormattedMessage()).contains("Bad credentials");
        assertThat(event.getThrowableProxy()).isNull();
    }
}
