package com.proje.notification.service;

import com.proje.notification.entity.ProcessedEvent;
import com.proje.notification.event.EmployeeEvent;
import com.proje.notification.repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Olayi "bu benim" diye isaretler.
 *
 * <p><b>Neden ayri bir bean ve neden REQUIRES_NEW?</b> Onceki hali bu isi
 * dinleyicinin KENDI transaction'i icinde yapiyordu. Birincil anahtar ihlal
 * edildiginde Hibernate transaction'i <b>rollback-only</b> isaretler; istisna
 * yakalanip {@code false} donulse bile dinleyici normal bitince proxy commit'e
 * gecer ve {@code UnexpectedRollbackException} firlar.
 *
 * <p>Net etki: mesaj reddedilir, yeniden teslim edilir ve ancak o zaman hizli
 * yoldan atlanir. Calisir -- ama bir retry hakki yakar ve tamamen normal bir
 * yaris icin korkutucu bir yigin izi basar.
 *
 * <p><b>Bu, projedeki en ogretici test kusuruydu:</b> birim test geciyordu
 * cunku Mockito sahtesinde <em>geri alinacak bir transaction yoktur</em>. Test
 * yesil, uretim yanlis davraniyordu. Kural: <b>transaction sinirini
 * ilgilendiren bir davranis, gercek veritabani olmadan dogrulanmis
 * sayilmaz.</b>
 *
 * <p>REQUIRES_NEW ile ihlal YALNIZCA bu kucuk transaction'i geri alir;
 * disaridaki mail transaction'i saglam kalir.
 */
@Service
public class EventClaimService {

    private final ProcessedEventRepository processedEventRepository;

    public EventClaimService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * @return olay ilk kez bu tuketici tarafindan sahiplenildiyse true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(EmployeeEvent event) {
        // Hizli yol: mukerrer teslimlerin buyuk cogunlugu burada elenir ve
        // istisna maliyeti hic odenmez.
        if (processedEventRepository.existsById(event.eventId())) {
            return false;
        }

        // saveAndFlush SART: duz save() yazmayi yalnizca kuyruga alir ve gercek
        // INSERT commit aninda, yani mail coktan gittikten sonra calisirdi. O
        // durumda birincil anahtar mukerrer SATIRI engelleyebilir ama mukerrer
        // MAILI engelleyemezdi.
        //
        // ISTISNA BURADA YAKALANMAZ ve bu bilinclidir. Yakalansaydi transaction
        // hala rollback-only isaretli kalir ve proxy commit'e gectiginde
        // UnexpectedRollbackException firlardi -- REQUIRES_NEW hasari ic
        // transaction'a hapseder ama onun KENDI commit'ini kurtarmaz. Olculdu.
        //
        // Disari birakilinca Spring ic transaction'i normal sekilde geri alir
        // ve OZGUN istisnayi firlatir; cagiran onu transaction sinirinin
        // DISINDA yakalar.
        processedEventRepository.saveAndFlush(new ProcessedEvent(
                event.eventId(), event.eventType().name(), event.employeeId()));
        return true;
    }
}
