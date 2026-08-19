package com.proje.employee.config;

import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveType;
import com.proje.employee.entity.User;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.LeaveRequestRepository;
import com.proje.employee.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Ornek izin verisi.
 *
 * NEDEN AYRI BIR TOHUMLAYICI: personel tohumlayicisi zaten 270 satir ve izin
 * baska bir sey tohumluyor -- farkli tablo, farkli on kosul (personel VE
 * hesap hazir olmali).
 *
 * NEDEN GEREKLI: takvim ekrani yazildiginda ekranda hicbir sey yoktu.
 * Veritabanindaki izinler onceki oturumlarda ELLE girilmis test kayitlariydi
 * ve 2027, 2051, 2084 gibi yillara dagilmisti; yani ozellik calisiyordu ama
 * gosterecek verisi yoktu.
 */
@Configuration
public class DemoLeaveSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoLeaveSeeder.class);

    /**
     * Tohumlanan satirlari tanitan not.
     *
     * Ayri bir "isaretci KAYIT" yazilmadi: personel tohumlayicisinda tam da o
     * denenmisti ve sahte satir personel listesinde ve devir orani sorgusunda
     * gorunuyordu. Burada isaret verinin KENDI alaninda durur ve kullaniciya
     * gosterildiginde de dogru bir sey soyler.
     */
    static final String MARKER = "Seeded demo request";

    /**
     * Bir izin: kimin, ne tur, BUGUNE gore kac gun sonra baslar, kac gun surer.
     *
     * Tarihler MUTLAK degil GOREL. Mutlak yazilsaydi tohum birkac ay sonra
     * gecmiste kalir ve takvim yine bos acilirdi -- duzeltmeye calistigimiz
     * sorunun ta kendisi.
     */
    private record Leave(String email, LeaveType type, int startsInDays, int lengthDays,
                         Decision decision, String note) {
    }

    private enum Decision {
        APPROVED, PENDING, REJECTED, CANCELLED
    }

    /**
     * Ayni kisinin izinleri BIRBIRIYLE CAKISMAMALI: veritabaninda bir dislama
     * kisiti var (`ex_leave_no_overlap`) ve cakisan bir tohum uygulamayi
     * ACILISTA dusururdu. Reddedilmis ve iptal edilmis kayitlar kisitin
     * disinda kalir, o yuzden onlar cakisabilir.
     *
     * Aralik bilerek genis: onceki aydan gelecek aya kadar yayiliyor, boylece
     * takvim hangi ay acilirsa acilsin bos gorunmuyor.
     */
    private static final List<Leave> LEAVES = List.of(
            new Leave("ada.lovelace@demo.example.com", LeaveType.ANNUAL, -18, 5,
                    Decision.APPROVED, "Family visit"),
            new Leave("grace.hopper@demo.example.com", LeaveType.SICK, -12, 2,
                    Decision.APPROVED, "Flu"),
            new Leave("margaret.hamilton@demo.example.com", LeaveType.ANNUAL, -5, 4,
                    Decision.APPROVED, null),
            new Leave("katherine.johnson@demo.example.com", LeaveType.ANNUAL, -2, 6,
                    Decision.APPROVED, "Half term"),
            new Leave("donald.knuth@demo.example.com", LeaveType.PARENTAL, 0, 10,
                    Decision.APPROVED, "Parental leave"),
            new Leave("barbara.liskov@demo.example.com", LeaveType.ANNUAL, 3, 3,
                    Decision.PENDING, "Wedding"),
            new Leave("edsger.dijkstra@demo.example.com", LeaveType.ANNUAL, 6, 5,
                    Decision.APPROVED, null),
            new Leave("annie.easley@demo.example.com", LeaveType.SICK, 8, 1,
                    Decision.PENDING, "Dentist"),
            new Leave("dorothy.vaughan@demo.example.com", LeaveType.ANNUAL, 11, 7,
                    Decision.APPROVED, "Summer break"),
            new Leave("mary.jackson@demo.example.com", LeaveType.UNPAID, 15, 4,
                    Decision.PENDING, "Personal matters"),
            new Leave("alan.kay@demo.example.com", LeaveType.ANNUAL, 19, 5,
                    Decision.APPROVED, null),
            new Leave("john.backus@demo.example.com", LeaveType.ANNUAL, 24, 3,
                    Decision.PENDING, null),
            // Reddedilen ve iptal edilen kayitlar BILEREK var: takvimin onlari
            // GOSTERMEDIGINI, listenin ise gosterdigini ancak veri varsa
            // gorebiliriz.
            new Leave("ada.lovelace@demo.example.com", LeaveType.UNPAID, 12, 3,
                    Decision.REJECTED, "Unpaid month"),
            new Leave("grace.hopper@demo.example.com", LeaveType.ANNUAL, 20, 2,
                    Decision.CANCELLED, "Changed plans"));

    @Bean
    // Personel ve hesaplar hazir olmali: izin ikisine de baglaniyor.
    @Order(UserSeeder.SEED_ACCOUNTS_FIRST + 2)
    ApplicationRunner seedDemoLeave(EmployeeRepository employees,
                                    UserRepository users,
                                    LeaveRequestRepository leaveRequests,
                                    @Value("${app.demo-data.enabled:false}") boolean enabled,
                                    @Value("${app.admin.email:}") String adminEmail) {

        return args -> {
            if (!enabled) {
                return;
            }
            seed(employees, users, leaveRequests, adminEmail);
        };
    }

    @Transactional
    void seed(EmployeeRepository employees, UserRepository users,
              LeaveRequestRepository leaveRequests, String adminEmail) {

        if (leaveRequests.existsByNoteContaining(MARKER)) {
            log.info("Demo leave already present, nothing seeded");
            return;
        }

        Optional<User> recorder = users.findByEmail(adminEmail);

        if (recorder.isEmpty()) {
            // Sessizce gecmek yanlis olurdu: izin kaydinin "kim girdi" alani
            // zorunlu ve eksik bir tohum, sebebi anlasilmayan bir bosluk
            // birakirdi.
            log.warn("Demo leave skipped: no account found for {}", adminEmail);
            return;
        }

        LocalDate today = LocalDate.now();
        int seeded = 0;

        for (Leave leave : LEAVES) {
            Optional<Employee> employee = employees.findByEmail(leave.email());

            if (employee.isEmpty()) {
                // Personel tohumu degistirilmis olabilir; bir eksik kayit
                // butun tohumlamayi dusurmemeli.
                log.warn("Demo leave skipped for unknown employee {}", leave.email());
                continue;
            }

            LocalDate start = today.plusDays(leave.startsInDays());
            LeaveRequest request = new LeaveRequest(
                    employee.get(), recorder.get(), leave.type(),
                    start, start.plusDays(leave.lengthDays() - 1L),
                    leave.note() == null ? MARKER : leave.note() + " (" + MARKER + ")");

            switch (leave.decision()) {
                case APPROVED -> request.approve(recorder.get());
                case REJECTED -> request.reject(recorder.get(), "Not enough cover that week");
                case CANCELLED -> request.cancel();
                case PENDING -> {
                    // Karar verilmez: takvimde kesikli cerceveyle gorunmeli.
                }
            }

            leaveRequests.save(request);
            seeded++;
        }

        log.info("Seeded {} demo leave requests around {}", seeded, today);
    }
}
