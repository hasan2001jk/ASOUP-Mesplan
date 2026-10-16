#!/usr/bin/env bash
# Одноразовый стенд Mesplan в Docker — проверка без рабочего сервера и рабочей базы.
#
#   test-stand/stand.sh up      поднять SQL Server и службу (сначала: gradle bootJar)
#   test-stand/stand.sh down    удалить контейнеры
#   test-stand/stand.sh logs    журнал службы
#   test-stand/stand.sh sql "SELECT …"   запрос к базе стенда
#
#   AUTH_MODE=enforce JWT_SECRET=… test-stand/stand.sh up   — с проверкой токенов
#
# Внутри:
#   - SQL Server 2019, база mesplan с кириллической сортировкой (Cyrillic_General_CI_AS)
#     и схемой рабочей базы из SCHEMA (по умолчанию docs/schema-2025-01-22.sql — снимок
#     IntelliJ). DDL=create — вместо этого схему создаёт служба по сущностям.
#   - три строки справочников (род вагона, признак собственности, типоразмер);
#   - служба из build/libs/mesplan.jar на Java 1.8.0_65 — той же версии, что на сервере
#     (Zulu 8u65 берётся из кэша стенда AsoupServer или скачивается один раз).
#
# Служба: http://localhost:18687. Пароль базы случайный и живёт в ~/.cache/mesplan-stand.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${HOME}/.cache/mesplan-stand"
NET=mp-stand
PORT="${PORT:-18687}"
JDK_ZIP_URL="https://cdn.azul.com/zulu/bin/zulu8.10.0.1-ca-jdk8.0.65-linux_x64.zip"
JDK_DIR="${HOME}/.cache/asoupserver-stand/zulu1.8.0_65-8.10.0.1-x86lx64"

sqlcmd() {
  docker exec mp-stand-sql /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$(cat "${CACHE}/pw")" "$@"
}
sql() { sqlcmd -d mesplan -h -1 -W -Q "SET NOCOUNT ON; $1"; }

ensure_jdk() {
  [ -x "${JDK_DIR}/bin/java" ] && return
  mkdir -p "$(dirname "${JDK_DIR}")"
  echo "Скачиваю Java 1.8.0_65 (Zulu)…"
  curl -fsSL -o "$(dirname "${JDK_DIR}")/jdk65.zip" "${JDK_ZIP_URL}"
  python3 -I -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" \
    "$(dirname "${JDK_DIR}")/jdk65.zip" "$(dirname "${JDK_DIR}")"
  chmod -R u+x "${JDK_DIR}/bin" "${JDK_DIR}/jre/bin"
}

up() {
  [ -f "${ROOT}/build/libs/mesplan.jar" ] || { echo "Нет build/libs/mesplan.jar — сначала gradle bootJar"; exit 1; }
  ensure_jdk
  mkdir -p "${CACHE}/cfg"
  echo "T$(openssl rand -hex 12)!a" > "${CACHE}/pw"; chmod 600 "${CACHE}/pw"
  docker network create "${NET}" >/dev/null
  docker run -d --name mp-stand-sql --network "${NET}" -e ACCEPT_EULA=Y \
    -e "MSSQL_SA_PASSWORD=$(cat "${CACHE}/pw")" mcr.microsoft.com/mssql/server:2019-latest >/dev/null
  for _ in $(seq 1 60); do
    sqlcmd -Q "IF DB_ID('mesplan') IS NULL CREATE DATABASE mesplan COLLATE Cyrillic_General_CI_AS" >/dev/null 2>&1 && break
    sleep 3
  done
  if [ "${DDL:-none}" = none ]; then
    docker cp "${SCHEMA:-${ROOT}/docs/schema-2025-01-22.sql}" mp-stand-sql:/tmp/schema.sql
    sqlcmd -d mesplan -b -i /tmp/schema.sql >/dev/null
  fi
  cat > "${CACHE}/cfg/stand.properties" <<EOF
server.port=8687
spring.datasource.url=jdbc:sqlserver://mp-stand-sql:1433;databaseName=mesplan
spring.datasource.username=sa
spring.datasource.hikari.connection-timeout=3000
spring.jpa.hibernate.ddl-auto=${DDL:-none}
EOF
  cp "${ROOT}/build/libs/mesplan.jar" "${CACHE}/app.jar"
  docker run -d --name mp-stand-app --network "${NET}" -p "127.0.0.1:${PORT}:8687" -e LANG=C.UTF-8 \
    -e "MESPLAN_DB_PASSWORD=$(cat "${CACHE}/pw")" \
    -e "MESPLAN_AUTH_MODE=${AUTH_MODE:-off}" -e "JWT_SECRET=${JWT_SECRET:-}" \
    -v "${JDK_DIR}:/jdk:ro" -v "${CACHE}/cfg:/cfg:ro" -v "${CACHE}/app.jar:/app.jar:ro" \
    debian:bookworm-slim /jdk/bin/java -jar /app.jar \
    --spring.config.additional-location=file:/cfg/stand.properties >/dev/null
  for _ in $(seq 1 60); do
    curl -s -m 3 -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/api/dictionaries/przsob" 2>/dev/null \
      | grep -qE '200|401' && break
    sleep 3
  done
  sql "INSERT INTO rodvag(code,symb,name) VALUES('20','КР','Крытые'),('60','ПВ','Полувагоны');
       INSERT INTO przsob(code,name,comment) VALUES(1,'Инвентарный',''),(2,'Собственный','');
       INSERT INTO conttypesize(code,name,nameshort,length,brutto) VALUES(1,'20-футовый','20',20,24);" >/dev/null \
    || { echo "Служба не поднялась — test-stand/stand.sh logs"; exit 1; }
  echo "Стенд готов: http://localhost:${PORT}  (Java 1.8.0_65, режим токенов ${AUTH_MODE:-off})"
}

down() {
  docker rm -f mp-stand-app mp-stand-sql >/dev/null 2>&1 || true
  docker network rm "${NET}" >/dev/null 2>&1 || true
  echo "Стенд удалён"
}

case "${1:-}" in
  up) up ;;
  down) down ;;
  logs) docker logs mp-stand-app 2>&1 ;;
  sql) sql "$2" ;;
  *) sed -n '2,22p' "$0"; exit 1 ;;
esac
