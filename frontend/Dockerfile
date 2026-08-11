FROM node:22-alpine AS build
WORKDIR /build

# Kilit dosyasiyla birlikte kopyalanir: npm ci tam olarak kilitteki surumleri
# kurar, npm install gibi surumleri yukseltmez.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# API adresi build zamaninda koda gomulur, calisma aninda okunmaz.
# Varsayilan localhost'tur: istegi yapan sey konteyner degil, kullanicinin
# TARAYICISIDIR ve tarayici Docker aginin disindadir -- "employee-service"
# adini cozemez.
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build


# Vite gelistirme sunucusu uretimde kullanilmaz; statik dosyalar nginx ile sunulur.
# unprivileged surumu root olmadan calisir.
FROM nginxinc/nginx-unprivileged:1.27-alpine

ARG VITE_API_URL=http://localhost:8080

COPY nginx.conf /etc/nginx/conf.d/default.conf

# CSP'deki API adresi, paketin icine gomulen adresle AYNI degerden uretilir.
# Ikisi ayri yerden gelseydi, birini degistirip digerini unutmak uygulamayi
# sessizce kirardi.
USER root
RUN sed -i "s|__API_ORIGIN__|${VITE_API_URL}|" /etc/nginx/conf.d/default.conf
USER nginx

COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 8080
