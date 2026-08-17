package com.proje.notification.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * Sinanan sey YONLENDIRME KARARIDIR: hangi basamaga gidiyor, ne zaman park
 * ediliyor ve hedef dogru kuyruk mu.
 *
 * TTL'in gercekten dolmasi SINANMAZ -- o RabbitMQ'nun isi ve onu taklit etmek
 * kendi kodumuz yerine broker'i test etmek olurdu.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Retry ladder")
class RetryLadderRecovererTest {

    private static final String RETRY_EXCHANGE = "employee.retry.exchange";
    private static final String DLX = "employee.dlx";

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Captor
    private ArgumentCaptor<String> exchange;

    @Captor
    private ArgumentCaptor<String> routingKey;

    @Captor
    private ArgumentCaptor<Message> sent;

    private RetryLadderRecoverer recoverer;

    @BeforeEach
    void setUp() {
        recoverer = new RetryLadderRecoverer(rabbitTemplate, RETRY_EXCHANGE, DLX);
    }

    private Message from(String queue, Integer rung) {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(queue);
        properties.setReceivedExchange("employee.exchange");
        properties.setReceivedRoutingKey("employee.updated");

        if (rung != null) {
            properties.setHeader(RetryLadder.ATTEMPT_HEADER, rung);
        }

        return new Message("{}".getBytes(), properties);
    }

    private void capture() {
        verify(rabbitTemplate).send(exchange.capture(), routingKey.capture(), sent.capture());
    }

    /** `getHeader` jeneriktir ve AssertJ'de belirsizlik yaratir; Object'e sabitlenir. */
    private Object headerOf(String name) {
        return sent.getValue().getMessageProperties().getHeader(name);
    }

    @Test
    @DisplayName("Sends a first failure to the shortest rung, not straight to the dead letter queue")
    void firstFailureGoesToFirstRung() {
        // Onceki hali dogrudan parka birakiyordu: bes deneme SANIYELER icinde
        // tukeniyordu, oysa olculen ariza SAATLER olcegindeydi.
        recoverer.recover(from("employee.notification.queue", null), new IllegalStateException("db down"));

        capture();

        assertThat(exchange.getValue()).isEqualTo(RETRY_EXCHANGE);
        assertThat(routingKey.getValue()).isEqualTo("employee.notification.queue.retry.5m");
        assertThat(headerOf(RetryLadder.ATTEMPT_HEADER)).isEqualTo(1);
    }

    @Test
    @DisplayName("Climbs to the next rung on each further failure")
    void climbsTheLadder() {
        recoverer.recover(from("employee.notification.queue", 1), new IllegalStateException("still down"));

        capture();

        assertThat(routingKey.getValue()).isEqualTo("employee.notification.queue.retry.30m");
        assertThat(headerOf(RetryLadder.ATTEMPT_HEADER)).isEqualTo(2);
    }

    @Test
    @DisplayName("Parks the message once the ladder is exhausted")
    void parksWhenExhausted() {
        // Merdivenin sonu bir KUYRUK degil bir PARK YERI: mesaj burada kalir ve
        // uyari atesler. Vazgecmeyi bilmeyen yeniden deneme, kuyrugun onunu
        // tikayan seyin ta kendisidir.
        recoverer.recover(from("employee.notification.queue", RetryLadder.RUNGS.size()),
                new IllegalStateException("broken payload"));

        capture();

        assertThat(exchange.getValue()).isEqualTo(DLX);
        assertThat(routingKey.getValue()).isEqualTo("employee.notification.dlq");
    }

    @Test
    @DisplayName("Parks an account failure in the account queue, not the employee one")
    void accountFailureGoesToItsOwnDeadLetterQueue() {
        // OLCULEN HATA: onceki recoverer hedefi SABIT tutuyordu
        // ("employee.notification.dlq") ve her iki kuyruk icin calisiyordu --
        // yani hesap olaylari yanlis park kuyruguna dusuyordu.
        recoverer.recover(from("account.notification.queue", RetryLadder.RUNGS.size()),
                new IllegalStateException("smtp down"));

        capture();

        assertThat(routingKey.getValue()).isEqualTo("account.notification.dlq");
    }

    @Test
    @DisplayName("Keeps the original routing key across every rung")
    void keepsTheOriginalRoutingKey() {
        // Ozgun anahtar her basamakta yeniden yazilsaydi ikinci turda
        // "ozgun" deger merdivenin kendi adresi olurdu ve mesajin nereden
        // geldigi kaybolurdu -- oynatma yordami tam da bu basligi kullaniyor.
        Message climbed = from("employee.notification.queue", 1);
        climbed.getMessageProperties().setHeader("x-original-routingKey", "employee.created");
        climbed.getMessageProperties().setReceivedRoutingKey("employee.notification.queue.retry.5m");

        recoverer.recover(climbed, new IllegalStateException("still down"));

        capture();

        assertThat(headerOf("x-original-routingKey")).isEqualTo("employee.created");
    }

    @Test
    @DisplayName("Refuses to guess a target when the source queue is unknown")
    void failsLoudlyWithoutASourceQueue() {
        // Sessizce atmak, mesaji kaybetmenin en gorunmez yolu olurdu.
        MessageProperties properties = new MessageProperties();

        assertThatThrownBy(() ->
                recoverer.recover(new Message("{}".getBytes(), properties), new IllegalStateException("x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source queue");
    }
}
