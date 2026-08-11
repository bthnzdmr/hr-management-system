# Cok asamali build: derleme araclari uretim imajina girmez.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# pom.xml once kopyalanir: bagimliliklar degismedigi surece Docker bu katmani
# onbellekten alir ve kod her degistiginde yeniden indirmez.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests


FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Konteyner root olarak calismaz: imajda bir acik bulunursa saldirgan
# dogrudan yonetici yetkisiyle baslamamalidir.
RUN addgroup -S app && adduser -S -G app app
USER app

COPY --from=build --chown=app:app /build/target/*.jar app.jar

EXPOSE 8761

# MaxRAMPercentage olmadan JVM, konteyner sinirinin yalnizca %25'ini heap
# olarak kullanir; 768M sinirin ~500M'i bos kalirdi.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
