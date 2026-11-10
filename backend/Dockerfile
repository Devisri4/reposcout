# Build stage: compile and package the app with Maven (tests already run in CI).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# Run stage: only the Java runtime and the jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home reposcout
USER reposcout
COPY --from=build /app/target/reposcout-0.0.1-SNAPSHOT.jar app.jar
# Render's free plan has 512 MB of memory; keep the JVM well inside it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
