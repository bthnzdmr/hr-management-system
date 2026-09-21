package com.proje.employee.service;

import com.proje.employee.dto.NotificationPreferenceResponse;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.NotificationKind;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.NotificationMuteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tercihler GERCEK veritabanina karsi sinaniyor.
 *
 * <p>Taklit edilen bir repository, bilesik birincil anahtarin ve `ON DELETE
 * CASCADE`in dogru kuruldugunu HICBIR sekilde kanitlamaz -- bu ders projede
 * bes kez alindi.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificationPreferenceService.class)
class NotificationPreferenceServiceTest {

    @Autowired
    private NotificationPreferenceService service;

    @Autowired
    private NotificationMuteRepository mutes;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private DepartmentRepository departments;

    private Employee ada;
    private Employee grace;

    @BeforeEach
    void setUp() {
        // Personel tablosu SUPURULMUYOR: bu test temiz bir kadroya ihtiyac
        // duymuyor ve `deleteAllInBatch()` gelistirme veritabanindaki izin,
        // hak ve hesap yabanci anahtarlarina takiliyor -- ayni tuzak projede
        // iki kez yasandi. Izolasyonu zaten @DataJpaTest'in geri aldigi
        // transaction sagliyor; test yalnizca KENDI kayitlarini kuruyor.
        mutes.deleteAllInBatch();

        Department engineering = departments.save(new Department("Pref " + UUID.randomUUID()));
        grace = employees.save(new Employee("Grace", "Hopper",
                "grace." + UUID.randomUUID() + "@example.com",
                engineering, "Director", LocalDate.of(2020, 1, 6)));
        ada = employees.save(new Employee("Ada", "Lovelace",
                "ada." + UUID.randomUUID() + "@example.com",
                engineering, "Engineer", LocalDate.of(2021, 3, 1)));
    }

    private Set<NotificationKind> enabledIn(NotificationPreferenceResponse response) {
        return response.items().stream()
                .filter(NotificationPreferenceResponse.Item::enabled)
                .map(NotificationPreferenceResponse.Item::kind)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("Starts with every notification switched on")
    void defaultsToEverythingOn() {
        // Varsayilan KODDA yazili, tabloda degil: hicbir satir yokken de
        // cevap dolu gelmeli.
        NotificationPreferenceResponse response = service.forEmployee(ada.getId());

        assertThat(response.items()).hasSameSizeAs(NotificationKind.values());
        assertThat(enabledIn(response)).containsExactlyInAnyOrder(NotificationKind.values());
        assertThat(mutes.count()).isZero();
    }

    @Test
    @DisplayName("Stores only the deviation from the default")
    void storesOnlyMutes() {
        service.replace(ada.getId(), Set.of(NotificationKind.LEAVE_DECISION));

        // Acik olan tur icin SATIR YAZILMAZ; tabloda yalnizca sapma durur.
        assertThat(mutes.findKindsByEmployeeId(ada.getId()))
                .containsExactly(NotificationKind.LEAVE_REQUEST);
    }

    @Test
    @DisplayName("Replacing the whole set is idempotent")
    void replacingIsIdempotent() {
        // Artimli bir uc olsaydi iki es zamanli istek birbirinin uzerine
        // yazabilirdi; komple gondermek bunu yapisal olarak imkansiz kilar.
        service.replace(ada.getId(), Set.of(NotificationKind.LEAVE_DECISION));
        service.replace(ada.getId(), Set.of(NotificationKind.LEAVE_DECISION));

        assertThat(mutes.findKindsByEmployeeId(ada.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Turning a notification back on removes its row")
    void reEnablingDeletesTheRow() {
        service.replace(ada.getId(), Set.of());
        assertThat(mutes.findKindsByEmployeeId(ada.getId())).hasSize(2);

        NotificationPreferenceResponse back = service.replace(ada.getId(),
                Set.of(NotificationKind.LEAVE_REQUEST, NotificationKind.LEAVE_DECISION));

        assertThat(mutes.findKindsByEmployeeId(ada.getId())).isEmpty();
        assertThat(enabledIn(back)).containsExactlyInAnyOrder(NotificationKind.values());
    }

    @Test
    @DisplayName("Keeps each person's preferences separate")
    void preferencesAreNotShared() {
        service.replace(ada.getId(), Set.of());

        assertThat(enabledIn(service.forEmployee(grace.getId())))
                .containsExactlyInAnyOrder(NotificationKind.values());
    }

    @Test
    @DisplayName("Reads several people's preferences in one query")
    void readsSeveralPeopleAtOnce() {
        // Olay yayininda hem personelin hem yoneticisinin tercihi lazim; ayri
        // ayri sorulsaydi her olay iki gidis donus ederdi.
        service.replace(ada.getId(), Set.of(NotificationKind.LEAVE_DECISION));
        service.replace(grace.getId(), Set.of(NotificationKind.LEAVE_REQUEST));

        Map<Long, Set<NotificationKind>> muted =
                service.mutedFor(Set.of(ada.getId(), grace.getId()));

        assertThat(muted.get(ada.getId())).containsExactly(NotificationKind.LEAVE_REQUEST);
        assertThat(muted.get(grace.getId())).containsExactly(NotificationKind.LEAVE_DECISION);
    }

    @Test
    @DisplayName("Returns nothing rather than querying for an empty set of people")
    void emptyRequestSkipsTheQuery() {
        assertThat(service.mutedFor(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("Lets go of the preferences when the employee record is removed")
    void deletingAnEmployeeRemovesTheirPreferences() {
        // Garanti veritabani kisitindan gelir (`ON DELETE CASCADE`), uygulama
        // kodundan degil: baska bir yoldan silinen bir personel de artik
        // kimsenin okumayacagi satirlar birakmamali.
        service.replace(ada.getId(), Set.of());
        assertThat(mutes.count()).isEqualTo(2);

        employees.deleteById(ada.getId());
        employees.flush();

        assertThat(mutes.count()).isZero();
    }
}
