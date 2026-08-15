package com.proje.employee.repository;

import com.proje.employee.entity.Employee;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Gosterge panelinin toplama sorgulari.
 *
 * Ayri bir arayuz: bunlar okuma-raporlama sorgulari ve EmployeeRepository'nin
 * is akisiyla ilgisi yok. Karistirilsalardi "hangi sorgu uretimde hangisi
 * raporda" ayrimi kaybolurdu.
 *
 * Sorgular NATIVE: date_trunc ve generate_series gibi PostgreSQL islevlerinin
 * JPQL karsiligi yok. Raporlama, veritabaninin gucunu kullanmanin dogru yeri.
 */
public interface DashboardRepository extends Repository<Employee, Long> {

    interface Headcount {
        long getActiveCount();

        long getInactiveCount();

        long getHiredLast30Days();

        long getHiredLast90Days();

        long getLeftLast12Months();
    }

    interface DepartmentHeadcount {
        String getDepartmentName();

        long getActiveCount();
    }

    /**
     * Aylik seri: bir ay ve o aya dusen sayi.
     *
     * Ayrilma ve ise alim sorgulari AYNI sekli donduruyor; ikinci gercek
     * kullanim ortaya ciktigi icin ortak bir projeksiyon artik hak edilmis.
     * Anlam DTO tarafinda tasinir (leavers / hires) -- projeksiyon yalnizca
     * bicimi tarif eder.
     */
    interface MonthlyCount {
        String getMonth();

        long getTotal();
    }

    interface ReasonCount {
        String getReason();

        long getLeaverCount();
    }

    interface SpanOfControl {
        long getManagerCount();

        double getAverageDirectReports();

        long getLargestTeamSize();
    }

    /**
     * FILTER (WHERE ...) tek gecişte birden fazla kosullu sayim yapar.
     *
     * Alternatifi bes ayri sorgu olurdu; tablo bir kez taranir ve sayaclar
     * ayni taramada doldurulur.
     */
    @Query(value = """
            SELECT count(*) FILTER (WHERE is_active)                          AS activeCount,
                   count(*) FILTER (WHERE NOT is_active)                      AS inactiveCount,
                   count(*) FILTER (WHERE hire_date > current_date - 30)      AS hiredLast30Days,
                   count(*) FILTER (WHERE hire_date > current_date - 90)      AS hiredLast90Days,
                   count(*) FILTER (WHERE terminated_at > current_date - 365) AS leftLast12Months
            FROM employee
            """, nativeQuery = true)
    Headcount headcount();

    // LEFT JOIN sart: personeli olmayan departman da listede gorunmeli.
    // Bos bir departman, panelin gostermesi gereken bir bulgudur.
    @Query(value = """
            SELECT d.name                                  AS departmentName,
                   count(e.id) FILTER (WHERE e.is_active)  AS activeCount
            FROM department d
            LEFT JOIN employee e ON e.department_id = d.id
            GROUP BY d.name
            ORDER BY 2 DESC, 1
            """, nativeQuery = true)
    List<DepartmentHeadcount> headcountByDepartment();

    /**
     * Son 12 ayin ayrilma sayilari, BOS AYLAR DAHIL.
     *
     * generate_series olmasaydi ayrilma olmayan aylar sonuc kumesinde hic
     * gorunmez ve grafik o aylari atlayarak cizerdi -- "Mart yok" ile
     * "Mart'ta kimse ayrilmadi" cok farkli seylerdir.
     */
    @Query(value = """
            SELECT to_char(m.month, 'YYYY-MM') AS month,
                   count(e.id)                 AS total
            FROM generate_series(date_trunc('month', current_date) - interval '11 months',
                                 date_trunc('month', current_date),
                                 interval '1 month') AS m(month)
            LEFT JOIN employee e
                   ON date_trunc('month', e.terminated_at) = m.month
            GROUP BY m.month
            ORDER BY m.month
            """, nativeQuery = true)
    List<MonthlyCount> turnoverByMonth();

    /**
     * Son 12 ayin ise alim sayilari, ayrilmalarla AYNI eksende.
     *
     * Ayri bir pencere kullanilsaydi (ornegin son 6 ay) iki seri ust uste
     * okunamazdi; "bu ay 7 kisi ayrildi" tek basina anlamsizdir, ayni ay kac
     * kisinin katildigi yaninda durmadikca.
     */
    @Query(value = """
            SELECT to_char(m.month, 'YYYY-MM') AS month,
                   count(e.id)                 AS total
            FROM generate_series(date_trunc('month', current_date) - interval '11 months',
                                 date_trunc('month', current_date),
                                 interval '1 month') AS m(month)
            LEFT JOIN employee e
                   ON date_trunc('month', e.hire_date) = m.month
            GROUP BY m.month
            ORDER BY m.month
            """, nativeQuery = true)
    List<MonthlyCount> hiresByMonth();

