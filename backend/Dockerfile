# Build and run stages are separate so the runtime image carries a JRE and one jar, not a JDK
# and a Maven cache.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Dependencies resolve in their own layer, so editing a source file does not re-download
# Maven Central on every deploy.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests are not run here. `./mvnw verify` gates the commit; running them again inside the image
# build would need a database and would slow every deploy for no extra signal.
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre

# Not root: a container escape starting from uid 0 is a different kind of day. /app is owned by
# it so a JVM crash can at least write its hs_err log next to the jar.
RUN useradd --uid 1001 --no-create-home app && mkdir -p /app && chown app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /build/target/*.jar app.jar
USER app

# The image IS the deployment, so it defaults to the deployed configuration. Without this a
# host with every database variable set correctly still starts on in-memory H2, reports itself
# healthy, and loses every bookmark on the next restart — the failure is invisible until the
# data is gone. Local development does not use this image; it uses ./mvnw spring-boot:run.
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
