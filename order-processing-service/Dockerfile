FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY checkout-service/pom.xml checkout-service/pom.xml
COPY order-processing-service/pom.xml order-processing-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY tracking-service/pom.xml tracking-service/pom.xml
RUN mvn -q -pl common,order-processing-service -am dependency:go-offline

COPY common/src common/src
COPY order-processing-service/src order-processing-service/src
RUN mvn -q -pl common,order-processing-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/order-processing-service/target/order-processing-service-*.jar app.jar
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
