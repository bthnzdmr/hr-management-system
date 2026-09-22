#!/usr/bin/env bash
#
# Bir yedegi GERI YUKLEYIP dogrular.
#
# Bu betik yedekleme kadar onemlidir: geri yuklenmemis bir yedek, yedek
# DEGILDIR -- yalnizca yedek oldugu varsayilan bir dosyadir. Bozuk oldugu,
# ihtiyac duyuldugu gun anlasilir ve o gun cok gectir.
#
# GERI YUKLEME AYRI BIR VERITABANINA YAPILIR. Uzerine yazmak, dogrulamayi
# calisan sistemi riske atan bir islem haline getirirdi ve kimse duzenli
# olarak calistirmazdi. Dogrulama tehlikeliyse yapilmaz; yapilmayan
# dogrulama da yok demektir.
#
# Kullanim:
#   ops/backup/verify-restore.sh backups/employee_db_20260922T101500Z.dump
set -euo pipefail

DUMP="${1:?kullanim: verify-restore.sh <dump dosyasi>}"
CONTAINER="${POSTGRES_CONTAINER:-hr-postgres}"

: "${DB_USERNAME:?DB_USERNAME tanimli degil -- once .env dosyasini kabuga yukleyin}"

# Dosya adindan kaynak veritabanini cikar: employee_db_2026...dump -> employee_db
BASENAME="$(basename "$DUMP")"
SOURCE_DB="${BASENAME%%_2*}"
SCRATCH="verify_${SOURCE_DB}"

echo "Yedek     : $DUMP"
echo "Kaynak    : $SOURCE_DB"
echo "Hedef     : $SCRATCH (gecici)"
echo

cleanup() {
    docker exec "$CONTAINER" psql --username="$DB_USERNAME" --dbname=postgres \
        -q -c "DROP DATABASE IF EXISTS $SCRATCH;" >/dev/null 2>&1 || true
}
trap cleanup EXIT

cleanup
docker exec "$CONTAINER" psql --username="$DB_USERNAME" --dbname=postgres \
    -q -c "CREATE DATABASE $SCRATCH;"

# --exit-on-error SART: onsuz pg_restore hatalari SAYAR ama sifir kodla
# doner ve bozuk bir yedek "basarili" gorunur.
docker exec -i "$CONTAINER" pg_restore \
    --username="$DB_USERNAME" \
    --dbname="$SCRATCH" \
    --exit-on-error \
    --no-owner \
    < "$DUMP"

echo "Geri yukleme tamam. Karsilastirma:"
echo

# Satir sayilari KARSILASTIRILIR, yalnizca "hata olmadi" denmez: bos bir
# dokum de hatasiz geri yuklenir. Olcut, verinin GERCEKTEN orada olmasidir.
mismatch=0
tables="$(docker exec "$CONTAINER" psql --username="$DB_USERNAME" --dbname="$SOURCE_DB" \
    -tAc "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY 1;")"

printf "  %-24s %10s %10s\n" "tablo" "kaynak" "geri yuklenen"
printf "  %-24s %10s %10s\n" "------------------------" "----------" "-------------"

for t in $tables; do
    t="$(echo "$t" | tr -d '\r')"
    [ -z "$t" ] && continue
    a="$(docker exec "$CONTAINER" psql --username="$DB_USERNAME" --dbname="$SOURCE_DB" \
        -tAc "SELECT count(*) FROM \"$t\";" | tr -d '\r')"
    b="$(docker exec "$CONTAINER" psql --username="$DB_USERNAME" --dbname="$SCRATCH" \
        -tAc "SELECT count(*) FROM \"$t\";" | tr -d '\r')"

    flag=""
    if [ "$a" != "$b" ]; then
        flag="  <-- UYUSMUYOR"
        mismatch=$((mismatch + 1))
    fi
    printf "  %-24s %10s %10s%s\n" "$t" "$a" "$b" "$flag"
done

echo
if [ "$mismatch" -ne 0 ]; then
    echo "BASARISIZ: $mismatch tabloda satir sayisi tutmuyor."
    exit 1
fi

echo "BASARILI: butun tablolar birebir geri yuklendi."
