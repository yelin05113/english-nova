FROM maven:3.9.9-eclipse-temurin-17 AS build
ARG SERVICE_NAME
WORKDIR /workspace

COPY pom.xml ./
COPY english-nova-common/pom.xml english-nova-common/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY system-service/pom.xml system-service/pom.xml
COPY study-service/pom.xml study-service/pom.xml
COPY search-service/pom.xml search-service/pom.xml
COPY import-service/pom.xml import-service/pom.xml
COPY quiz-service/pom.xml quiz-service/pom.xml

RUN mvn -q -pl ${SERVICE_NAME} -am -DskipTests dependency:go-offline

COPY english-nova-common/src english-nova-common/src
COPY auth-service/src auth-service/src
COPY gateway-service/src gateway-service/src
COPY system-service/src system-service/src
COPY study-service/src study-service/src
COPY search-service/src search-service/src
COPY import-service/src import-service/src
COPY quiz-service/src quiz-service/src

RUN mvn -q -pl ${SERVICE_NAME} -am -DskipTests package

FROM eclipse-temurin:17-jre
ARG SERVICE_NAME
WORKDIR /app

COPY --from=build /workspace/${SERVICE_NAME}/target/${SERVICE_NAME}-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
