package com.proje.employee.event;

import com.proje.employee.entity.OutboxEvent;
import com.proje.employee.repository.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 100;
    private static final long CONFIRM_TIMEOUT_MS = 1000;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxRelay relay() {
        return new OutboxRelay(outboxRepository, rabbitTemplate,
                MAX_ATTEMPTS, BATCH_SIZE, CONFIRM_TIMEOUT_MS);
    }

    private OutboxEvent event() {
        return event("abc-123");
    }

    private OutboxEvent event(String correlationId) {
        return new OutboxEvent(UUID.randomUUID(), "CREATED", "employee.created",
                "{\"eventType\":\"CREATED\"}", correlationId);
    }

    private void answerWith(boolean ack, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    @DisplayName("Marks an event as published once the broker confirms it")
    void marksPublishedOnAck() {
        OutboxEvent event = event();
        when(outboxRepository.lockPending(anyInt(), anyInt())).thenReturn(List.of(event));
        answerWith(true, null);

        relay().publishPending();

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    @DisplayName("Sends the stored payload as raw JSON bytes without re-encoding it")
    void sendsPayloadAsRawJson() {
        OutboxEvent event = event();
        when(outboxRepository.lockPending(anyInt(), anyInt())).thenReturn(List.of(event));
        answerWith(true, null);

        relay().publishPending();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(anyString(), anyString(), captor.capture(), any(CorrelationData.class));
        Message sent = captor.getValue();

        assertThat(new String(sent.getBody(), StandardCharsets.UTF_8)).isEqualTo(event.getPayload());
        assertThat(sent.getMessageProperties().getContentType())
                .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(sent.getMessageProperties().getMessageId())
                .isEqualTo(event.getEventId().toString());
    }

    @Test
    @DisplayName("Counts a broker rejection against the event and leaves it unpublished")
    void countsAttemptOnNack() {
        OutboxEvent event = event();
        when(outboxRepository.lockPending(anyInt(), anyInt())).thenReturn(List.of(event));
        answerWith(false, "queue full");

        relay().publishPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("queue full");
    }

    @Test
    @DisplayName("An unreachable broker consumes no attempt and stops the batch")
    void unreachableBrokerDoesNotConsumeAttempts() {
        List<OutboxEvent> pending = List.of(event(), event(), event());
        when(outboxRepository.lockPending(anyInt(), anyInt())).thenReturn(pending);
        doThrow(new AmqpConnectException(new RuntimeException("Connection refused")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        relay().publishPending();

        // Erisilemezlik mesajin kusuru degildir: sayac artmaz, kalan satirlar
        // bosuna denenmez. Aksi halde tek kesinti tum bekleyen olaylari yakardi.
        assertThat(pending).allSatisfy(event -> {
            assertThat(event.getAttempts()).isZero();
            assertThat(event.getPublishedAt()).isNull();
        });
        verify(rabbitTemplate, times(1))
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    @DisplayName("Abandons an event once it reaches the attempt limit")
    void abandonsAfterAttemptLimit() {
        OutboxEvent event = event();
        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            event.markFailed("earlier failure");
        }
        when(outboxRepository.lockPending(anyInt(), anyInt())).thenReturn(List.of(event));
        answerWith(false, "rejected");

        relay().publishPending();

        assertThat(event.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("Puts the correlation id on the message so the consumer can pick it up")
    void carriesCorrelationIdOnTheMessage() {
        // Kimlik mesajla kuyrugu gecmezse tuketici onu hicbir yerden
        // ogrenemez ve iki servisin loglari birlestirilemez.
        OutboxEvent event = event("abc-123");
        when(outboxRepository.lockPending(anyInt(), anyInt())).thenReturn(List.of(event));
        answerWith(true, null);

        relay().publishPending();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(anyString(), anyString(), captor.capture(),
                any(CorrelationData.class));

        String header = captor.getValue().getMessageProperties().getHeader("X-Correlation-Id");
        assertThat(header).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("Publishes rows written before the correlation column existed")
    void publishesRowsWithoutCorrelationId() {
        // V8 oncesi satirlarda kimlik NULL. Bos baslik gondermek yerine
        // baslik hic konmaz; eksik kimlik yayini ENGELLEMEMELI.
        OutboxEvent event = event(null);
        when(outboxRepository.lockPending(anyInt(), anyInt())).thenReturn(List.of(event));
        answerWith(true, null);

        relay().publishPending();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(anyString(), anyString(), captor.capture(),
                any(CorrelationData.class));

        String header = captor.getValue().getMessageProperties().getHeader("X-Correlation-Id");
        assertThat(header).isNull();
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Does not blame the message when the channel died after sending")
    void doesNotCountInfrastructureNack() {
        // Kural zaten yaziliydi -- "erisilemezlik o mesajin kusuru degildir" --
        // ama yalnizca send()'in firlattigi istisnalar icin isliyordu. Kanal
        // send()'den SONRA olurse red bir NACK olarak gelir ve sayaci haksiz
        // yere artirirdi: tek bir broker yeniden baslatmasi, ucustaki 20
        // mesajin bes hakkindan birini birden yakardi.
        OutboxEvent event = event();
        when(outboxRepository.lockPending(anyInt(), anyInt())).thenReturn(List.of(event));
        answerWith(false, "channel closed");

        relay().publishPending();

        assertThat(event.getAttempts())
                .as("an infrastructure failure must not consume the message's attempts")
                .isZero();
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("Treats a nack with no reason as infrastructure, not as the message's fault")
    void treatsReasonlessNackAsInfrastructure() {
        // Broker bir mesaji KENDI kusuru yuzunden reddettiginde sebep bildirir.
        // Sebepsiz red, kapanmakta olan bir kanaldan gelir.
        OutboxEvent event = event();
        when(outboxRepository.lockPending(anyInt(), anyInt())).thenReturn(List.of(event));
        answerWith(false, null);

        relay().publishPending();

        assertThat(event.getAttempts()).isZero();
    }

    @Test
    @DisplayName("Still blames the message when the broker rejects it with a real reason")
    void stillCountsGenuineNack() {
        // Ayrim korunmali: gercek bir red hala sayilmali, yoksa bozuk bir mesaj
        // sonsuza kadar denenir ve kuyrugun onunu tikardi.
        OutboxEvent event = event();
        when(outboxRepository.lockPending(anyInt(), anyInt())).thenReturn(List.of(event));
        answerWith(false, "NO_ROUTE for routing key employee.created");

        relay().publishPending();

        assertThat(event.getAttempts()).isEqualTo(1);
    }
}
