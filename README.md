# Personel Yönetim Sistemi (Mikroservis)

> Personel kayıtlarını yöneten bir web uygulaması. Kayıt değiştiğinde bildirim maili, ana uygulamanın içinde değil, **ayrı bir servis** tarafından **mesaj kuyruğu** üzerinden gönderilir.

**Durum:** Employee Service çalışıyor — JWT ile korunan beş REST ucu, rol bazlı yetkilendirme, doğrulama, merkezî hata yönetimi, Swagger ve sağlık ucu hazır (45 test). Notification Service ve arayüz henüz yazılmadı. Bölümlerdeki ✅ / 🚧 işaretleri neyin hazır olduğunu gösterir.

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
│  TARAYICI                                                🚧      │
│  React + TypeScript + MUI          :5173                         │
│  Personel listesi ve kayıt formu ekranları                       │
└───────────────────────┬──────────────────────────────────────────┘
                        │  HTTP (JSON) + JWT
                        ▼
┌──────────────────────────────────────────────────────────────────┐
│  EMPLOYEE SERVICE (Spring Boot)      :8080               ✅      │
│  • REST uçları (listele, ekle, güncelle, pasifleştir)            │
│  • JWT ile kimlik doğrulama, rol bazlı yetkilendirme             │
│  • Doğrulama, iş kuralları, merkezî hata yönetimi                │
│  • Verinin tek gerçek kaynağı (source of truth)                  │
└──────┬────────────────────────────────────┬──────────────────────┘
       │ SQL                                │ olay mesajı (fire-and-forget)
       ▼                                    ▼
┌──────────────┐                    ┌──────────────────┐
│ PostgreSQL   │  ✅                │   RabbitMQ       │  ✅  :5672
│   :5432      │                    │  (mesaj kuyruğu) │
└──────────────┘                    └────────┬─────────┘
                                             │ mesajı tüketir
                                             ▼
                        ┌────────────────────────────────────────┐
                        │  NOTIFICATION SERVICE   :8081    🚧    │
                        │  • Kuyruktan olayı okur                │
                        │  • Feign ile Employee'den detay çeker ─┼──► :8080
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
| **React Frontend**       | Kullanıcının gördüğü arayüz                                 | 🚧 Faz 5 |
| **Employee Service**     | Verinin sahibi; tüm iş kurallarının uygulandığı yer         | ✅       |
| **PostgreSQL**           | Personel verisinin kalıcı olarak saklandığı yer             | ✅       |
| **RabbitMQ**             | İki servis arasında mesaj taşıyan aracı                     | ✅       |
| **Notification Service** | Olayı dinleyip mail hazırlayan ve gönderen servis           | 🚧 Faz 4 |
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
 6. PostgreSQL'e UPDATE atılır → transaction COMMIT olur   ← veri artık kalıcı
        ↓
 7. ANCAK ŞİMDİ: RabbitMQ'ya "employee.updated" olayı bırakılır
        ↓
 8. Employee Service kullanıcıya 200 OK döner   ← kullanıcı mail için beklemez
        ↓  (paralel, milisaniyeler sonra)
 9. Notification Service kuyruktan mesajı alır
        ↓
10. "Bu olayı daha önce işledim mi?" kontrolü    (idempotency)
        ↓  hayır
11. Feign ile Employee Service'e sorar: 42 numaralı personelin detayları?
        ↓
12. Mail şablonunu doldurur, MailHog'a gönderir
        ↓