    @Query(value = """
            SELECT termination_reason AS reason,
                   count(*)           AS leaverCount
            FROM employee
            WHERE terminated_at IS NOT NULL
            GROUP BY termination_reason
            ORDER BY 2 DESC
            """, nativeQuery = true)
    List<ReasonCount> terminationReasons();

    /**
     * Yonetici basina dusen ast sayisi (span of control).
     *
     * Ic sorgu once her yoneticinin ast sayisini bulur, dis sorgu onlarin
     * ortalamasini alir. Dogrudan "count / count(distinct)" yazilsaydi ayni
     * sonucu verirdi ama en buyuk ekip bulunamazdi.
     */
    @Query(value = """
            SELECT count(*)                     AS managerCount,
                   coalesce(avg(team.size), 0)  AS averageDirectReports,
                   coalesce(max(team.size), 0)  AS largestTeamSize
            FROM (SELECT manager_id, count(*) AS size
                  FROM employee
                  WHERE manager_id IS NOT NULL AND is_active
                  GROUP BY manager_id) AS team
            """, nativeQuery = true)
    SpanOfControl spanOfControl();

    // Veri kalitesi uyarilari: panelin en cok ise yarayan parcasi sayi degil,
    // eyleme donusen bulgudur.
    @Query(value = """
            SELECT count(*) FROM employee WHERE is_active AND manager_id IS NULL
            """, nativeQuery = true)
    long countActiveWithoutManager();

    @Query(value = """
            SELECT count(*) FROM department d
            WHERE NOT EXISTS (SELECT 1 FROM employee e
                              WHERE e.department_id = d.id AND e.is_active)
            """, nativeQuery = true)
    long countEmptyDepartments();

    interface OrgNode {
        Long getEmployeeId();

        String getFirstName();

        String getLastName();

        String getJobTitle();

        String getDepartmentName();

        Long getManagerId();

        int getDepth();
    }

    /**
     * Organizasyon agaci, TEK sorguda ve YUKARIDAN ASAGIYA.
     *
     * assertNoCycle'daki CTE yukari dogru yuruyordu; bu onun aynasi. Cikis
     * satirlari KOKLERDIR (yoneticisi olmayan aktif personel) ve her adimda
     * bir seviye asagi inilir.
     *
     * DERINLIK SIGORTASI ayni sebeple sart: veride dolayli bir dongu varsa
     * ozyineleme sonsuza kadar calisir. Veritabani CHECK kisiti yalnizca
     * "kendi kendinin yoneticisi" durumunu yakalar.
     *
     * Yalnizca AKTIF personel: ayrilmis birinin altinda duran ekip, artik var
     * olmayan bir raporlama cizgisini gosterirdi. Bunun bedeli, yoneticisi
     * pasiflesmis personelin agaca hic girmemesidir -- servis bunu SAYAR ve
     * cevapta ayrica bildirir, sessizce kaybetmez.
     */
    @Query(value = """
            WITH RECURSIVE org AS (
                SELECT e.id, e.first_name, e.last_name, e.job_title,
                       e.department_id, e.manager_id, 1 AS depth
                FROM employee e
                WHERE e.manager_id IS NULL AND e.is_active

                UNION ALL

                SELECT e.id, e.first_name, e.last_name, e.job_title,
                       e.department_id, e.manager_id, o.depth + 1
                FROM employee e
                JOIN org o ON e.manager_id = o.id
                WHERE e.is_active AND o.depth < :maxDepth
            )
            SELECT o.id            AS employeeId,
                   o.first_name    AS firstName,
                   o.last_name     AS lastName,
                   o.job_title     AS jobTitle,
                   d.name          AS departmentName,
                   o.manager_id    AS managerId,
                   o.depth         AS depth
            FROM org o
            JOIN department d ON d.id = o.department_id
            ORDER BY o.depth, o.last_name, o.first_name
            """, nativeQuery = true)
    List<OrgNode> orgChart(@Param("maxDepth") int maxDepth);

    /** Agaca girmesi BEKLENEN toplam: farki "ulasilamayan" demektir. */
    @Query(value = "SELECT count(*) FROM employee WHERE is_active", nativeQuery = true)
    long countActive();
}
