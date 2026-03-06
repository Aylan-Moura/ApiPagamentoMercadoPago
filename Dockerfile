# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x ./mvnw

# Cache de dependências (rebuilds mais rápidos)
RUN ./mvnw dependency:go-offline -B

# Copia apenas o código principal — ignora pasta de testes no build
COPY src/main src/main
RUN ./mvnw clean package -DskipTests -B

# ── Runtime stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Usuário não-root
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 3000

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
