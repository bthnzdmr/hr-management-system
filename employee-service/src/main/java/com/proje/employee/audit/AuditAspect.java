package com.proje.employee.audit;

import com.proje.employee.config.CorrelationIdFilter;
import com.proje.employee.service.AccessScope;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Denetim izini yazan aspect.
 *
 * AOP'un ders kitabi kullanimi: denetim, CAPRAZ KESEN bir ilgidir (cross-cutting
 * concern). Her servis metoduna elle "kaydet" satiri eklemek, o satirin bir yerde
 * unutulmasi demektir -- ve unutuldugu yer tam da denetlenmesi gereken yerdir.
 *
 * <p><b>@AfterReturning secildi, @Around degil:</b> yalnizca BASARIYLA biten islem
 * kaydedilir. Istisna firlatan bir cagri zaten hicbir sey degistirmedi; onu
 * "yapildi" diye yazmak izi yalanci kilardi. Basarisiz DENEMELER ayri bir
 * ihtiyactir ve ayri bir mekanizma ister (girisimi kaydetmek, is islemi geri
 * alinsa bile kalmalidir -- yani REQUIRES_NEW).
 *
 * <p><b>Ayni transaction icinde yazilir.</b> Cagiranin transaction'ina katilir,
 * kendi transaction'ini acmaz. Boylece is islemi geri alinirsa denetim kaydi da
 * geri alinir: GERCEKLESMEMIS bir islemi kaydetmis olmayiz. Bunun bedeli,
 * basarisiz denemelerin ize girmemesidir -- yukarida yazildigi gibi, o farkli
 * bir ihtiyac.
 *
 * <p><b>AOP'un yapamadigi sey:</b> "onceki durum". Aspect metoda girmeden onceki
 * veriyi bilmez; roller degistiginde ESKI rolleri goremez. Bu bir eksiklik degil
 * bir sinir: iz EKLEMELI oldugu icin "onceki hal", ayni hedefin BIR ONCEKI
 * kaydidir. Zincir okundugunda gecmis zaten cikar.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private static final String SYSTEM_ACTOR = "system";

    private final AuditEntryRepository auditEntryRepository;

    public AuditAspect(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void record(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            auditEntryRepository.save(new AuditEntry(
                    actor(),
                    auditable.action(),
                    auditable.targetType(),
                    targetId(joinPoint, result),
                    result instanceof AuditLabel labelled ? labelled.auditLabel() : null,
                    detail(joinPoint, auditable, result),
                    MDC.get(CorrelationIdFilter.MDC_KEY)));
        } catch (RuntimeException e) {
            // Denetim kaydi is islemini DUSURMEMELI: kayit yazilamiyorsa
            // personel guncellemesi de iptal olsaydi, izleme mekanizmasi
            // sistemin kendisini kirilgan hale getirirdi.
            //
            // Sessizce yutulmuyor: WARN ile gorunur kaliyor. Bu satirlarin
            // loglarda birikmeye baslamasi, izin guvenilmez oldugunun isaretidir.
            log.warn("Audit entry could not be written for {}",
                    joinPoint.getSignature().toShortString(), e);
        }
    }

    /**
     * Islemi yapan.
     *
     * JWT filtresi principal olarak duz bir String (e-posta) koyuyor; bu yuzden
     * getName() dogru cevabi verir. Kimlik yoksa "system": tohumlayici ve
     * zamanlanmis isler de bu yoldan gecebilir.
     */
    private String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM_ACTOR;
        }
        return authentication.getName();
    }

    /**
     * Hedefin kimligi.
     *
     * Once ARGUMANLARA bakilir: guncelleme metotlarinda id ilk parametredir ve
     * islem oncesi de sonrasi da ayni kalir. Argumanda yoksa cevaba bakilir:
     * "create" gibi metotlarda kimlik ancak kayit yazildiktan sonra bellidir.
     */
    private String targetId(JoinPoint joinPoint, Object result) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Long id) {
                return id.toString();
            }
        }
        return idOf(result);
    }

    private String idOf(Object result) {
        if (result == null) {
            return null;
        }
        try {
            Method accessor = result.getClass().getMethod("id");
            Object value = accessor.invoke(result);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException e) {
            // Cevapta id yok: hedef kimligi bilinmiyor demektir, hata degil.
            return null;
        }
    }

    /**
     * Insan tarafindan okunacak ozet.
     *
     * Arguman listesinden uretilir; DTO'lar record oldugu icin toString()
     * alan adlarini da tasir. Long id ve String actor atlanir: ilki targetId'de,
     * ikincisi actor'de zaten var.
     *
     * Sirlar buraya DTO'nun kendi toString'inde maskelenerek gelir
     * (password=***), cunku toString her yerde cagrilir: log, istisna mesaji,
     * denetim kaydi. Sirri hic uretmeyen bir temsil onu her yerde korur.
     */
    private String detail(JoinPoint joinPoint, Auditable auditable, Object result) {
        if (!auditable.includeArguments()) {
            return auditable.summary().isBlank() ? null : auditable.summary();
        }

        // Cevap kendini anlatabiliyorsa SOZ ONUNDUR ve TEK BASINA yeter:
        // sonucu bilen taraf odur. `summary` eklenmiyor, cunku durum zaten
        // cevabin icinde -- "approved Approved · ..." diye tekrarlardi.
        // Arguman dokumu yalnizca bir geri dususe kaldi.
        if (result instanceof AuditDetail described) {
            String sentence = described.auditDetail();

            if (sentence != null && !sentence.isBlank()) {
                return sentence;
            }
        }

        // Parametre ADLARI da yazilir. Etiketsizken kayit "false, RESIGNED"
        // goruntusundeydi ve neyin false oldugu okunamiyordu.
        String[] names = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        Object[] args = joinPoint.getArgs();

        String rendered = IntStream.range(0, args.length)
                .filter(i -> args[i] != null && !(args[i] instanceof Long)
                        && !(args[i] instanceof String))
                .filter(i -> readable(args[i]))
                .mapToObj(i -> label(names, i) + args[i])
                .collect(Collectors.joining(", "));

        if (!auditable.summary().isBlank()) {
            return rendered.isBlank() ? auditable.summary() : auditable.summary() + ": " + rendered;
        }
        return rendered.isBlank() ? null : rendered;
    }

    /** Parametre adlari derleyici ayarina bagli; yoksa etiketsiz yazilir. */
    private static String label(String[] names, int index) {
        return names != null && index < names.length && names[index] != null
                ? names[index] + "="
                : "";
    }

    /**
     * Yalnizca insan tarafindan okunabilir ozetler yazilir.
     *
     * Entity'ler DISLANIR: toString'i olmayaninki kimlik karmasi basar, olani
     * ise butun alanlarini -- ornegin passwordHash -- ize dokebilir.
     *
     * IC MAKINE de dislanir. `AccessScope` bir record oldugu icin bu suzgecten
     * geciyordu ve denetim ekraninda
     * "kind: ALL · Employee: null · allSalaries: false" gorunuyordu. Bir kapsam
     * nesnesi kullaniciya hicbir sey soylemez ve okuma aninda duzeltilemez --
     * orada bilgi YOKTUR. Kaydedilmemesi gereken sey kaydedilmez.
     */
    private static boolean readable(Object arg) {
        if (arg instanceof AccessScope) {
            return false;
        }

        Class<?> type = arg.getClass();

        return type.isRecord() || type.isEnum() || type.getPackageName().startsWith("java.");
    }
}
