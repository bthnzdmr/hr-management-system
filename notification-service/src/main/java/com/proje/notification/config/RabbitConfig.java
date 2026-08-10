package com.proje.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class RabbitConfig {

    // Ureticinin tanimladigi exchange. Burada da tanimlanmasi cakisma degildir:
    // ayni ozelliklerle bildirim yapmak gecerlidir ve iki servisin acilis
    // sirasindan bagimsiz calismasini saglar.
    public static final String EXCHANGE = "employee.exchange";

    public static final String QUEUE = "employee.notification.queue";
    public static final String DLX = "employee.dlx";
    public static final String DLQ = "employee.notification.dlq";

    // employee.# : employee ile baslayan tum olaylar. Yeni bir olay tipi
    // eklendiginde bu servis onu da almaya baslar, binding degistirilmez.
    private static final String ROUTING_PATTERN = "employee.#";

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

    @Bean
    Binding notificationBinding(Queue notificationQueue, TopicExchange employeeExchange) {
        return BindingBuilder.bind(notificationQueue).to(employeeExchange).with(ROUTING_PATTERN);
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
}
