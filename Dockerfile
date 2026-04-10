# ============================================================
#  Stage 1: Build
# ============================================================
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /build

# Copy pom first — lets Docker cache the dependency layer
# so re-builds only re-download deps when pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy sources and build
COPY src ./src
RUN mvn package -DskipTests -q

# ============================================================
#  Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Non-root user for security
RUN groupadd --system mockserver && \
    useradd  --system --gid mockserver --no-create-home mockserver

COPY --from=build /build/target/mock-server-*.jar app.jar
RUN chown mockserver:mockserver app.jar

USER mockserver

# Admin UI port (fixed)
EXPOSE 8080
# User mock ports range
EXPOSE 9000-9020

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
