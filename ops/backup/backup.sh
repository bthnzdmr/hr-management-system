#!/usr/bin/env bash
#
# Iki veritabaninin da yedegini alir.
#
# NEDEN pg_dump, VOLUME KOPYASI DEGIL?
# Calisan bir PostgreSQL'in veri dizinini kopyalamak TUTARSIZ bir yedek
# uretir: kopyalama surerken yazilan sayfalar yarim yakalanir. Dogru yol ya
# dosya sistemi anlik goruntusu ya da mantiksal dokumdur. pg_dump tek bir
# tutarli anlik goruntuden okur ve ayrica SURUMLER ARASI tasinabilir --
# PostgreSQL 16 dokumu 17'ye yuklenebilir, ham veri dizini yuklenemez.
#
# NEDEN --format=custom?
# Duz SQL yerine ikili bicim: sikistirilmis, PARALEL geri yuklenebilir ve
# pg_restore ile TEK TABLO secilerek acilabilir. Duz SQL'de yalnizca
# "hepsini calistir" vardir.
#
# Kullanim:
#   ops/backup/backup.sh              # ./backups altina yazar
#   BACKUP_DIR=/mnt/yedek ops/backup/backup.sh
set -euo pipefail

CONTAINER="${POSTGRES_CONTAINER:-hr-postgres}"
BACKUP_DIR="${BACKUP_DIR:-backups}"
DATABASES="${DATABASES:-employee_db notification_db}"

: "${DB_USERNAME:?DB_USERNAME tanimli degil -- once .env dosyasini kabuga yukleyin}"

# Zaman damgasi UTC: yerel saatle adlandirilan yedekler yaz saati gecisinde
# ayni ada iki kez yazilabilir ve biri digerini ezer.
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BACKUP_DIR"

echo "Yedekleme basliyor: $STAMP (UTC)"

for db in $DATABASES; do
    out="$BACKUP_DIR/${db}_${STAMP}.dump"

    # Once gecici bir ada yazilir, sonra tasinir: yarida kesilen bir yedek
    # TAMAMLANMIS gibi gorunmemelidir -- geri yukleme aninda fark edilen bir
    # bozuk yedek, hic yedek olmamasindan kotudur.
    docker exec "$CONTAINER" pg_dump \
        --username="$DB_USERNAME" \
        --dbname="$db" \
        --format=custom \
        --compress=9 \
        > "${out}.partial"

    mv "${out}.partial" "$out"
    printf "  %-20s %s\n" "$db" "$(du -h "$out" | cut -f1)"
done

echo
echo "Yedekler: $BACKUP_DIR"
echo "DOGRULAMA: ops/backup/verify-restore.sh calistirilmadan bir yedek"
echo "kanitlanmis sayilmaz -- geri yuklenmemis yedek, yedek degildir."
