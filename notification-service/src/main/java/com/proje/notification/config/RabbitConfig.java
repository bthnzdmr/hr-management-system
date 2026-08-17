package com.proje.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

@Configuration
public class RabbitConfig {

    // Ureticinin tanimladigi exchange. Burada da tanimlanmasi cakisma degildir:
    // ayni ozelliklerle bildirim yapmak gecerlidir ve iki servisin acilis
    // sirasindan bagimsiz calismasini saglar.
    public static final String EXCHANGE = "employee.exchange";

    public static final String QUEUE = "employee.notification.queue";
    public static final String DLX = "employee.dlx";
    public static final String DLQ = "employee.notification.dlq";

    // Hesap olaylari AYRI bir kuyruga gider: farkli govde sekli, farkli is.
    // Ayni kuyrukta olsalardi tek bir dinleyici iki ayri JSON semasini
    // cozmek zorunda kalirdi.
    public static final String ACCOUNT_QUEUE = "account.notification.queue";
    public static final String ACCOUNT_DLQ = "account.notification.dlq";

    // Yalnizca ISLEYEBILDIGIMIZ olay tiplerine abone olunur.
    //
    // Onceden "employee.#" jokeri vardi ve yorumunda "yeni olay tipi eklenince
    // bu servis onu da alir" yaziyordu. Ama EmployeeEventType KAPALI bir enum:
    // taninmayan tip Jackson'da cozulemez, uc deneme bosa gider ve mesaj kimsenin
    // bakmadigi DLQ'ya duserdi. Joker binding ile kapali enum birbiriyle celisir.
    //
    // Yeni bir olay tipi eklendiginde artik iki sey birlikte degisir: enum ve
    // buradaki liste. Sessizce DLQ'ya dusmek yerine hic teslim edilmez.
    private static final List<String> ROUTING_KEYS = List.of(
            "employee.created",
            "employee.updated",
            "employee.deactivated",
            "employee.reactivated");

    private static final List<String> ACCOUNT_ROUTING_KEYS = List.of(
            "account.password-reset-requested",
            "account.invited");

    @Bean
    TopicExchange employeeExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    // Basarisiz mesajlarin yonlendirildigi exchange. Topic degil direct:
    // burada desen eslesmesine ihtiyac yok, hedef tektir.
    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    Queue deadLetterQueue() {
        // DLQ'nun kendi DLX'i yoktur: buradan reddedilen mesajin gidecegi
        // baska yer olmamalidir, aksi halde dongu olusur.
        return QueueBuilder.durable(DLQ).build();
    }

    // Her routing key icin ayri bir binding. Declarables ile tek bean altinda
    // toplanir; Spring hepsini acilista bildirir.
    @Bean
    Declarables notificationBindings(Queue notificationQueue, TopicExchange employeeExchange) {
        return new Declarables(ROUTING_KEYS.stream()
                .map(key -> BindingBuilder.bind(notificationQueue).to(employeeExchange).with(key))
                .toList());
    }

    @Bean
    Queue accountQueue() {
        return QueueBuilder.durable(ACCOUNT_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(ACCOUNT_DLQ)
                .build();
    }

    @Bean
    Queue accountDeadLetterQueue() {
        return QueueBuilder.durable(ACCOUNT_DLQ).build();
    }

    @Bean
    Declarables accountBindings(Queue accountQueue, TopicExchange employeeExchange) {
        return new Declarables(ACCOUNT_ROUTING_KEYS.stream()
                .map(key -> BindingBuilder.bind(accountQueue).to(employeeExchange).with(key))
                .toList());
    }

    @Bean
    Binding accountDeadLetterBinding(Queue accountDeadLetterQueue,
                                     DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(accountDeadLetterQueue).to(deadLetterExchange).with(ACCOUNT_DLQ);
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ);
    }

    // Uretici mesaji application/json olarak gonderiyor; bu donusturucu onu
    // dogrudan EmployeeEvent'e cevirir.
    @Bean
    MessageConverter jsonMessageConverter(Jackson2ObjectMapperBuilder builder) {
        return new Jackson2JsonMessageConverter(builder.build());
    }

    /**
     * Retry hakki biten mesaji DLQ'ya HATA SEBEBIYLE birlikte tasir.
     *
     * Varsayilan davranis mesaji reddetmek ve DLX'e birakmaktir; o zaman DLQ'da
     * yalnizca x-death sayaci bulunur, NEDEN basarisiz oldugu bilinmez.
     * RepublishMessageRecoverer x-exception-message ve x-exception-stacktrace
     * basliklarini ekler -- DLQ'ya bakan kisi sebebi dogrudan gorur.
     */
    @Bean
    MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, DLX, DLQ);
    }
}
