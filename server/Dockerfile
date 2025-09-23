# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

ARG JAR_FILE
COPY target/${JAR_FILE} app.jar

## Override it with: java -jar /app.jar
## Default remote-debug on
CMD ["java", "-Xdebug", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8010", "-jar", "/app.jar"]
