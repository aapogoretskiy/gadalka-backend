FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
#   JAVA_OPTS: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]