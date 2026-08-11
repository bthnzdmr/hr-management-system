# Personel Yönetim Sistemi (Mikroservis)

> Personel kayıtlarını yöneten bir web uygulaması. Kayıt değiştiğinde bildirim maili, ana uygulamanın içinde değil, **ayrı bir servis** tarafından **mesaj kuyruğu** üzerinden gönderilir.

**Durum:** Sistem uçtan uca çalışıyor. Employee Service (on REST ucu — dokuzu kimlik doğrulaması ister, rol bazlı yetkilendirme, transactional outbox), Notification Service (idempotent tüketici, DLQ, Feign, mail) ve React arayüzü hazır; tamamı tek komutla konteynerlerde ayağa kalkıyor.

---

## İçindekiler

1. [Proje Hakkında](#1-proje-hakkında)
2. [Mimari](#2-mimari)
3. [Uçtan Uca Akış](#3-uçtan-uca-akış)
4. [Teknoloji Yığını](#4-teknoloji-yığını)
5. [Servisler ve Portlar](#5-servisler-ve-portlar)
6. [Kurulum ve Çalıştırma](#6-kurulum-ve-çalıştırma)
7. [API Uçları](#7-api-uçları)
8. [Yol Haritası](#8-yol-haritası)
9. [Proje Yapısı](#9-proje-yapısı)
10. [Sorun Giderme](#10-sorun-giderme)

---

## 1. Proje Hakkında

Sistem iki işi yapar:

1. **Personel kayıtlarını yönetir** — listeleme, ekleme, güncelleme, silme. Klasik bir CRUD uygulaması.
2. **Değişiklikleri bildirir** — bir personel kaydı değiştiğinde, değişiklik bilgilerini içeren bir mail gönderir.

İkinci iş, mimarinin tamamının sebebidir. Mail gönderimi ana uygulamanın içinde yapılsaydı proje sıradan bir CRUD uygulaması olurdu. Ayrı bir servise taşındığı ve iletişim kuyruk üzerinden kurulduğu için ortaya bir **dağıtık sistem** çıkıyor: iki bağımsız uygulama, iki farklı iletişim biçimi (senkron ve asenkron), ve bunların getirdiği problemler (mesaj kaybı, tekrar teslim, servis keşfi).

---

## 2. Mimari

```
┌──────────────────────────────────────────────────────────────────┐
│  TARAYICI                                                ✅      │
│  React + TypeScript + MUI          :5173                         │
│  Giriş, personel listesi, detay sayfası, kayıt formu             │
└───────────────────────┬──────────────────────────────────────────┘
                        │  HTTP (JSON) + JWT · CORS ile izinli
                        ▼
┌──────────────────────────────────────────────────────────────────┐
│  EMPLOYEE SERVICE (Spring Boot)      :8080               ✅      │
│  • REST uçları (listele, ara, ekle, güncelle, durum değiştir)    │
│  • JWT ile kimlik doğrulama, rol bazlı yetkilendirme             │
│  • Doğrulama, iş kuralları, merkezî hata yönetimi                │
│  • Verinin tek gerçek kaynağı (source of truth)                  │
└──────┬───────────────────────────────────────────────────────────┘
       │ SQL: personel satırı + outbox satırı, tek transaction
       ▼
┌──────────────┐   OutboxRelay      ┌──────────────────┐
│ PostgreSQL   │  ✅  ─────────────►│   RabbitMQ       │  ✅  :5672
│   :5432      │   (periyodik)      │  (mesaj kuyruğu) │
└──────────────┘                    └────────┬─────────┘
                                             │ mesajı tüketir
                                             ▼
                        ┌────────────────────────────────────────┐
                        │  NOTIFICATION SERVICE   :8081    ✅    │
                        │  • Kuyruktan olayı okur, mükerrer mi   │
                        │    diye kendi veritabanına bakar       │
                        │  • Feign ile yöneticiyi sorar (CC) ────┼──► :8080
                        │  • Mail şablonunu doldurup gönderir    │
                        └───────────────┬────────────────────────┘
                                        │ SMTP
                                        ▼
                                 ┌──────────────┐
                                 │   MailHog    │  ✅  :1025 / :8025
                                 │ (sahte SMTP) │
                                 └──────────────┘

        ┌─────────────────────────────────────────────────┐
        │  EUREKA SERVER  :8761  — servis rehberi   ✅     │
        │  Her servis açılışta buraya kendini kaydeder    │
        └─────────────────────────────────────────────────┘
```

### Parçalar ve görevleri

| Parça                    | Görevi                                                      | Durum    |
| ------------------------ | ----------------------------------------------------------- | -------- |
| **React Frontend**       | Kullanıcının gördüğü arayüz                                 | ✅       |
| **Employee Service**     | Verinin sahibi; tüm iş kurallarının uygulandığı yer         | ✅       |
| **PostgreSQL**           | Personel verisinin kalıcı olarak saklandığı yer             | ✅       |
| **RabbitMQ**             | İki servis arasında mesaj taşıyan aracı                     | ✅       |
| **Notification Service** | Olayı dinleyip mail hazırlayan ve gönderen servis           | ✅       |
| **MailHog**              | Gerçek SMTP sunucusu yerine geliştirme ortamı sahtesi       | ✅       |
| **Eureka Server**        | Servislerin birbirini IP yerine **isimle** bulmasını sağlar | ✅       |

---

## 3. Uçtan Uca Akış

Bir personelin departmanının değiştirildiği senaryo, baştan sona:

```
 1. Kullanıcı formda değişikliği yapar, Kaydet'e basar
        ↓
 2. React → Axios → PUT /api/employees/42       (header'da JWT)
        ↓
 3. Spring Security filtresi: token geçerli mi? Bu kullanıcı yazma yetkisine sahip mi?
        ↓  evet
 4. Controller: gelen JSON geçerli mi?          (@Valid)
        ↓  evet
 5. Service: iş kuralları çalışır                (@Transactional başlar)
        ↓
 6. PostgreSQL'e iki INSERT/UPDATE atılır: personel satırı ve outbox satırı
        ↓
 7. Transaction COMMIT olur  ← ikisi birlikte kalıcı olur, RabbitMQ hiç devrede değil
        ↓
 8. Employee Service kullanıcıya 200 OK döner   ← kullanıcı mail için beklemez
        ↓  (paralel, saniyeler içinde)
 9. OutboxRelay outbox'taki gönderilmemiş satırı okur
        ↓
10. RabbitMQ'ya "employee.updated" olarak yayınlar, broker onaylayınca işaretler
        ↓
11. Notification Service kuyruktan mesajı alır
        ↓
12. "Bu olayı daha önce işledim mi?" kontrolü    (idempotency)
        ↓  hayır
13. Feign ile Employee Service'e sorar: 42 numaralı personelin detayları?
        ↓
14. Mail şablonunu doldurur, MailHog'a gönderir
        ↓
15. Mail http://localhost:8025 adresinden görüntülenir
```

Bu akışta üç kritik tasarım kararı gizli: olayın iş verisiyle aynı transaction'a
yazılması, kullanıcının maili beklememesi, ve aynı mesajın iki kez gelebilmesi.
Gerekçeleri proje kurallarında kayıtlıdır.

---

## 4. Teknoloji Yığını

### Backend

| Teknoloji              | Ne için kullanılıyor                                    | Durum      |
| ---------------------- | ------------------------------------------------------- | ---------- |
| Java 17                | Hedef dil sürümü (derleme JDK 21 ile yapılabilir)       | ✅         |
| Spring Boot 3.2.5      | Uygulama iskeleti, gömülü sunucu, otomatik yapılandırma | ✅         |
| Spring Cloud 2023.0.1  | Eureka ve Feign'in geldiği sürüm ailesi                 | ✅         |
| Maven 3.9              | Bağımlılık yönetimi ve derleme                          | ✅         |
| Spring Data JPA        | Veritabanı erişimi, sorgu üretimi                       | ✅         |
| Flyway                 | Versiyonlu veritabanı şema yönetimi                     | ✅         |
| Spring Validation      | Girdi doğrulama (sınırda)                               | ✅         |
| Spring AOP             | Kesişen ilgiler: metot süre ölçümü                      | ✅         |
| slf4j                  | Loglama arayüzü, korelasyon kimliği (MDC)               | ✅         |
| springdoc-openapi      | Swagger arayüzü, API dokümantasyonu                     | ✅         |
| Actuator               | Sağlık ucu (liveness / readiness)                       | ✅         |
| Eureka                 | Servis keşfi                                            | ✅         |
| RabbitMQ (Spring AMQP) | Servisler arası asenkron mesajlaşma                     | ✅ altyapı |
| Spring Security        | Kimlik doğrulama (JWT) ve rol bazlı yetkilendirme       | ✅         |
| jjwt                   | JWT üretme ve doğrulama                                 | ✅         |
| OpenFeign              | Servisler arası deklaratif HTTP çağrısı                 | ✅         |

### Frontend

| Teknoloji          | Ne için kullanılıyor                                           | Durum |
| ------------------ | -------------------------------------------------------------- | ----- |
| React + TypeScript | Arayüz ve tip güvenliği                                        | ✅    |
| MUI                | Hazır bileşenler; özel tema, sayfalı ve sıralanabilir tablo    | ✅    |
| Redux Toolkit      | Sunucu verisi durumu (liste, arama, filtre, sıralama, hata)    | ✅    |
| Context API        | Oturum, rol ve tema (seyrek değişen, her yerden okunan veri)   | ✅    |
| Axios              | HTTP istemcisi; interceptor ile merkezi token ve hata yönetimi | ✅    |

### Altyapı

| Teknoloji      | Ne için kullanılıyor                             | Durum |
| -------------- | ------------------------------------------------ | ----- |
| PostgreSQL 16  | Personel verisi                                  | ✅    |
| MailHog        | Geliştirme ortamı sahte SMTP sunucusu            | ✅    |
| Docker Compose | Altyapı servislerinin tek komutla ayağa kalkması | ✅    |

---

## 5. Servisler ve Portlar

| Servis               | Port  | Adres                                        | Durum    |
| -------------------- | ----- | -------------------------------------------- | -------- |
| Eureka Server        | 8761  | http://localhost:8761                        | ✅       |
| Employee Service     | 8080  | http://localhost:8080                        | ✅       |
| Notification Service | 8081  | http://localhost:8081                        | ✅       |
| Frontend (React)     | 5173  | http://localhost:5173                        | ✅       |
| PostgreSQL           | 5432  | `employee_db` ve `notification_db`           | ✅       |
| RabbitMQ (AMQP)      | 5672  | uygulamaların bağlandığı port                | ✅       |
| RabbitMQ paneli      | 15672 | http://localhost:15672                       | ✅       |
| MailHog (SMTP)       | 1025  | Notification Service buraya mail atar        | ✅       |
| MailHog paneli       | 8025  | http://localhost:8025                        | ✅       |

Kimlik bilgileri depoda yazmaz; hepsi `.env` dosyasından gelir (bkz. bölüm 6).

**Portlar nerede tanımlı?**

- Konteyner servisleri → [docker-compose.yml](docker-compose.yml), her servisin `ports` bloğu. Yazım biçimi `"host portu : konteyner portu"` şeklindedir; sol taraf değiştirilebilir, sağ taraf konteyner içindeki programın dinlediği sabit porttur.
- Spring Boot uygulamaları → ilgili servisin `src/main/resources/application.yml` dosyasındaki `server.port` anahtarı. Örnek: [eureka-server/src/main/resources/application.yml](eureka-server/src/main/resources/application.yml).

---

## 6. Kurulum ve Çalıştırma

### Gereksinimler

| Araç           | Sürüm                                       |
| -------------- | ------------------------------------------- |
| JDK            | 17 veya üzeri (Java 17 bytecode hedeflenir) |
| Maven          | 3.9+                                        |
| Docker Desktop | Çalışır durumda                             |
| Node.js        | 22+ (yalnızca frontend için)         |

### 1. Ortam değişkenlerini hazırla ✅

Kimlik bilgilerinin depoda karşılığı yoktur ve varsayılanları da yoktur; tanımsız
bırakılırsa uygulama açılmaz. Şablonu kopyalayıp doldur:

```bash
cp .env.example .env
```

| Değişken | Ne için | Zorunlu mu |
|---|---|---|
| `POSTGRES_DB` | Veritabanı adı | Hayır, varsayılan `employee_db` |
| `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL kimlik bilgileri | Evet |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | RabbitMQ kimlik bilgileri | Evet |
| `JWT_SECRET` | Token imzalama anahtarı, **base64**, en az 32 bayt | Evet |
| `SERVICE_ACCOUNT_EMAIL` / `SERVICE_ACCOUNT_PASSWORD` | Notification Service'in Employee Service'i çağırırken kullandığı hesap | Evet |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | İlk yönetici hesabı | Hayır, verilmezse hesap oluşturulmaz |

Zorunlu bir değişken eksikse `docker compose` hiçbir konteyneri başlatmaz ve
hangisinin eksik olduğunu söyler — sistem yarım çalışmak yerine hiç başlamaz.

Değerleri sen seçersin; konteynerler ilk açılışta bu değerlerle kurulur.
`JWT_SECRET` üretmek için:

```bash
# Linux / macOS
openssl rand -base64 48
```

```powershell
# Windows PowerShell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 }))
```

> `ADMIN_PASSWORD` yalnızca hesap **ilk kez** oluşturulurken kullanılır. Hesap
> zaten varsa değer yok sayılır; parola değiştirmek için hesabı silmek gerekir.

### 2. Altyapıyı başlat ✅

```bash
docker compose up -d
```

Bu komut üç konteyner başlatır: RabbitMQ, PostgreSQL, MailHog. `.env` dosyasını
Docker Compose kendiliğinden okur. Uygulama servislerini **başlatmaz** — onlar
konteynerde değil, doğrudan makinede çalışır.

Doğrulama:

```bash
docker compose ps
```

Üçü de `running` durumunda görünmelidir.

### 3. Eureka Server'ı başlat ✅

```bash
cd eureka-server
mvn spring-boot:run
```

Açılması yaklaşık 15–20 saniye sürer. Ardından http://localhost:8761 adresinde panel açılmalıdır.

### 4. Employee Service'i başlat ✅

Maven, Docker Compose'un aksine `.env` dosyasını kendiliğinden okumaz; değerlerin
kabuğa yüklenmesi gerekir.

```bash
# Linux / macOS
set -a && source .env && set +a
cd employee-service && mvn spring-boot:run
```

```powershell
# Windows PowerShell
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $name, $value = $_.Split('=', 2)
    [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
}
cd employee-service; mvn spring-boot:run
```

Açılışta Flyway şemayı oluşturur, yönetici hesabı yoksa oluşturulur ve servis
Eureka'ya kaydolur. Doğrulama:

```bash
curl http://localhost:8080/actuator/health     # {"status":"UP"}
```

Swagger arayüzü: http://localhost:8080/swagger-ui.html

### Testleri çalıştırma

```bash
cd employee-service
mvn test
```

Veri katmanı testleri `docker compose` ile ayağa kalkan PostgreSQL'i kullanır,
dolayısıyla hem altyapının çalışıyor hem de ortam değişkenlerinin yukarıdaki gibi
kabuğa yüklenmiş olması gerekir. Testler transaction içinde çalışıp geri alındığı
için geliştirme veritabanını kirletmez.

### 5. Notification Service'i başlat ✅

`mvn spring-boot:run` kabuğu **bloke eder**. Bu yüzden yeni bir kabuk aç, depo
kökünden başla ve `.env`'i **orada da yükle** — ortam değişkenleri kabuklar
arasında taşınmaz.

```bash
# yeni kabuk, depo kokunde
set -a && source .env && set +a
cd notification-service && mvn spring-boot:run
```

Servisin dışarıya açılan bir REST API'si yoktur; yalnızca kuyruğu dinler.
Doğrulama:

```bash
curl http://localhost:8081/actuator/health     # {"status":"UP"}
```

Bir personel oluşturup http://localhost:8025 adresinde mailin geldiğini görebilirsin.
Personelin yöneticisi varsa mail ona da CC'lenir.

> `notification_db` veritabanı, PostgreSQL konteyneri **ilk kez** kurulurken
> [docker/postgres-init/](docker/postgres-init/) altındaki betikle oluşturulur.
> Konteyner daha önce kurulmuşsa bir kez elle oluşturmak gerekir:
>
> ```bash
> docker exec -e PGPASSWORD=$DB_PASSWORD hr-postgres \
>   psql -U $DB_USERNAME -d $POSTGRES_DB -c "CREATE DATABASE notification_db;"
> ```

### 6. Frontend'i başlat ✅

```bash
cd frontend
npm install
npm run dev
```

http://localhost:5173 adresinde açılır. Giriş için `.env` dosyasındaki
`ADMIN_EMAIL` / `ADMIN_PASSWORD` değerleri kullanılır.

Arayüz Employee Service'e doğrudan gider; API adresi varsayılan olarak
`http://localhost:8080`'dir ve `VITE_API_URL` ortam değişkeniyle değiştirilebilir.
Backend'in bu kaynağa CORS izni vermesi gerekir (`CORS_ALLOWED_ORIGINS`).

| Rol | Görebildiği |
|---|---|
| `USER` | Personel listesi ve personel detay sayfası (salt okunur) |
| `ADMIN` | Bunlara ek olarak oluştur, güncelle, pasifleştir / yeniden aktifleştir |

Arayüzde bulunanlar: yan menülü uygulama kabuğu, açık/koyu tema (seçim
tarayıcıda saklanır, seçim yoksa işletim sisteminin tercihi izlenir), ada ve
e-postaya göre arama, aktif/pasif filtresi, sütun sıralama, yönetici sütunu,
astların listelendiği detay sayfası, ve yöneticiyi ID yerine adıyla seçtiren
arama kutusu.

Testler:

```bash
cd frontend
npm test
```

### Tek komutla tüm sistem ✅

Yukarıdaki adımlar geliştirme akışıdır: servisler makinede çalışır, kod
değiştiğinde yeniden başlatmak yeterlidir. Sistemin tamamını konteynerlerde
çalıştırmak için:

```bash
docker compose --profile full up -d --build
```

Bu komut altyapıya ek olarak Eureka, Employee Service, Notification Service ve
arayüzü de başlatır. Adresler aynıdır (`:5173`, `:8080`, `:8081`, `:8761`).

Kod değiştiğinde imajın yeniden üretilmesi gerekir (`--build`); bu yüzden
geliştirirken profilsiz kullanım daha hızlıdır.

### Uçtan uca testler ✅

Ayağa kalkmış sisteme dışarıdan istek atan kara kutu testleri:

```bash
cd e2e
mvn test
```

Zincirin tamamını doğrular: giriş → personel oluşturma → outbox → kuyruk →
tüketici → MailHog'a düşen mail. Ayrı bir projede durur ve servislerin kendi
test koşusuna karışmaz; sistem kapalıyken ne yapılması gerektiğini söyleyerek
başarısız olur.

### Durdurma

```bash
docker compose stop                    # konteynerleri durdurur, veriyi korur
docker compose down                    # konteynerleri siler, volume'daki veri korunur
docker compose --profile full down     # uygulama konteynerleri dahil hepsini durdurur
```

---

## 7. API Uçları

Taban adres: `http://localhost:8080`

| Metot | Uç | Açıklama | Yetki | Başarılı |
|---|---|---|---|---|
| `POST` | `/api/auth/login` | Token alma | herkese açık | `200` |
| `GET` | `/api/employees` | Sayfalı liste. `?page=0&size=20&sort=lastName,asc&search=liskov&active=true` | giriş yapmış | `200` |
| `GET` | `/api/employees/{id}` | Tek kayıt | giriş yapmış | `200` |
| `GET` | `/api/employees/{id}/direct-reports` | Doğrudan bağlı personel | giriş yapmış | `200` |
| `POST` | `/api/employees` | Yeni kayıt | `ADMIN` | `201` + `Location` |
| `PUT` | `/api/employees/{id}` | Güncelleme | `ADMIN` | `200` |
| `PUT` | `/api/employees/{id}/status` | Pasifleştirme / yeniden aktifleştirme (tekrarı etkisiz) | `ADMIN` | `200` |
| `GET` | `/api/employees/{id}/salary` | Maaş bilgisi | `ADMIN` | `200` |
| `PUT` | `/api/employees/{id}/salary` | Maaş güncelleme | `ADMIN` | `200` |
| `GET` | `/api/departments` | Aktif departmanlar, isme göre sıralı | giriş yapmış | `200` |

`search` ada, soyada ve e-postaya bakar; `active` verilmezse aktif/pasif ayrımı
yapılmaz. Sıralanabilir alanlar: `lastName`, `firstName`, `email`, `jobTitle`,
`hireDate`.

**Neden `DELETE` değil `PUT /{id}/status`?** Kayıt silinmiyor, durumu
değişiyor — ve `DELETE`'in geri dönüşü yoktur. Pasifleştirme tek yönlü bir
kapıydı; aynı uç iki yöne de çalışınca hem doğru fiil kullanılmış oluyor hem de
işlem geri alınabiliyor.

**Maaş neden ayrı uçta?** Genel personel cevabında dönseydi, `USER` rolündeki
istemciler — Notification Service dahil — maaşı görürdü. Genel güncellemede yer
alsaydı, maaşı okuyamayan bir istemci onu her kayıtta `null` gönderip silerdi.
Ayrı alt kaynak her iki sorunu da yapısal olarak ortadan kaldırır.

Departman listesi sayfasızdır: sayısı kurumsal olarak sınırlı bir referans
verisidir ve seçim kutusunu doldurmak için kullanılır. Büyüyebilen listelerde
(personel gibi) sayfalama zorunludur.

Yetki kuralı **okuma / yazma** ayrımına dayanır: okumak için giriş yapmış olmak
yeterlidir, veri değiştiren her uç `ADMIN` rolü ister. Kural yazılmamış bir uç
varsayılan olarak kimlik doğrulaması ister — açıkta kalmaz.

### Kimlik doğrulama

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"<parola>"}'
```

```json
{ "token": "eyJhbGciOiJIUzM4NCJ9...", "tokenType": "Bearer", "expiresInSeconds": 900 }
```

Sonraki isteklerde token `Authorization` header'ında taşınır:

```bash
curl http://localhost:8080/api/employees -H "Authorization: Bearer <token>"
```

Token 15 dakika geçerlidir (`JWT_VALIDITY_MINUTES` ile değiştirilebilir).
Kimlik doğrulanmamış istek `401`, yetkisi olmayan istek `403` döner.

### Örnek istek

```bash
curl -X POST http://localhost:8080/api/employees \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Ada",
    "lastName": "Lovelace",
    "email": "ada@example.com",
    "departmentId": 1,
    "jobTitle": "Software Engineer",
    "hireDate": "2024-01-15",
    "salary": 85000.00
  }'
```

```json
{
  "id": 100,
  "firstName": "Ada",
  "lastName": "Lovelace",
  "email": "ada@example.com",
  "phone": null,
  "departmentId": 1,
  "departmentName": "Software Development",
  "managerId": null,
  "managerFullName": null,
  "jobTitle": "Software Engineer",
  "hireDate": "2024-01-15",
  "active": true
}
```

### Hata cevapları

Hatalar RFC 7807 (`ProblemDetail`) biçiminde döner.

| Durum | Kod |
|---|---|
| Doğrulama hatası | `400` + alan bazlı `errors` listesi |
| Geçersiz yönetici ataması (döngü) | `400` |
| Pasif bir kişinin yönetici atanması | `400` |
| Bozuk JSON gövdesi | `400` |
| Geçersiz sayfalama/sıralama parametresi | `400` |
| Kimlik doğrulanmadı / geçersiz token | `401` |
| Yetki yok | `403` |
| Kayıt bulunamadı | `404` |
| Desteklenmeyen HTTP metodu | `405` |
| E-posta zaten kayıtlı | `409` |

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request contains invalid fields",
  "instance": "/api/employees",
  "errors": [
    { "field": "email", "message": "Email format is invalid" },
    { "field": "departmentId", "message": "Department is required" }
  ]
}
```

### Swagger

Etkileşimli API arayüzü: http://localhost:8080/swagger-ui.html
OpenAPI belgesi (JSON): http://localhost:8080/v3/api-docs

Belge controller ve DTO sınıflarından üretilir; elle güncellenmez.

---

## 8. Yol Haritası

| Faz   | İçerik                                                                                | Neden bu sırada                                      | Durum     |
| ----- | ------------------------------------------------------------------------------------- | ---------------------------------------------------- | --------- |
| **0** | Git deposu, `.gitignore`, altyapı, Eureka                                             | Kod yazmadan önce geri dönülebilir bir zemin gerekir | ✅        |
| **1** | Employee Service: JPA, Flyway, REST, validation, hata yönetimi, AOP, Swagger, testler | Diğer her şey bu servisin verisine ve API'sine bağlı | ✅        |
| **2** | Spring Security: JWT, rol bazlı yetkilendirme                                         | Korunacak uçlar önce var olmalı                      | ✅        |
| **3** | Olay yayını: transactional outbox, publisher confirms                                 | Yayınlanacak bir değişiklik önce var olmalı          | ✅        |
| **4** | Notification Service: tüketici, idempotency, DLQ, Feign, mail                         | Dinlenecek mesaj önce var olmalı                     | ✅        |
| **5** | React + TypeScript arayüz: giriş, sayfalı liste, form                                 | Çağrılacak API önce stabil olmalı                    | ✅        |
| **6** | Dockerfile'lar, tek komutla ayağa kalkan sistem, uçtan uca testler                    | Parçaların tamamı hazır olmalı                       | ✅        |

Genel kural: **veriyi üreten, tüketenden önce gelir.**

---

## 9. Proje Yapısı

```
HR Management System/
├── docker-compose.yml          ✅  altyapı + "full" profilinde tüm uygulamalar
├── .env.example                ✅  ortam değişkeni şablonu (.env buradan kopyalanır)
├── docker/postgres-init/       ✅  ilk kurulumda çalışan veritabanı betikleri
├── README.md                   ✅  bu dosya
├── eureka-server/              ✅  servis keşif sunucusu
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/proje/eureka/EurekaServerApplication.java
│       └── resources/application.yml
├── employee-service/           ✅  ana uygulama servisi
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/proje/employee/
│       │   ├── controller/     REST uçları
│       │   ├── service/        iş kuralları, transaction sınırı
│       │   ├── repository/     veritabanı erişimi
│       │   ├── entity/         tablo karşılıkları
│       │   ├── dto/            API sözleşmesi
│       │   ├── mapper/         entity ↔ dto çevirisi
│       │   ├── exception/      hata sınıfları + merkezî yakalayıcı
│       │   ├── event/          olay sözleşmesi, outbox yazıcı ve relay
│       │   └── config/         güvenlik, JWT, aspect, correlation ID filtresi
│       ├── main/resources/db/migration/   V1__ V2__ V3__ V4__
│       └── test/               88 test
├── notification-service/       ✅  olayları dinleyip mail gönderen servis
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/proje/notification/
│       │   ├── listener/       kuyruk dinleyicisi, idempotency kontrolü
│       │   ├── service/        mail hazırlama, yönetici sorgulama
│       │   ├── client/         Feign istemcileri ve servis token'ı
│       │   ├── entity/         işlenen olay kaydı
│       │   ├── repository/     veritabanı erişimi
│       │   ├── event/          olay sözleşmesinin tüketici tarafı
│       │   └── config/         kuyruk, DLX ve DLQ tanımları
│       ├── main/resources/db/migration/   V1__
│       └── test/               23 test
└── frontend/                   ✅  React + TypeScript arayüz
    ├── package.json
    └── src/
        ├── api/                Axios istemcisi ve interceptor'lar
        ├── auth/               Context API: oturum, korumalı rotalar
        ├── store/              Redux Toolkit: liste durumu, arama, filtre, sıralama
        ├── theme/              Tema tanımı ve açık/koyu tema seçimi
        ├── pages/              Giriş, liste, detay, form, 404 ekranları
        ├── components/         Kabuk, onay penceresi, geri bildirim, hata sınırı
        ├── types/              Backend sözleşmesinin TypeScript karşılığı
        └── *.test.ts(x)        67 test (Vitest + Testing Library)

e2e/                            ✅  çalışan sisteme dışarıdan bakan testler
└── src/test/java/com/proje/e2e/    8 test
```

Her Java servisinin **kendi `pom.xml`'i** vardır; ortak bir üst pom kullanılmaz. Mikroservislerin bağımsız derlenip bağımsız dağıtılabilmesi bu mimarinin amacıdır, ortak bir üst pom onları sürüm olarak birbirine bağlardı.

---

## 10. Sorun Giderme

### `port is already allocated` hatası

`docker compose up -d` şu hatayı verirse:

```
Error response from daemon: failed to set up container networking:
Bind for 127.0.0.1:5432 failed: port is already allocated
```

Bir başka program (çoğunlukla başka bir Docker projesi) aynı portu tutuyordur. Bir makinede iki program aynı portu dinleyemez.

Portu kimin tuttuğunu bulmak için:

```bash
docker ps --format "{{.Names}}\t{{.Ports}}"
```

İki çözüm vardır:

1. Çakışan konteyneri durdur: `docker stop <konteyner-adı>` — geri almak için `docker start <konteyner-adı>`.
2. Bu projenin host portunu değiştir: `docker-compose.yml`'de `"5432:5432"` yerine `"5433:5432"` yaz. Sol taraf host portudur ve serbestçe değiştirilebilir.

### Konteyner çalışıyor ama porta bağlanılamıyor

Konteyner `running` görünmesine rağmen `localhost:<port>` cevap vermiyorsa, port yönlendirmesi kurulmamış olabilir. Kontrol:

```bash
docker inspect <konteyner-adı> --format "{{json .NetworkSettings.Ports}}"
```

Çıktı `{"5432/tcp":[]}` gibi **boş** bir dizi içeriyorsa yönlendirme aktif değildir. `docker restart` bunu düzeltmez; konteyneri yeniden oluşturmak gerekir:

```bash
docker compose up -d --force-recreate postgres
```

Adlandırılmış volume kullanıldığı için veri kaybolmaz.
