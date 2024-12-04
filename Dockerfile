FROM maven:3.9.9-amazoncorretto-23 AS build

COPY src src
COPY pom.xml pom.xml

RUN mvn clean package assembly:single
RUN ls

FROM amazoncorretto:23


COPY --from=build target/server-distribution ./server-distribution

ENTRYPOINT ["sh", "server-distribution/bin/server.sh"]