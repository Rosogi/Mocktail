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
RUN groupadd --system mocktail && \
    useradd  --system --gid mocktail --no-create-home mocktail

COPY --from=build /build/target/mocktail*.jar mocktail.jar
RUN chown mocktail:mocktail mocktail.jar

USER mocktail

# Admin UI port (fixed)
EXPOSE 8080
# User mock ports range
EXPOSE 9000-9020

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "mocktail.jar"]
