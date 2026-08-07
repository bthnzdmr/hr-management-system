# Personel Yönetim Sistemi (Mikroservis)

> Personel kayıtlarını yöneten bir web uygulaması. Kayıt değiştiğinde bildirim maili, ana uygulamanın içinde değil, **ayrı bir servis** tarafından **mesaj kuyruğu** üzerinden gönderilir.

**Durum:** Geliştirme aşamasında. Altyapı ve servis keşif sunucusu çalışıyor; uygulama servisleri henüz yazılmadı. Bölümlerdeki ✅ / 🚧 işaretleri neyin hazır olduğunu gösterir.

---

## İçindekiler

1. [Proje Hakkında](#1-proje-hakkında)
2. [Mimari](#2-mimari)
3. [Uçtan Uca Akış](#3-uçtan-uca-akış)
4. [Tasarım Kararları ve Gerekçeleri](#4-tasarım-kararları-ve-gerekçeleri)
5. [Teknoloji Yığını](#5-teknoloji-yığını)
6. [Servisler ve Portlar](#6-servisler-ve-portlar)
7. [Kurulum ve Çalıştırma](#7-kurulum-ve-çalıştırma)
8. [API Uçları](#8-api-uçları)
9. [Yol Haritası](#9-yol-haritası)
10. [Proje Yapısı](#10-proje-yapısı)
11. [Sorun Giderme](#11-sorun-giderme)

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
│  PERSONEL SERVICE (Spring Boot)      :8080               🚧      │
│  • REST uçları (listele, ekle, güncelle, sil)                    │
│  • Doğrulama, yetkilendirme, iş kuralları                        │
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
                        │  • Feign ile Personel'den detay çeker ─┼──► :8080
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

| Parça | Görevi | Durum |
|---|---|---|
| **React Frontend** | Kullanıcının gördüğü arayüz | 🚧 Faz 5 |
| **Personel Service** | Verinin sahibi; tüm iş kurallarının uygulandığı yer | 🚧 Faz 1 |
| **PostgreSQL** | Personel verisinin kalıcı olarak saklandığı yer | ✅ |
| **RabbitMQ** | İki servis arasında mesaj taşıyan aracı | ✅ |
| **Notification Service** | Olayı dinleyip mail hazırlayan ve gönderen servis | 🚧 Faz 4 |
| **MailHog** | Gerçek SMTP sunucusu yerine geliştirme ortamı sahtesi | ✅ |
| **Eureka Server** | Servislerin birbirini IP yerine **isimle** bulmasını sağlar | ✅ |

---

## 3. Uçtan Uca Akış

Bir personelin departmanının değiştirildiği senaryo, baştan sona:

```
 1. Kullanıcı formda değişikliği yapar, Kaydet'e basar
        ↓
 2. React → Axios → PUT /api/personel/42        (header'da JWT)
        ↓
 3. Spring Security filtresi: token geçerli mi? Bu kullanıcı yazma yetkisine sahip mi?
        ↓  evet
 4. Controller: gelen JSON geçerli mi?          (@Valid)
        ↓  evet
 5. Service: iş kuralları çalışır                (@Transactional başlar)
        ↓
 6. PostgreSQL'e UPDATE atılır → transaction COMMIT olur   ← veri artık kalıcı
        ↓
 7. ANCAK ŞİMDİ: RabbitMQ'ya "personel.updated" olayı bırakılır
        ↓
 8. Personel Service kullanıcıya 200 OK döner   ← kullanıcı mail için beklemez
        ↓  (paralel, milisaniyeler sonra)
 9. Notification Service kuyruktan mesajı alır
        ↓
10. "Bu olayı daha önce işledim mi?" kontrolü    (idempotency)
        ↓  hayır
11. Feign ile Personel Service'e sorar: 42 numaralı personelin detayları?
        ↓
12. Mail şablonunu doldurur, MailHog'a gönderir
        ↓
13. Mail http://localhost:8025 adresinden görüntülenir
```

Bu akışta üç kritik tasarım kararı gizli:

- **6. ve 7. adımın sırası** — mesaj neden commit'ten *sonra*?
- **8. adımın 9'dan önce olması** — kullanıcı neden maili beklemiyor?
- **10. adımdaki kontrol** — aynı mesaj neden iki kez gelebilir?

Gerekçeleri bir sonraki bölümde.

---

## 4. Tasarım Kararları ve Gerekçeleri

> 🖊 **Bu bölüm proje sahibi tarafından, ilgili faz tamamlandıkça yazılmaktadır.** Amaç, her kararın gerekçesini karar tazeyken kendi cümlelerle kayıt altına almak.

### 4.1 Neden mikroservis? Ne zaman kullanılmaz?

Bu sistemde iki iş var ve önem düzeyleri farklı: personel kaydını güncellemek **kritik**, bildirim maili göndermek **ikincil**. İkisi aynı programda olsaydı, kritik iş ikincil işin arızasından etkilenirdi.

Somut olarak: mail gönderimi `@Transactional` bir metodun içinde yapılsaydı ve mail sunucusu çökseydi, fırlayan `RuntimeException` transaction'ı geri alır ve personel güncellemesi de iptal olurdu. Yani *mail sunucusu kapalı olduğu için departman değiştirilemezdi*. Hatayı yakalayıp yutsaydık bu sefer veri kaydolur ama kullanıcı hata mesajı görür, işlemin başarısız olduğunu sanıp tekrar dener ve mükerrer kayıt oluşurdu. İki seçenek de kabul edilemez.

Buradaki hastalığın adı **bağlılık (coupling)**: bir servisin çalışabilirliğinin, ilgisiz bir bileşenin çalışabilirliğine bağlanması. Kuyruk bu bağı koparır — Personel Service mail sunucusunu hiç tanımaz, mesajı kuyruğa bırakıp işini bitirir. Notification Service çökse bile mesaj kuyrukta bekler, servis ayağa kalkınca işlenir.

**Ne zaman kullanılmaz:** Küçük ölçekli projelerde, tek kişilik veya küçük ekiplerde, servis sınırlarının henüz netleşmediği erken aşamalarda, bağımsız ölçekleme ihtiyacı yokken ve log/trace altyapısı kurulmamışken mikroservis net bir kayıptır. Sınırları yanlış çizmenin maliyeti de asimetriktir: monolitte iki sınıfın yerini değiştirmek bir IDE işlemidir, iki servis arasında aynı iş API sürümleme, veri taşıma ve iki ayrı dağıtım gerektirir. Bu yüzden makul varsayılan "önce monolit"tir — bir monoliti sonradan bölmek, yanlış bölünmüş servisleri birleştirmekten çok daha kolaydır.

### 4.2 Neden mesaj kuyruğu, senkron REST çağrısı yerine?

İki ayrı servis olması tek başına kuyruğu gerektirmez — Personel Service, Notification Service'i doğrudan HTTP ile de çağırabilirdi. Kuyruk üç sebeple tercih edildi.

**1. Erişilebilirlik bağlılığı.** Senkron çağrıda Notification Service çökmüşse istek başarısız olur ve `@Transactional` metottan yayılan hata personel güncellemesini de geri alır. Ayrıca tersi durum daha da kötüdür: HTTP çağrısı başarılı olup sonraki bir adım patlarsa veritabanı geri alınır ama **gönderilmiş mail geri alınamaz**. Ağ üzerinden yapılmış bir işlemin geri alınamaması, dağıtık transaction probleminin özüdür.

**2. Gecikme.** Senkron çağrıda cevap gelmeden sıradaki satır çalışmaz; kullanıcı bütün zincirin bitmesini bekler. Kuyruğa mesaj bırakmak birkaç milisaniye sürer, mail gönderimi ise saniyeler alabilir.

**3. Tüketici bağımsızlığı — asıl sebep budur.** Senkron REST'te Personel Service, mesajı **kime** gönderdiğini bilmek zorundadır. Yarın SMS ve denetim kaydı servisleri eklenirse Personel Service'in koduna üç ayrı istemci, üç adres, üç timeout ve üç hata yönetimi girer; her yeni tüketici için bu servis değiştirilip yeniden dağıtılır. Kuyrukta ise Personel Service sadece **ne olduğunu** duyurur; yeni tüketiciler kuyruğa kendileri abone olur ve üretici tarafta tek satır değişmez.

Bu ayrım mesajın adında da görünür: olay `personel.updated` diye adlandırılmıştır, `mailGonder` diye değil. Bir **olgu** bildirilir, bir **emir** verilmez — komut alıcısını bilmeyi gerektirir, olay gerektirmez. Desenin adı **yayınla/abone ol (publish/subscribe)**.

**Bedeli:** Kuyruk bedava değildir. Sistem **nihai tutarlılığa (eventual consistency)** geçer — veri güncellendiği an mail henüz gitmemiştir. Ayrıca mesaj tekrar teslim edilebilir (bkz. 4.5), sıralama garantisi sınırlıdır ve hata ayıklamak tek bir çağrı yığınını okumaktan zordur.

### 4.3 Mesaj neden transaction commit olduktan *sonra* yayınlanıyor?

<!-- 🚧 Faz 3 sonunda yazılacak -->

### 4.4 Neden Flyway, `ddl-auto: update` yerine?

<!-- 🚧 Faz 1 sonunda yazılacak -->

### 4.5 Neden idempotency kontrolü gerekli?

<!-- 🚧 Faz 4 sonunda yazılacak -->

### 4.6 Neden Eureka, servis adresini koda gömmek yerine?

Sabit adres yazmanın üç sorunu var.

**1. Adres ortama göre değişir.** `http://localhost:8080` yalnızca geliştirme makinesinde geçerlidir; gerçek bir sunucuda veya konteyner ağında anlamını yitirir. Yapılandırmayı her ortam için ayrı tutmak gerekir.

**2. Yük dengelemesi mümkün olmaz.** Sabit adres tek bir hedef demektir. Personel Service'in üç kopyası çalışsa bile, adresi koduna gömen istemci her isteği aynı kopyaya gönderir; diğer ikisi hiç iş almaz. Yük dağıtımı için birinin **tüm kopyaların listesini** tutması gerekir — Eureka bu listeyi tutar, Feign de `lb://` önekiyle listeden seçim yapar.

**3. Değişiklik yeniden başlatma gerektirir.** Adres değiştiğinde ilgili `application.yml` düzenlenir ve servis yeniden başlatılır; bu sırada servis kapalıdır. Aynı adresi kullanan servis sayısı kadar bu işlem tekrarlanır. Eureka'da ise kayıt ve silme işini servislerin kendisi yapar: yeni kopya açılınca kendini kaydeder, çöken kopyanın kalp atışı (heartbeat) kesilince listeden düşer. Hiçbir dosya düzenlenmez.

Bu, 4.2'deki fikrin kardeşidir: yayınla/abone ol deseni "kime gönderdiğini bilme" zorunluluğunu kaldırır, servis keşfi ise "nerede olduğunu bilme" zorunluluğunu.

**Bu projedeki dürüst değerlendirme:** İki servis ve tek makine için Eureka gerekli değildir; yapılandırmaya sabit bir adres yazmak yeterdi. Burada öğrenme amacıyla kullanılmaktadır. Gerçekten gerekli hale geldiği koşullar: kopya sayısının dinamik olması, adreslerin önceden bilinmemesi (bulut ortamları, Kubernetes) ve servis sayısının elle takip edilemeyecek kadar artması.

### 4.7 Neden hem Redux hem Context API?

<!-- 🚧 Faz 5 sonunda yazılacak -->

---

## 5. Teknoloji Yığını

### Backend

| Teknoloji | Ne için kullanılıyor | Durum |
|---|---|---|
| Java 17 | Hedef dil sürümü (derleme JDK 21 ile yapılabilir) | ✅ |
| Spring Boot 3.2.5 | Uygulama iskeleti, gömülü sunucu, otomatik yapılandırma | ✅ |
| Spring Cloud 2023.0.1 | Eureka ve Feign'in geldiği sürüm ailesi | ✅ |
| Maven 3.9 | Bağımlılık yönetimi ve derleme | ✅ |
| Spring Data JPA | Veritabanı erişimi, sorgu üretimi | 🚧 |
| Flyway | Versiyonlu veritabanı şema yönetimi | 🚧 |
| Spring Validation | Girdi doğrulama (sınırda) | 🚧 |
| Spring Security | Kimlik doğrulama (JWT) ve yetkilendirme | 🚧 |
| Spring AOP | Kesişen ilgiler: loglama, süre ölçümü | 🚧 |
| slf4j | Loglama arayüzü, korelasyon kimliği (MDC) | 🚧 |
| RabbitMQ (Spring AMQP) | Servisler arası asenkron mesajlaşma | ✅ altyapı |
| Eureka | Servis keşfi | ✅ |
| OpenFeign | Servisler arası deklaratif HTTP çağrısı | 🚧 |
| springdoc-openapi | Swagger arayüzü, API dokümantasyonu | 🚧 |

### Frontend

| Teknoloji | Ne için kullanılıyor | Durum |
|---|---|---|
| React + TypeScript | Arayüz ve tip güvenliği | 🚧 |
| MUI | Hazır bileşenler, DataGrid ile sunucu taraflı liste | 🚧 |
| Redux Toolkit | Sunucu verisi durumu (liste, filtre, yükleniyor, hata) | 🚧 |
| Context API | Uygulama geneli durum (tema, oturum) | 🚧 |
| Axios | HTTP istemcisi; interceptor ile merkezi token ve hata yönetimi | 🚧 |

### Altyapı

| Teknoloji | Ne için kullanılıyor | Durum |
|---|---|---|
| PostgreSQL 16 | Personel verisi | ✅ |
| MailHog | Geliştirme ortamı sahte SMTP sunucusu | ✅ |
| Docker Compose | Altyapı servislerinin tek komutla ayağa kalkması | ✅ |

---

## 6. Servisler ve Portlar

| Servis | Port | Adres | Durum |
|---|---|---|---|
| Eureka Server | 8761 | http://localhost:8761 | ✅ |
| Personel Service | 8080 | http://localhost:8080 | 🚧 Faz 1 |
| Notification Service | 8081 | http://localhost:8081 | 🚧 Faz 4 |
| Frontend (React) | 5173 | http://localhost:5173 | 🚧 Faz 5 |
| PostgreSQL | 5432 | `personel_db` / `personel` / `personel123` | ✅ |
| RabbitMQ (AMQP) | 5672 | uygulamaların bağlandığı port | ✅ |
| RabbitMQ paneli | 15672 | http://localhost:15672 — `guest` / `guest` | ✅ |
| MailHog (SMTP) | 1025 | Notification Service buraya mail atar | ✅ |
| MailHog paneli | 8025 | http://localhost:8025 | ✅ |

**Portlar nerede tanımlı?**

- Konteyner servisleri → [docker-compose.yml](docker-compose.yml), her servisin `ports` bloğu. Yazım biçimi `"host portu : konteyner portu"` şeklindedir; sol taraf değiştirilebilir, sağ taraf konteyner içindeki programın dinlediği sabit porttur.
- Spring Boot uygulamaları → ilgili servisin `src/main/resources/application.yml` dosyasındaki `server.port` anahtarı. Örnek: [eureka-server/src/main/resources/application.yml](eureka-server/src/main/resources/application.yml).

---

## 7. Kurulum ve Çalıştırma

### Gereksinimler

| Araç | Sürüm |
|---|---|
| JDK | 17 veya üzeri (Java 17 bytecode hedeflenir) |
| Maven | 3.9+ |
| Docker Desktop | Çalışır durumda |
| Node.js | 22+ (yalnızca frontend için, Faz 5) |

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

Açılması yaklaşık 15–20 saniye sürer. Ardından http://localhost:8761 adresinde panel açılmalı, "Instances currently registered with Eureka" listesi henüz boş olmalıdır — kaydolacak servis yazılmadı.

### 3. Personel Service'i başlat 🚧

Faz 1 tamamlandığında bu bölüm doldurulacak.

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

## 8. API Uçları

🚧 Henüz API yok. Faz 1 tamamlandığında bu bölüm doldurulacak ve Swagger arayüzü http://localhost:8080/swagger-ui.html adresinde yayınlanacak.

---

## 9. Yol Haritası

| Faz | İçerik | Neden bu sırada | Durum |
|---|---|---|---|
| **0** | Git deposu, `.gitignore`, altyapı, Eureka | Kod yazmadan önce geri dönülebilir bir zemin gerekir | 🔄 kısmen |
| **1** | Personel Service: JPA, Flyway, REST, validation, hata yönetimi, AOP, Swagger, testler | Diğer her şey bu servisin verisine ve API'sine bağlı | 🚧 |
| **2** | Spring Security: JWT, rol bazlı yetkilendirme | Korunacak uçlar önce var olmalı | 🚧 |
| **3** | RabbitMQ olay yayını (üretici taraf) | Yayınlanacak bir değişiklik önce var olmalı | 🚧 |
| **4** | Notification Service: tüketici, idempotency, Feign, mail | Dinlenecek mesaj önce var olmalı | 🚧 |
| **5** | React + TypeScript arayüz | Çağrılacak API önce stabil olmalı | 🚧 |
| **6** | Uçtan uca test, Dockerfile'lar, dokümantasyon | Parçaların tamamı hazır olmalı | 🚧 |

Genel kural: **veriyi üreten, tüketenden önce gelir.**

---

## 10. Proje Yapısı

```
HR Management System/
├── docker-compose.yml          ✅  altyapı tanımı (RabbitMQ, PostgreSQL, MailHog)
├── README.md                   ✅  bu dosya
├── CLAUDE.md                   🚧  projeye özel geliştirme kuralları
├── eureka-server/              ✅  servis keşif sunucusu
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/proje/eureka/EurekaServerApplication.java
│       └── resources/application.yml
├── personel-service/           🚧  Faz 1
├── notification-service/       🚧  Faz 4
└── frontend/                   🚧  Faz 5
```

Her Java servisinin **kendi `pom.xml`'i** vardır; ortak bir üst pom kullanılmaz. Mikroservislerin bağımsız derlenip bağımsız dağıtılabilmesi bu mimarinin amacıdır, ortak bir üst pom onları sürüm olarak birbirine bağlardı.

---

## 11. Sorun Giderme

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
