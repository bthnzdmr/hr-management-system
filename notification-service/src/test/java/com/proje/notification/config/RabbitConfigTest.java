package com.proje.notification.config;

import com.proje.notification.event.AccountEventType;
import com.proje.notification.event.EmployeeEventType;
import com.proje.notification.event.LeaveEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding listesi ile olay tipleri arasindaki baglantiyi korur.
 *
 * Joker binding kaldirildiginda su risk dogdu: enum'a yeni bir tip eklenip
 * routing key eklenmezse kod derlenir, tum testler gecer ve olay SESSIZCE
 * teslim edilmez. Bu test o sessizligi kirar.
 */
class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    private List<String> boundRoutingKeys() {
        Declarables declarables = config.notificationBindings(
                new Queue(RabbitConfig.QUEUE), new TopicExchange(RabbitConfig.EXCHANGE));

        return declarables.getDeclarables().stream()
                .map(Binding.class::cast)
                .map(Binding::getRoutingKey)
                .toList();
    }

    @Test
    @DisplayName("Every event type the consumer knows has a matching routing key")
    void everyEventTypeHasARoutingKey() {
        List<String> keys = boundRoutingKeys();

        assertThat(EmployeeEventType.values())
                .allSatisfy(type -> assertThat(keys)
                        .contains("employee." + type.name().toLowerCase(Locale.ROOT)));
    }

    @Test
    @DisplayName("Binds no routing key the consumer cannot handle")
    void bindsNoUnhandledRoutingKey() {
        // Ters yon de onemli: isleyemedigimiz bir tipe abone olmak, mesaji
        // uc deneme sonrasi DLQ'ya dokmek demektir.
        assertThat(boundRoutingKeys()).hasSameSizeAs(EmployeeEventType.values());
    }

    @Test
    @DisplayName("Sends failed messages to a dead letter queue that has no dead letter exchange")
    void deadLetterQueueHasNoDeadLetterExchange() {
        // DLQ'nun kendi DLX'i olsaydi mesaj dongu olusturabilirdi.
        Queue deadLetter = config.deadLetterQueue();

        assertThat(deadLetter.getArguments()).doesNotContainKey("x-dead-letter-exchange");
        assertThat(config.notificationQueue().getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitConfig.DLX)
                .containsEntry("x-dead-letter-routing-key", RabbitConfig.DLQ);
    }

    @Test
    @DisplayName("Declares one binding per routing key")
    void declaresOneBindingPerRoutingKey() {
        Collection<Declarable> declarables = config.notificationBindings(
                new Queue(RabbitConfig.QUEUE), new TopicExchange(RabbitConfig.EXCHANGE))
                .getDeclarables();

        assertThat(declarables).allMatch(Binding.class::isInstance);
    }
    private List<String> boundLeaveKeys() {
        return config.leaveBindings(
                new Queue(RabbitConfig.LEAVE_QUEUE), new TopicExchange(RabbitConfig.EXCHANGE))
                .getDeclarables().stream()
                .map(Binding.class::cast)
                .map(Binding::getRoutingKey)
                .toList();
    }

    private List<String> boundAccountKeys() {
        return config.accountBindings(
                new Queue(RabbitConfig.ACCOUNT_QUEUE), new TopicExchange(RabbitConfig.EXCHANGE))
                .getDeclarables().stream()
                .map(Binding.class::cast)
                .map(Binding::getRoutingKey)
                .toList();
    }

    /** Enum adi routing key'e cevrilir: `PASSWORD_RESET_REQUESTED` -> `password-reset-requested`. */
    private String slug(Enum<?> type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @Test
    @DisplayName("Every leave event type the consumer knows has a matching routing key")
    void everyLeaveEventTypeHasARoutingKey() {
        // Ayni sessizlik burada da mumkun: enum'a tip eklenip binding
        // eklenmezse olay yayinlanir ama KIMSEYE teslim edilmez.
        List<String> keys = boundLeaveKeys();

        assertThat(LeaveEventType.values())
                .allSatisfy(type -> assertThat(keys).contains("leave." + slug(type)));
    }

    @Test
    @DisplayName("Binds no leave routing key the consumer cannot handle")
    void bindsNoUnhandledLeaveRoutingKey() {
        assertThat(boundLeaveKeys()).hasSameSizeAs(LeaveEventType.values());
    }

    @Test
    @DisplayName("Every account event type the consumer knows has a matching routing key")
    void everyAccountEventTypeHasARoutingKey() {
        // Hesap baglamalari bugune kadar HIC tutulmuyordu; ayni degismez.
        List<String> keys = boundAccountKeys();

        assertThat(AccountEventType.values())
                .allSatisfy(type -> assertThat(keys).contains("account." + slug(type)));
    }

    @Test
    @DisplayName("Leave failures go to a dead letter queue that has no dead letter exchange")
    void leaveDeadLetterQueueHasNoDeadLetterExchange() {
        assertThat(config.leaveDeadLetterQueue().getArguments())
                .doesNotContainKey("x-dead-letter-exchange");
        assertThat(config.leaveQueue().getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitConfig.DLX)
                .containsEntry("x-dead-letter-routing-key", RabbitConfig.LEAVE_DLQ);
    }
}
