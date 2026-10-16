#!/usr/bin/env bash
# Изолированный стенд «целиком»: Backend-Auth + фронт oper-dev + Mesplan (enforce).
# Ни одна рабочая служба и ни одна рабочая база не используются.
#
#   test-stand/full.sh up      поднять (Mesplan — test-stand/stand.sh, режим enforce)
#   test-stand/full.sh down    всё удалить
#
# Порты (только 127.0.0.1): фронт :18081, Backend-Auth :14546, Mesplan :18687.
# Пользователи: planner (ROLE_USER, ROLE_MESPLAN), Regar (ROLE_USER, ROLE_D — без
# доступа к МЕСПЛАН), admin (ROLE_ADMIN). Пароль общий, случайный — ${CACHE}/password.
# Остальные службы фронта — на мёртвом адресе (ISOLATED=1), AsoupServer тоже.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AUTH_REPO="${AUTH_REPO:-${ROOT}/../Backend-Auth-dev}"
FRONT_REPO="${FRONT_REPO:-${ROOT}/../oper-dev}"
CACHE="${HOME}/.cache/mesplan-stand"
NET=mp-stand

up() {
  local auth_jar
  auth_jar="$(ls "${AUTH_REPO}"/target/*.jar 2>/dev/null | head -1)"
  [ -n "${auth_jar}" ] || { echo "Нет ${AUTH_REPO}/target/*.jar — сначала собрать Backend-Auth"; exit 1; }
  mkdir -p "${CACHE}"
  [ -s "${CACHE}/jwt" ] || { echo -n "mesplan-$(openssl rand -hex 48)" > "${CACHE}/jwt"; chmod 600 "${CACHE}/jwt"; }
  [ -s "${CACHE}/password" ] || { echo "Test-$(openssl rand -hex 6)" > "${CACHE}/password"; chmod 600 "${CACHE}/password"; }

  AUTH_MODE=enforce JWT_SECRET="$(cat "${CACHE}/jwt")" "${ROOT}/test-stand/stand.sh" up
  local sqlcmd=(docker exec mp-stand-sql /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$(cat "${CACHE}/pw")")
  "${sqlcmd[@]}" -Q "CREATE DATABASE backend COLLATE Cyrillic_General_CI_AS" >/dev/null
  docker cp "${AUTH_REPO}/docs/auth-schema.sql" mp-stand-sql:/tmp/auth-schema.sql
  "${sqlcmd[@]}" -i /tmp/auth-schema.sql >/dev/null
  local hash
  hash="$(python3 -I -c "import bcrypt,sys; print(bcrypt.hashpw(sys.argv[1].encode(), bcrypt.gensalt(10)).decode().replace('\$2b\$','\$2a\$'))" "$(cat "${CACHE}/password")")"
  "${sqlcmd[@]}" -d backend -Q "SET NOCOUNT ON;
    INSERT INTO role(id,name,description) VALUES (1,'ROLE_USER','user'),(2,'ROLE_ADMIN','admin'),(3,'ROLE_D','d'),(4,'ROLE_MESPLAN','mesplan'),(5,'ROLE_STAT','stat');
    SET IDENTITY_INSERT departments ON;
    INSERT INTO departments(id,name,address,station) VALUES (1,'Отдел планирования','тест',0),(2,'Регар','тест',745807);
    SET IDENTITY_INSERT departments OFF;
    SET IDENTITY_INSERT users ON;
    INSERT INTO users(id,name,firstname,lastname,email,enabled,password,dep_id,job,datecreate) VALUES
      (1,'planner','Тест','Планировщик','planner@test',1,'${hash}',1,'плановик',GETDATE()),
      (2,'Regar','Тест','Регар','regar@test',1,'${hash}',2,'оператор',GETDATE()),
      (3,'admin','Тест','Админ','admin@test',1,'${hash}',1,'администратор',GETDATE());
    SET IDENTITY_INSERT users OFF;
    INSERT INTO user_roles(user_id,roles_id) VALUES (1,1),(1,4),(2,1),(2,3),(3,2);"

  cp "${auth_jar}" "${CACHE}/backend-auth.jar"
  docker run -d --name mp-stand-auth --network "${NET}" -p 127.0.0.1:14546:4545 \
    -e "DB_URL=jdbc:sqlserver://mp-stand-sql:1433;databaseName=backend" -e DB_USERNAME=sa \
    -e "DB_PASSWORD=$(cat "${CACHE}/pw")" -e "JWT_SECRET=$(cat "${CACHE}/jwt")" \
    -v "${CACHE}/backend-auth.jar:/app.jar:ro" maven:3.8-openjdk-8 java -jar /app.jar >/dev/null

  DOCKER_BUILDKIT=1 docker build -q -f "${FRONT_REPO}/Dockerfile.devtest" \
    --build-arg TEST_BACKEND_URL=http://localhost:14546 \
    --build-arg TEST_ASOUPSERVER_URL=http://127.0.0.1:9 \
    --build-arg TEST_MESPLAN_URL=http://localhost:18687 \
    --build-arg ISOLATED=1 -t oper-dev:mesplan "${FRONT_REPO}" >/dev/null
  docker run -d --name mp-stand-front -p 127.0.0.1:18081:4200 oper-dev:mesplan >/dev/null

  for _ in $(seq 1 60); do
    if curl -s -m 3 -o /dev/null -w '%{http_code}' http://localhost:14546/api/auth/signin -X POST \
         -H 'Content-Type: application/json' -d '{}' 2>/dev/null | grep -qE '^[1-5]'; then
      echo "Стенд готов: фронт http://localhost:18081/oper/login (planner | Regar | admin, пароль: ${CACHE}/password)"
      return
    fi
    sleep 3
  done
  echo "Backend-Auth не поднялся: docker logs mp-stand-auth"; exit 1
}

down() {
  docker rm -f mp-stand-front mp-stand-auth >/dev/null 2>&1 || true
  "${ROOT}/test-stand/stand.sh" down
}

case "${1:-}" in
  up) up ;;
  down) down ;;
  *) sed -n '2,12p' "$0"; exit 1 ;;
esac
