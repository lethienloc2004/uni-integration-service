# =====================================================================
# NFR-11 — trien khai doc lap: chay duoc tu README tren may khac, khong phu
# thuoc IDE. Co so du lieu la thu vien nhung nen khong can dich vu ngoai nao.
# =====================================================================

# ---- Tang bien dich -------------------------------------------------
# GHIM phien ban cu the thay vi dung tag noi "3.9".
#
# Ly do rat cu the: tag noi 3.9-eclipse-temurin-21 tung keo ve mot ban ma JDK ben trong
# khong chay duoc — `uname` chay binh thuong nhung `java` im lang, khien `mvn package`
# khong tao ra thu muc target va buoc COPY --from=build that bai voi thong bao rat kho hieu
# ("lstat /build/target: no such file or directory"). Ghim phien ban lam build tai lap duoc
# va loai han ca lop loi do.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Tai phu thuoc truoc, tach rieng de tan dung cache khi chi doi ma nguon
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# ---- Tang chay ------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Khong chay bang root. Tao nhom TUONG MINH thay vi dua vao hanh vi mac dinh
# cua useradd, vi hanh vi do khac nhau giua cac ban phan phoi.
RUN groupadd -r -g 1001 appuser \
 && useradd -r -u 1001 -g appuser appuser

COPY --from=build /build/target/*.jar app.jar

# Thu muc du lieu H2 — gan volume de anh xa va su kien dang cho song sot
# qua cac lan chay lai container
RUN mkdir -p /app/data && chown -R appuser:appuser /app
VOLUME /app/data
USER appuser

EXPOSE 8080

# CO Y KHONG khai bao HEALTHCHECK.
#
# Anh JRE khong bao dam co san wget hay curl, ma mot HEALTHCHECK goi cong cu khong ton tai
# se lam container luon bi danh dau "unhealthy" — te hon han la khong khai bao gi.
#
# Endpoint tham do la GET /health (NFR-09). Khi trien khai that, khai bao no o tang dieu phoi
# (docker compose healthcheck, Kubernetes livenessProbe) noi biet chac co cong cu nao san co.

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
