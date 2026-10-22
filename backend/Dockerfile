# Backend image: build with the Maven wrapper, run on a slim Java runtime as a non-root user.

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /app
# Dependencies first, so they're cached unless pom.xml changes
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# Tests run in CI against a real database; the image build only packages
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre-noble
RUN useradd --system --uid 1001 servicelog \
    # Photo storage; production mounts a volume here, which keeps this owner
    && mkdir -p /data/photos && chown servicelog /data/photos
WORKDIR /app
COPY --from=build /app/target/servicelog-*.jar app.jar
USER servicelog
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
