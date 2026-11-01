FROM maven:3.9.11-eclipse-temurin-21-alpine AS dependencies
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

FROM dependencies AS test
COPY src ./src
RUN mvn -B -ntp test

FROM test AS build
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-alpine-3.22 AS runtime
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER spring
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD wget -q -O - http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
