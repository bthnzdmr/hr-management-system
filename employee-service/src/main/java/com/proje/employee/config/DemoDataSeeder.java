package com.proje.employee.config;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.TerminationReason;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Gelistirme ve tanitim icin gercekci bir organizasyon uretir. */
@Configuration
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    // Tohumun daha once atildigini LISTENIN SON kaydina bakarak anlariz.
    //

    private record Seed(String first, String last, String title, String department,
                        int hireYear, int hireMonth, String managerEmail,
                        String salary, boolean active,
                        String leftOn, TerminationReason reason) {

        /** Hala calisan personel icin kisa yol. */
        static Seed active(String first, String last, String title, String department,
                           int hireYear, int hireMonth, String managerEmail, String salary) {
            return new Seed(first, last, title, department, hireYear, hireMonth,
                    managerEmail, salary, true, null, null);
        }

        /** Ayrilmis personel: tarih ve sebep ZORUNLU, cunku devir orani buna dayanir. */
        static Seed left(String first, String last, String title, String department,
                         int hireYear, int hireMonth, String salary,
                         String leftOn, TerminationReason reason) {
            return new Seed(first, last, title, department, hireYear, hireMonth,
                    null, salary, false, leftOn, reason);
        }
    }

    /** Ozellikle cesitli: */
    private static final List<Seed> PEOPLE = List.of(
            // --- Yonetim ---
            Seed.active("Barbara", "Liskov", "Chief Technology Officer", "Software Development",
                    2019, 3, null, "180000"),
            Seed.active("Grace", "Hopper", "Engineering Manager", "Software Development",
                    2020, 1, "barbara.liskov@demo.example.com", "145000"),
            Seed.active("Katherine", "Johnson", "Head of Finance", "Accounting",
                    2019, 9, null, "150000"),
            Seed.active("Mary", "Jackson", "Head of People", "Human Resources",
                    2020, 6, null, "138000"),
            Seed.active("Dorothy", "Vaughan", "Sales Director", "Sales",
                    2021, 2, null, "142000"),
            Seed.active("Annie", "Easley", "Marketing Director", "Marketing",
                    2021, 8, null, "134000"),

            // --- Yazilim ---
            Seed.active("Alan", "Kay", "Senior Engineer", "Software Development",
                    2021, 4, "grace.hopper@demo.example.com", "128000"),
            Seed.active("Ada", "Lovelace", "Engineer", "Software Development",
                    2022, 9, "grace.hopper@demo.example.com", "112000"),
            Seed.active("Edsger", "Dijkstra", "Engineer", "Software Development",
                    2023, 1, "grace.hopper@demo.example.com", "108000"),
            Seed.active("Donald", "Knuth", "Engineer", "Software Development",
                    2023, 6, "grace.hopper@demo.example.com", "106000"),
            Seed.active("Margaret", "Hamilton", "Staff Engineer", "Software Development",
                    2020, 11, "barbara.liskov@demo.example.com", "152000"),
            Seed.active("Linus", "Torvalds", "Platform Engineer", "Software Development",
                    2024, 2, "margaret.hamilton@demo.example.com", "118000"),
            Seed.active("Radia", "Perlman", "Network Engineer", "Software Development",
                    2024, 7, "margaret.hamilton@demo.example.com", "121000"),
            Seed.active("Ken", "Thompson", "Junior Engineer", "Software Development",
                    2025, 3, "alan.kay@demo.example.com", "84000"),
            Seed.active("Dennis", "Ritchie", "Junior Engineer", "Software Development",
                    2025, 9, "alan.kay@demo.example.com", "82000"),
            Seed.active("Bjarne", "Stroustrup", "Intern", "Software Development",
                    2026, 6, "alan.kay@demo.example.com", "48000"),

            // --- Muhasebe ---
            Seed.active("Luca", "Pacioli", "Senior Accountant", "Accounting",
                    2021, 5, "katherine.johnson@demo.example.com", "98000"),
            Seed.active("Mary", "Addison", "Accountant", "Accounting",
                    2023, 3, "katherine.johnson@demo.example.com", "84000"),
            Seed.active("Peter", "Drucker", "Payroll Specialist", "Accounting",
                    2024, 10, "katherine.johnson@demo.example.com", "79000"),
            Seed.active("Sofia", "Kovalevskaya", "Financial Analyst", "Accounting",
                    2025, 5, "luca.pacioli@demo.example.com", "88000"),

            // --- Insan Kaynaklari ---
            Seed.active("Lillian", "Gilbreth", "HR Specialist", "Human Resources",
                    2021, 11, "mary.jackson@demo.example.com", "92000"),
            Seed.active("Frank", "Bunker", "Recruiter", "Human Resources",
                    2023, 8, "mary.jackson@demo.example.com", "78000"),
            Seed.active("Elton", "Mayo", "HR Coordinator", "Human Resources",
                    2025, 1, "lillian.gilbreth@demo.example.com", "68000"),

            // --- Satis ---
            Seed.active("Zig", "Ziglar", "Senior Account Executive", "Sales",
                    2021, 7, "dorothy.vaughan@demo.example.com", "104000"),
            Seed.active("Mary", "Kay", "Account Executive", "Sales",
                    2022, 4, "dorothy.vaughan@demo.example.com", "94000"),
            Seed.active("Joe", "Girard", "Account Executive", "Sales",
                    2024, 1, "dorothy.vaughan@demo.example.com", "91000"),
            Seed.active("Erica", "Feldman", "Sales Development Rep", "Sales",
                    2025, 8, "zig.ziglar@demo.example.com", "72000"),
            Seed.active("Omar", "Sadik", "Sales Development Rep", "Sales",
                    2026, 2, "zig.ziglar@demo.example.com", "70000"),

            // --- Pazarlama ---
            Seed.active("David", "Ogilvy", "Brand Manager", "Marketing",
                    2022, 1, "annie.easley@demo.example.com", "101000"),
            Seed.active("Claude", "Hopkins", "Content Lead", "Marketing",
                    2023, 10, "annie.easley@demo.example.com", "89000"),
            Seed.active("Leyla", "Demir", "Marketing Specialist", "Marketing",
                    2025, 4, "david.ogilvy@demo.example.com", "74000"),
            Seed.active("Ahmet", "Yilmaz", "Growth Analyst", "Marketing",
                    2026, 1, "david.ogilvy@demo.example.com", "76000"),

            // --- Ayrilmis personel: pasif kayitlar da gercekci gorunmeli ---
            Seed.left("John", "Backus", "Engineer", "Software Development",
                    2020, 5, "115000", "2025-11-30", TerminationReason.RESIGNED),
            Seed.left("Adele", "Goldberg", "Designer", "Marketing",
                    2021, 3, "96000", "2026-01-15", TerminationReason.RESIGNED),
            Seed.left("Niklaus", "Wirth", "Engineer", "Software Development",
                    2022, 2, "110000", "2026-03-31", TerminationReason.END_OF_CONTRACT),
            Seed.left("Seymour", "Papert", "Accountant", "Accounting",
                    2023, 4, "81000", "2026-05-20", TerminationReason.RETIRED),
            Seed.left("Vera", "Molnar", "Designer", "Marketing",
                    2022, 8, "92000", "2026-06-30", TerminationReason.DISMISSED),
            Seed.left("Hedy", "Lamarr", "Engineer", "Software Development",
                    2021, 10, "119000", "2026-07-15", TerminationReason.RESIGNED));

    @Bean
    // Hesaplari personele baglar; UserSeeder'dan SONRA calismali.
    @Order(UserSeeder.SEED_ACCOUNTS_FIRST + 1)
    ApplicationRunner seedDemoData(EmployeeRepository employeeRepository,
                                   DepartmentRepository departmentRepository,
                                   UserRepository userRepository,
                                   @Value("${app.demo-data.enabled:false}") boolean enabled,
                                   @Value("${app.user.email:}") String readOnlyUserEmail,
                                   @Value("${app.demo.manager.email:}") String managerEmail) {

        return args -> {
            if (!enabled) {
                return;
            }
            seed(employeeRepository, departmentRepository);
            linkDemoAccounts(employeeRepository, userRepository, readOnlyUserEmail);
            linkManagerAccount(employeeRepository, userRepository, managerEmail);
        };
    }

    /** Salt okuyan demo hesabini gercek bir personele baglar. */
    @Transactional
    void linkDemoAccounts(EmployeeRepository employeeRepository,
                          UserRepository userRepository,
                          String readOnlyUserEmail) {

        if (readOnlyUserEmail.isBlank()) {
            return;
        }

        userRepository.findByEmail(readOnlyUserEmail).ifPresent(account -> {
            if (account.getEmployee() != null) {
                return;
            }
            // Yoneticisi olan biri secilir ki "kendi kaydini gorur" davranisi
            // ekipli bir baglamda denenebilsin.
            employeeRepository.findByEmail("ada.lovelace@demo.example.com").ifPresent(employee -> {
                account.setEmployee(employee);
                userRepository.save(account);
                log.info("Linked demo account {} to employee {}", readOnlyUserEmail, employee.getId());
            });
        });
    }

    /** Yonetici demo hesabini EKIBI OLAN bir personele baglar. */
    @Transactional
    void linkManagerAccount(EmployeeRepository employeeRepository,
                            UserRepository userRepository,
                            String managerEmail) {

        if (managerEmail.isBlank()) {
            return;
        }

        userRepository.findByEmail(managerEmail).ifPresent(account -> {
            if (account.getEmployee() != null) {
                return;
            }
            employeeRepository.findByEmail("grace.hopper@demo.example.com").ifPresent(employee -> {
                account.setEmployee(employee);
                userRepository.save(account);
                log.info("Linked manager account {} to employee {}", managerEmail, employee.getId());
            });
        });
    }

    @Transactional
    void seed(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository) {
        String marker = emailOf(PEOPLE.get(PEOPLE.size() - 1));

        if (employeeRepository.existsByEmail(marker)) {
            log.info("Demo data already present, nothing seeded");
            return;
        }

        List<Employee> saved = new ArrayList<>();

        // Iki gecis: once herkes yoneticisiz yazilir, sonra baglanir. Tek
        // geciste yazilamaz cunku bir yoneticinin kaydi astindan SONRA
        // gelebilir ve henuz var olmayan bir satira baglanamayiz.
        for (Seed person : PEOPLE) {
            Department department = departmentRepository.findByName(person.department())
                    .orElseThrow(() -> new IllegalStateException(
                            "Demo data expects department: " + person.department()));

            Employee employee = new Employee(
                    person.first(), person.last(), emailOf(person), department,
                    person.title(), LocalDate.of(person.hireYear(), person.hireMonth(), 1));

            employee.setSalary(new BigDecimal(person.salary()));

            // Pasif kayitlar tarih ve sebeple birlikte yazilir; setActive
            // diye bir yol birakilmadi, cunku yarim bilgi devir oranini
            // sessizce yanlis gosterirdi.
            if (!person.active()) {
                employee.terminate(LocalDate.parse(person.leftOn()), person.reason());
            }
            saved.add(employeeRepository.save(employee));
        }

        for (int i = 0; i < PEOPLE.size(); i++) {
            String managerEmail = PEOPLE.get(i).managerEmail();
            if (managerEmail == null) {
                continue;
            }
            Employee manager = employeeRepository.findByEmail(managerEmail)
                    .orElseThrow(() -> new IllegalStateException(
                            "Demo data references an unknown manager: " + managerEmail));

            Employee employee = saved.get(i);
            employee.setManager(manager);

            // save() ACIKCA cagrilir. EmployeeService'te setter yeterlidir
            // cunku orada metot gercekten @Transactional bir proxy uzerinden
            employeeRepository.save(employee);
        }

        log.info("Seeded {} demo employees across {} departments",
                PEOPLE.size(), departmentRepository.findByActiveTrueOrderByNameAsc().size());
    }

    private String emailOf(Seed person) {
        return (person.first() + "." + person.last() + "@demo.example.com")
                .toLowerCase(java.util.Locale.ROOT);
    }
}
