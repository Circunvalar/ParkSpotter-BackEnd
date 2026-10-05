#!/usr/bin/env bash
# =============================================================================
# Prueba de punta a punta: levanta la API (jar ya compilado) contra la base de datos
# indicada por las variables SPRING_DATASOURCE_*, espera a que responda, ejecuta
# scripts/smoke-test.sh y apaga la API. Lo usa la CI y se puede correr en local.
#
# Uso:
#   ./mvnw -DskipTests package
#   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/parkspotter_test \
#   SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=... \
#   ./scripts/e2e.sh
#
# ¡Nunca apuntar a la base de producción! El smoke test crea usuarios y garajes de prueba.
# =============================================================================
set -euo pipefail

PORT="${PORT:-8080}"
LOG="${LOG:-target/e2e-app.log}"
JAR="${JAR:-$(ls target/BackendParkSpotter-*.jar 2>/dev/null | grep -v '\.original$' | head -1)}"

if [ -z "${SPRING_DATASOURCE_URL:-}" ]; then
    echo "Define SPRING_DATASOURCE_URL (y usuario/contraseña) con una base de datos de pruebas." >&2
    exit 2
fi
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
    echo "No se encontró el jar en target/. Ejecuta antes: ./mvnw -DskipTests package" >&2
    exit 2
fi

echo "Levantando $JAR en el puerto $PORT (log en $LOG)..."
java -jar "$JAR" --server.port="$PORT" > "$LOG" 2>&1 &
APP_PID=$!

stop_app() {
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
}
trap stop_app EXIT

# Espera hasta 120 s a que la API responda (curl reintenta mientras la conexión sea rechazada)
if ! curl -s -o /dev/null --retry 120 --retry-connrefused --retry-delay 1 --retry-max-time 120 \
        "http://localhost:$PORT/api/v1/garages/search"; then
    echo "La API no respondió a tiempo. Últimas líneas del log:" >&2
    tail -n 80 "$LOG" >&2
    exit 1
fi
echo "API lista."

if ! BASE_URL="http://localhost:$PORT" bash "$(dirname "$0")/smoke-test.sh"; then
    echo "El smoke test falló. Errores en el log de la API:" >&2
    grep -E "ERROR|Exception" "$LOG" | tail -n 40 >&2 || true
    exit 1
fi
