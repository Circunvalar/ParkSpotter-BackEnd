# syntax=docker/dockerfile:1
# Imagen del backend de ParkSpotter en dos etapas:
#   1. build: compila el jar con Maven (las pruebas ya corren en la CI; aquí se omiten para construir rápido).
#   2. runtime: solo el JRE 21 y el jar, con usuario sin privilegios.
# Funciona en amd64 y arm64 (las VM Ampere A1 de Oracle Always Free son arm64).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw pom.xml lombok.config ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/BackendParkSpotter-*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
# La imagen base ya trae curl (lo usa el HEALTHCHECK).
RUN groupadd --system spring && useradd --system --gid spring --home /app spring
COPY --from=build /app/app.jar app.jar
USER spring
EXPOSE 8080
# MaxRAMPercentage: el heap se ajusta a la memoria del contenedor en vez de a la de la máquina.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    TZ=America/Bogota
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/v3/api-docs > /dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
