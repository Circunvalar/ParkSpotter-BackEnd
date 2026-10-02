#!/usr/bin/env bash
# =============================================================================
# Smoke test de ParkSpotter (Sprint 01 a 03): verifica de punta a punta que la API
# desplegada funciona: autenticación, CRUD de garajes, plazas, búsqueda, mapa,
# detalle, estado en tiempo real (SSE), validaciones, seguridad y tiempos de respuesta.
#
# Uso:   BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh
# Requisitos: bash y curl (Git Bash en Windows sirve).
# Crea un usuario y garajes de prueba con nombres únicos; no borra datos existentes.
# Sale con código 0 si todo pasa y 1 si algo falla.
# =============================================================================
set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1"
ORIGIN="${ORIGIN:-http://localhost:3000}"
MAX_SECONDS="${MAX_SECONDS:-3}"
RUN_ID="$(date +%s)"
EMAIL="smoke-${RUN_ID}@parkspotter.co"
PASSWORD="SmokeTest2026!"
TMP_DIR="$(mktemp -d)"
PASSED=0
FAILED=0

trap 'rm -rf "$TMP_DIR"' EXIT

# ---------- utilidades ----------

pass() { PASSED=$((PASSED + 1)); printf '  \033[32mOK\033[0m   %s\n' "$1"; }
fail() { FAILED=$((FAILED + 1)); printf '  \033[31mFALLA\033[0m %s\n' "$1"; [ -n "${2:-}" ] && printf '        %s\n' "$2"; }
section() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# request METODO RUTA [BODY] [TOKEN] -> deja el código en $STATUS, el cuerpo en $BODY y el tiempo en $TIME
request() {
    local method="$1" path="$2" body="${3:-}" token="${4:-}"
    local args=(-s -o "$TMP_DIR/body" -w '%{http_code} %{time_total}' -X "$method" "$API$path" -H 'Accept: application/json')
    [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
    [ -n "$body" ] && args+=(-H 'Content-Type: application/json' --data-binary "$body")
    local result
    result="$(curl "${args[@]}")"
    STATUS="${result%% *}"
    TIME="${result##* }"
    BODY="$(cat "$TMP_DIR/body")"
}

# json_value CAMPO -> primer valor de "CAMPO" en $BODY (texto o número)
json_value() {
    printf '%s' "$BODY" | grep -oE "\"$1\":(\"[^\"]*\"|-?[0-9.]+|true|false|null)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"
}

expect_status() {
    local expected="$1" label="$2"
    if [ "$STATUS" = "$expected" ]; then pass "$label ($STATUS)"; else fail "$label: se esperaba $expected y llegó $STATUS" "$(printf '%s' "$BODY" | head -c 300)"; fi
}

expect_contains() {
    local needle="$1" label="$2"
    if printf '%s' "$BODY" | grep -qF -- "$needle"; then pass "$label"; else fail "$label: falta $needle" "$(printf '%s' "$BODY" | head -c 300)"; fi
}

expect_fast() {
    local label="$1"
    if awk -v t="$TIME" -v max="$MAX_SECONDS" 'BEGIN { exit !(t < max) }'; then pass "$label responde en ${TIME}s (< ${MAX_SECONDS}s)"; else fail "$label tardó ${TIME}s (máximo ${MAX_SECONDS}s)"; fi
}

printf 'Smoke test contra %s\n' "$BASE_URL"

# ---------- 1. Autenticación ----------
section "1. Autenticación"
request POST /auth/register "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
expect_status 200 "Registro de usuario"
TOKEN="$(json_value accessToken)"
REFRESH="$(json_value refreshToken)"

request POST /auth/register "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
expect_status 409 "Correo duplicado"

request POST /auth/register '{"email":"no-es-correo","password":"corta"}'
expect_status 400 "Registro con datos inválidos"
expect_contains '"password"' "El error indica el campo password"

request POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"OtraClave99999\"}"
expect_status 401 "Login con contraseña incorrecta"

request POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
expect_status 200 "Login correcto"
expect_fast "Login"

request GET /auth/me "" "$TOKEN"
expect_status 200 "Perfil del usuario (/auth/me)"

request GET /auth/me
expect_status 401 "Endpoint protegido sin token"

# ---------- 2. Garajes ----------
section "2. Gestión de garajes"
request POST /garages '{"name":"","totalSpots":0,"open24Hours":false}' "$TOKEN"
expect_status 400 "Garaje con datos inválidos"
expect_contains '"details"' "El error trae el detalle por campo"

request POST /garages "{\"name\":\"Smoke Centro $RUN_ID\",\"addressLine\":\"Cra 7 # 12-30\",\"city\":\"Bogot\\u00e1\",\"state\":\"Cundinamarca\",\"country\":\"Colombia\",\"latitude\":4.710989,\"longitude\":-74.072092,\"totalSpots\":6,\"pricePerHour\":3500,\"open24Hours\":true}" "$TOKEN"
expect_status 200 "Crear garaje 24 horas"
GARAGE_ID="$(json_value id)"

request POST /garages "{\"name\":\"Smoke Nocturno $RUN_ID\",\"addressLine\":\"Calle 119 # 6-20\",\"city\":\"Bogot\\u00e1\",\"state\":\"Cundinamarca\",\"country\":\"Colombia\",\"latitude\":4.7030,\"longitude\":-74.0300,\"totalSpots\":3,\"pricePerHour\":2500,\"open24Hours\":false,\"openingTime\":\"18:00\",\"closingTime\":\"06:00\"}" "$TOKEN"
expect_status 200 "Crear garaje con horario nocturno"

request GET "/garages/$GARAGE_ID"
expect_status 200 "Consulta pública de un garaje"
if printf '%s' "$BODY" | grep -qF "$EMAIL"; then fail "La consulta pública expone el correo del dueño"; else pass "La consulta pública no expone el correo del dueño"; fi

request PUT "/garages/$GARAGE_ID" '{"availableSpots":2}' "$TOKEN"
expect_status 400 "availableSpots no se puede editar a mano"

# ---------- 3. Búsqueda, mapa y detalle ----------
section "3. Búsqueda, mapa y detalle"
request GET "/garages/search?q=Smoke%20Centro%20$RUN_ID"
expect_status 200 "Búsqueda por texto"
expect_contains "\"id\":\"$GARAGE_ID\"" "La búsqueda encuentra el garaje"
expect_contains '"totalElements"' "Respuesta paginada"
expect_fast "Búsqueda por texto"

request GET "/garages/search?lat=4.710989&lng=-74.072092&radiusKm=5&onlyAvailable=true&openNow=true&sort=DISTANCE"
expect_status 200 "Búsqueda por cercanía con filtros"
expect_contains '"distanceKm"' "Incluye la distancia"
expect_fast "Búsqueda por cercanía"

request GET "/garages/search?size=500"
expect_status 400 "Búsqueda con size fuera de rango"

request GET "/garages/search?vehicleType=AVION"
expect_status 400 "Búsqueda con tipo de vehículo inexistente"

request GET "/garages/map?minLat=4.55&minLng=-74.20&maxLat=4.80&maxLng=-73.95"
expect_status 200 "Marcadores del mapa"
expect_contains '"markers"' "Respuesta con marcadores"
expect_fast "Mapa"

request GET "/garages/$GARAGE_ID/detail?includeSpots=true"
expect_status 200 "Detalle del garaje con plazas"
expect_contains '"availabilityByVehicleType"' "Disponibilidad por tipo de vehículo"
expect_contains '"code":"P-001"' "Incluye las plazas generadas"

request GET "/garages/00000000-0000-0000-0000-000000000000/detail"
expect_status 404 "Detalle de un garaje inexistente"

# ---------- 4. Plazas y tiempo real ----------
section "4. Plazas y estado en tiempo real"
request GET "/garages/$GARAGE_ID/spots?status=AVAILABLE"
expect_status 200 "Listado público de plazas"
SPOT_ID="$(json_value id)"

# Se abre el stream SSE en segundo plano y luego se ocupa una plaza
curl -s -N --max-time 6 -H 'Accept: text/event-stream' "$API/garages/$GARAGE_ID/availability/stream" > "$TMP_DIR/sse" &
SSE_PID=$!
curl -s -o /dev/null --max-time 2 "$API/garages/$GARAGE_ID/availability" # da tiempo a que el stream se conecte

request PATCH "/garages/$GARAGE_ID/spots/$SPOT_ID/status?status=OCCUPIED"
expect_status 401 "Cambiar estado de plaza sin token"

request PATCH "/garages/$GARAGE_ID/spots/$SPOT_ID/status?status=OCCUPIED" "" "$TOKEN"
expect_status 200 "Ocupar una plaza (dueño)"

wait "$SSE_PID" 2>/dev/null
if grep -q 'event:availability' "$TMP_DIR/sse" && grep -q '"occupiedSpots":1' "$TMP_DIR/sse"; then
    pass "El stream SSE envía el estado inicial y el cambio en tiempo real"
else
    fail "El stream SSE no entregó el cambio" "$(head -c 300 "$TMP_DIR/sse")"
fi

request GET "/garages/$GARAGE_ID/availability"
expect_status 200 "Disponibilidad actual"
expect_contains '"occupiedSpots":1' "La disponibilidad refleja la plaza ocupada"

request POST "/garages/$GARAGE_ID/spots" '{"code":"M-01","floor":2,"vehicleType":"MOTORCYCLE"}' "$TOKEN"
expect_status 201 "Agregar plaza para moto"
MOTO_ID="$(json_value id)"

request GET "/garages/search?vehicleType=MOTORCYCLE&q=Smoke%20Centro%20$RUN_ID"
expect_contains "\"id\":\"$GARAGE_ID\"" "El filtro por tipo de vehículo encuentra el garaje"

request DELETE "/garages/$GARAGE_ID/spots/$MOTO_ID" "" "$TOKEN"
expect_status 204 "Eliminar plaza libre"

# ---------- 5. Seguridad y cierre de sesión ----------
section "5. Seguridad y cierre de sesión"
PREFLIGHT="$(curl -s -o /dev/null -w '%{http_code}' -X OPTIONS "$API/auth/login" -H "Origin: $ORIGIN" \
    -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: content-type,x-client-id')"
if [ "$PREFLIGHT" = "200" ]; then pass "CORS permite el login desde $ORIGIN con X-Client-Id"; else fail "CORS rechazó el preflight ($PREFLIGHT)"; fi

request POST /auth/logout "{\"refreshToken\":\"$REFRESH\"}"
expect_status 204 "Logout"
request POST /auth/refresh "{\"refreshToken\":\"$REFRESH\"}"
expect_status 401 "El refresh token queda revocado tras el logout"

# ---------- resumen ----------
printf '\n\033[1mResultado: %d OK, %d fallas\033[0m\n' "$PASSED" "$FAILED"
[ "$FAILED" -eq 0 ]
