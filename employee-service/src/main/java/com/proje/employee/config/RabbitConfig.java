package com.proje.employee.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "employee.exchange";

    // Yalnizca exchange tanimlanir. Kuyruk ve baglantiyi tuketici kendisi tanimlar;
    // aksi halde yeni bir tuketici eklemek bu servisi degistirmeyi gerektirirdi.
    @Bean
    TopicExchange employeeExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }
}