13. Mail http://localhost:8025 adresinden görüntülenir
```

Bu akışta üç kritik tasarım kararı gizli: mesajın commit'ten *sonra* yayınlanması,
kullanıcının maili beklememesi, ve aynı mesajın iki kez gelebilmesi. Gerekçeleri
proje kurallarında kayıtlıdır.

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
| OpenFeign              | Servisler arası deklaratif HTTP çağrısı                 | 🚧 Faz 4   |

### Frontend

| Teknoloji          | Ne için kullanılıyor                                           | Durum |
| ------------------ | -------------------------------------------------------------- | ----- |
| React + TypeScript | Arayüz ve tip güvenliği                                        | 🚧    |
| MUI                | Hazır bileşenler, DataGrid ile sunucu taraflı liste            | 🚧    |
| Redux Toolkit      | Sunucu verisi durumu (liste, filtre, yükleniyor, hata)         | 🚧    |
| Context API        | Uygulama geneli durum (tema, oturum)                           | 🚧    |
| Axios              | HTTP istemcisi; interceptor ile merkezi token ve hata yönetimi | 🚧    |

### Altyapı

| Teknoloji      | Ne için kullanılıyor                             | Durum |
| -------------- | ------------------------------------------------ | ----- |
| PostgreSQL 16  | Personel verisi                                  | ✅    |
| MailHog        | Geliştirme ortamı sahte SMTP sunucusu            | ✅    |
| Docker Compose | Altyapı servislerinin tek komutla ayağa kalkması | ✅    |

---

## 5. Servisler ve Portlar

| Servis               | Port  | Adres                                      | Durum    |
| -------------------- | ----- | ------------------------------------------ | -------- |
| Eureka Server        | 8761  | http://localhost:8761                      | ✅       |
| Employee Service     | 8080  | http://localhost:8080                      | ✅       |
| Notification Service | 8081  | http://localhost:8081                      | 🚧 Faz 4 |
| Frontend (React)     | 5173  | http://localhost:5173                      | 🚧 Faz 5 |
| PostgreSQL           | 5432  | `employee_db` / `employee` / `employee123` | ✅       |
| RabbitMQ (AMQP)      | 5672  | uygulamaların bağlandığı port              | ✅       |
| RabbitMQ paneli      | 15672 | http://localhost:15672 — `guest` / `guest` | ✅       |
| MailHog (SMTP)       | 1025  | Notification Service buraya mail atar      | ✅       |
| MailHog paneli       | 8025  | http://localhost:8025                      | ✅       |

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
| Node.js        | 22+ (yalnızca frontend için, Faz 5)         |

### 1. Altyapıyı başlat ✅

```bash
docker compose up -d
```

Bu komut üç konteyner başlatır: RabbitMQ, PostgreSQL, MailHog. Uygulama servislerini **başlatmaz** — onlar konteynerde değil, doğrudan makinede çalışır.

Doğrulama:

```bash
docker compose ps
```

Üçü de `running` durumunda görünmelidir.

### 2. Eureka Server'ı başlat ✅

```bash
cd eureka-server
mvn spring-boot:run
```

Açılması yaklaşık 15–20 saniye sürer. Ardından http://localhost:8761 adresinde panel açılmalıdır.

### 3. Employee Service'i başlat ✅

Servis üç ortam değişkeni bekler. Hiçbirinin varsayılanı yoktur — sırlar depoya
girmediği için elle verilmeleri gerekir.

| Değişken | Ne için | Zorunlu mu |
|---|---|---|
| `JWT_SECRET` | Token imzalama anahtarı, **base64**, en az 32 bayt | Evet, yoksa uygulama açılmaz |
| `ADMIN_EMAIL` | İlk yönetici hesabının e-postası | Hayır, verilmezse hesap oluşturulmaz |
| `ADMIN_PASSWORD` | İlk yönetici hesabının parolası | Hayır |

Anahtar üretmek için:

```bash
# Linux / macOS
openssl rand -base64 48
```

```powershell
# Windows PowerShell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 }))
```

Çalıştırma:

```bash
cd employee-service
JWT_SECRET="<uretilen-anahtar>" ADMIN_EMAIL="admin@example.com" ADMIN_PASSWORD="<parola>" mvn spring-boot:run
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

Veri katmanı testleri `docker compose` ile ayağa kalkan PostgreSQL'i kullanır, dolayısıyla altyapının çalışıyor olması gerekir. Testler transaction içinde çalışıp geri alındığı için geliştirme veritabanını kirletmez.

### 4. Notification Service'i başlat 🚧

Faz 4 tamamlandığında bu bölüm doldurulacak.

### 5. Frontend'i başlat 🚧

Faz 5 tamamlandığında bu bölüm doldurulacak.

### Durdurma

```bash
docker compose stop     # konteynerleri durdurur, veriyi korur
docker compose down     # konteynerleri siler, volume'daki veri yine korunur
```

---

## 7. API Uçları

Taban adres: `http://localhost:8080`

| Metot | Uç | Açıklama | Yetki | Başarılı |
|---|---|---|---|---|
| `POST` | `/api/auth/login` | Token alma | herkese açık | `200` |
| `GET` | `/api/employees` | Sayfalı liste. `?page=0&size=20&sort=lastName,asc` | giriş yapmış | `200` |
| `GET` | `/api/employees/{id}` | Tek kayıt | giriş yapmış | `200` |
| `POST` | `/api/employees` | Yeni kayıt | `ADMIN` | `201` + `Location` |
| `PUT` | `/api/employees/{id}` | Güncelleme | `ADMIN` | `200` |
| `DELETE` | `/api/employees/{id}` | Pasifleştirme (kayıt silinmez) | `ADMIN` | `204` |

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
| Kimlik doğrulanmadı / geçersiz token | `401` |
| Yetki yok | `403` |
| Kayıt bulunamadı | `404` |
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
| **3** | RabbitMQ olay yayını (üretici taraf)                                                  | Yayınlanacak bir değişiklik önce var olmalı          | 🚧        |
| **4** | Notification Service: tüketici, idempotency, Feign, mail                              | Dinlenecek mesaj önce var olmalı                     | 🚧        |
| **5** | React + TypeScript arayüz                                                             | Çağrılacak API önce stabil olmalı                    | 🚧        |
| **6** | Uçtan uca test, Dockerfile'lar, dokümantasyon                                         | Parçaların tamamı hazır olmalı                       | 🚧        |

Genel kural: **veriyi üreten, tüketenden önce gelir.**

---

## 9. Proje Yapısı

```
HR Management System/
├── docker-compose.yml          ✅  altyapı tanımı (RabbitMQ, PostgreSQL, MailHog)
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
│       │   └── config/         güvenlik, JWT, aspect, correlation ID filtresi
│       ├── main/resources/db/migration/   V1__ V2__ V3__
│       └── test/               45 test
├── notification-service/       🚧  Faz 4
└── frontend/                   🚧  Faz 5
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
