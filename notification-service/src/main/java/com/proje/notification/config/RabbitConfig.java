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

    /**
     * Gecikmeli yeniden deneme basamaklarinin exchange'i.
     *
     * DLX'ten AYRI tutuldu bilerek: DLX "vazgecildi, park et" demektir,
     * bu exchange ise "bekle ve tekrar dene". Ikisini ayni exchange'e
     * yigmak, iki farkli niyeti tek adres altinda gizlerdi.
     *
     * Direct: desen eslesmesine ihtiyac yok, hedef her zaman tek bir kuyruk.
     */
    public static final String RETRY_EXCHANGE = "employee.retry.exchange";

    // Hesap olaylari AYRI bir kuyruga gider: farkli govde sekli, farkli is.
    // Ayni kuyrukta olsalardi tek bir dinleyici iki ayri JSON semasini
    // cozmek zorunda kalirdi.
    public static final String ACCOUNT_QUEUE = "account.notification.queue";
    public static final String ACCOUNT_DLQ = "account.notification.dlq";

    // Izin olaylari da AYRI: yine farkli govde sekli, farkli is. Ayrik kuyruk
    // bir izolasyon karari da -- bozuk bir izin sablonu personel
    // bildirimlerinin park kuyrugunu doldurmamali.
    public static final String LEAVE_QUEUE = "leave.notification.queue";
    public static final String LEAVE_DLQ = "leave.notification.dlq";

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

    private static final List<String> LEAVE_ROUTING_KEYS = List.of(
            "leave.requested",
            "leave.decided",
            "leave.cancelled");

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
    Queue leaveQueue() {
        return QueueBuilder.durable(LEAVE_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(LEAVE_DLQ)
                .build();
    }

    @Bean
    Queue leaveDeadLetterQueue() {
        return QueueBuilder.durable(LEAVE_DLQ).build();
    }

    @Bean
    Declarables leaveBindings(Queue leaveQueue, TopicExchange employeeExchange) {
        return new Declarables(LEAVE_ROUTING_KEYS.stream()
                .map(key -> BindingBuilder.bind(leaveQueue).to(employeeExchange).with(key))
                .toList());
    }

    @Bean
    Binding leaveDeadLetterBinding(Queue leaveDeadLetterQueue,
                                   DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(leaveDeadLetterQueue).to(deadLetterExchange).with(LEAVE_DLQ);
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ);
    }

    @Bean
    DirectExchange retryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    /**
     * Her tuketici kuyrugu icin basamak kuyruklari.
     *
     * Basamak kuyrugunun TUKETICISI YOKTUR: mesaj orada TTL dolana kadar
     * bekler, sonra `x-dead-letter-*` ile TUKETICI kuyruguna geri duser.
     * Bekleme isini RabbitMQ yapiyor; bizim zamanlanmis bir isimiz yok.
     *
     * Hedef, basamagin KENDI ozelligidir ve her tuketici kuyrugu icin ayri
     * basamak seti uretilir. Paylasilan tek set kullanilsaydi geri donus
     * adresi mesaja gore degismek zorunda kalirdi -- oysa
     * `x-dead-letter-routing-key` bir KUYRUK ozelligidir, mesaj ozelligi degil.
     */
    private Declarables ladderFor(String consumerQueue) {
        List<org.springframework.amqp.core.Declarable> declarations = new java.util.ArrayList<>();

        for (RetryLadder.Rung rung : RetryLadder.RUNGS) {
            String name = RetryLadder.queueName(consumerQueue, rung);

            Queue queue = QueueBuilder.durable(name)
                    .ttl((int) rung.ttl().toMillis())
                    // TTL dolunca mesaj TUKETICI kuyruguna geri doner.
                    .deadLetterExchange(RETRY_EXCHANGE)
                    .deadLetterRoutingKey(consumerQueue)
                    .build();

            declarations.add(queue);
            declarations.add(BindingBuilder.bind(queue)
                    .to(new DirectExchange(RETRY_EXCHANGE, true, false))
                    .with(name));
        }

        return new Declarables(declarations);
    }

    @Bean
    Declarables employeeRetryLadder() {
        return ladderFor(QUEUE);
    }

    @Bean
    Declarables accountRetryLadder() {
        return ladderFor(ACCOUNT_QUEUE);
    }

    @Bean
    Declarables leaveRetryLadder() {
        return ladderFor(LEAVE_QUEUE);
    }

    /**
     * Tuketici kuyruklari retry exchange'ine de baglanir.
     *
     * Bu baglanti olmadan basamaktan dusen mesaj hicbir kuyruga ulasmaz ve
     * SESSIZCE kaybolurdu -- yayinlanmis ama yonlendirilememis mesaj.
     *
     * Anahtar kuyrugun KENDI ADI: boylece geri donus yalnizca basarisiz olan
     * tuketiciyi hedefler. Ozgun topic anahtari kullanilsaydi ayni olay,
     * zaten basariyla islemis DIGER tuketicilere de tekrar giderdi.
     */
    @Bean
    Declarables retryReturnBindings(Queue notificationQueue, Queue accountQueue,
                                    Queue leaveQueue, DirectExchange retryExchange) {
        return new Declarables(
                BindingBuilder.bind(notificationQueue).to(retryExchange).with(QUEUE),
                BindingBuilder.bind(accountQueue).to(retryExchange).with(ACCOUNT_QUEUE),
                BindingBuilder.bind(leaveQueue).to(retryExchange).with(LEAVE_QUEUE));
    }

    // Uretici mesaji application/json olarak gonderiyor; bu donusturucu onu
    // dogrudan EmployeeEvent'e cevirir.
    @Bean
    MessageConverter jsonMessageConverter(Jackson2ObjectMapperBuilder builder) {
        return new Jackson2JsonMessageConverter(builder.build());
    }

    /**
     * Surec ici retry bittiginde mesaji merdivene tasir, sonunda park eder.
     *
     * Varsayilan davranis mesaji reddetmek ve dogrudan DLX'e birakmaktir; o
     * zaman DLQ'da yalnizca x-death sayaci bulunur, NEDEN basarisiz oldugu
     * bilinmez. Recoverer hem x-exception-message basligini ekler hem de
     * gecici arizalarin parka hic ulasmamasini saglar.
     */
    @Bean
    MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RetryLadderRecoverer(rabbitTemplate, RETRY_EXCHANGE, DLX);
    }
}
