# Native build. Maven is copied from the official Maven image, while the
# compilation itself runs with GraalVM Native Image.
FROM maven:3.9.11-eclipse-temurin-21 AS maven

FROM ghcr.io/graalvm/native-image-community:21 AS build
WORKDIR /workspace
COPY --from=maven /usr/share/maven /usr/share/maven
COPY --from=maven /usr/bin/mvn /usr/bin/mvn
ENV MAVEN_HOME=/usr/share/maven
ENV PATH="/usr/share/maven/bin:${PATH}"

COPY pom.xml .
RUN mvn -q -Pnative -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -Pnative -DskipTests native:compile

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates wget libstdc++6 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/sso-ident /app/sso-ident

EXPOSE 8080
ENTRYPOINT ["/app/sso-ident"]
