FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM quay.io/wildfly/wildfly:41.0.0.Final-jdk21
COPY --from=build --chown=jboss:jboss /root/.m2/repository/org/postgresql/postgresql/42.7.7/postgresql-42.7.7.jar /tmp/postgresql.jar
COPY --chown=jboss:jboss src/main/wildfly/configure.cli /tmp/configure.cli
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --file=/tmp/configure.cli \
    && rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history
COPY --from=build --chown=jboss:jboss /workspace/target/tickets.war /opt/jboss/wildfly/standalone/deployments/ROOT.war
EXPOSE 8080
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]
