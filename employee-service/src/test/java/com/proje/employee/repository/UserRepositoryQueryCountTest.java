package com.proje.employee.repository;

import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hesap listesinin sorgu sayisi.
 *
 * EmployeeMapperTest ayni korumayi personel listesi icin kuruyor; bu, hesap
 * listesinin karsiligi. Sorgu sayisini bir IDDIA olarak tutmak, N+1'in geri
 * gelmesini derleme zamaninda degil ama CI zamaninda yakalar.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryQueryCountTest {

    private static final int PAGE_SIZE = 10;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        // Test onceki duruma bagimli olmamali. @DataJpaTest transaction icinde
        // calisip geri aldigi icin bu silme kalici degil.
        entityManager.createQuery("DELETE FROM LeaveRequest").executeUpdate();
        entityManager.createQuery("UPDATE User u SET u.employee = null").executeUpdate();
        userRepository.deleteAllInBatch();

        for (int i = 0; i < PAGE_SIZE; i++) {
            User user = new User("count" + i + "@example.com", "hash",
                    Set.of(Role.EMPLOYEE, Role.MANAGER));
            userRepository.save(user);
        }
        entityManager.flush();
        entityManager.clear();

        statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    @DisplayName("Loading a page of accounts with their roles stays at a constant query count")
    void listingAccountsDoesNotScaleWithPageSize() {
        var page = userRepository.findAllWithEmployee(
                PageRequest.of(0, PAGE_SIZE, Sort.by("email")));

        // Rollere DOKUNULUR: UserMapper.toResponse zaten okuyor, yani koleksiyon
        // uretimde her zaman baslatiliyor. Dokunmasaydik test bos yere gecerdi.
        long rolesSeen = page.getContent().stream().mapToLong(u -> u.getRoles().size()).sum();
        assertThat(rolesSeen).isEqualTo(PAGE_SIZE * 2L);

        // Olculdu: @BatchSize olmadan sayfadaki HER kullanici icin ayri bir
        // "SELECT ... FROM user_role WHERE user_id = ?" atiliyordu --
        // 10 satirlik sayfada 1 + 10 + 1 = 12 sorgu.
        //
        // @ElementCollection'i JOIN FETCH ile cekmek cozum DEGIL: koleksiyon
        // fetch'i SQL seviyesinde sayfalamayi bozar ve Hibernate tum satirlari
        // bellege alip orada sayfalar. Toplu cekim (@BatchSize) sayfalamayi
        // bozmadan N sorguyu tek IN sorgusuna indirir.
        assertThat(statistics.getPrepareStatementCount())
                .as("a page of %d accounts must not cost one query per account", PAGE_SIZE)
                .isLessThanOrEqualTo(4);
    }
}
