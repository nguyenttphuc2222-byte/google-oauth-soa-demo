FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/seminar-app.jar /app/seminar-app.jar
RUN install -d -o 10001 -g 10001 /app/data
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/seminar-app.jar"]
