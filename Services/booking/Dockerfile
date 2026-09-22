FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM tomcat:11-jre21-temurin
RUN rm -rf /usr/local/tomcat/webapps/ROOT
COPY --from=build /workspace/target/booking.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
