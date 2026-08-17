package com.proje.employee.audit;

/**
 * Denetim izinde kaydin YANINDA duracak insan okunur ad.
 *
 * Ad, kayit YAZILIRKEN sabitlenir; okurken JOIN ile cekilmez. Iki sebeple:
 * kayit sonradan yeniden adlandirilirsa iz o gunku adi gostermeye devam
 * etmeli, ve hedef bir gun kaybolursa iz anlamsizlasmamali. Denetim izi,
 * referans verdigi satirin BUGUNKU durumuna bagli olmamalidir.
 *
 * Isim tabanli yansima yerine acik bir arayuz: hangi cevabin ad urettigi
 * derleyici tarafindan gorulur ve bir alan yeniden adlandirildiginda
 * sessizce bozulmaz.
 */
public interface AuditLabel {

    /** Ornek: "Ada Lovelace", "ada@example.com". Sir icermez. */
    String auditLabel();
}
