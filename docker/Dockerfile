# linux/amd64 image for openEuler x86_64 + Docker 20.10.x
# Base is Ubuntu 22.04 (glibc). Container OS does not need to match the Euler host.
# Override when Docker Hub is unreachable, e.g.:
#   --build-arg BASE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:17-jre-jammy
ARG BASE_IMAGE=eclipse-temurin:17-jre-jammy
FROM ${BASE_IMAGE}

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dpolyglot.engine.WarnInterpreterOnly=false" \
    PROFILES_ACTIVE=docker \
    SPRING_DATASOURCE_URL="jdbc:mysql://132.147.160.230:2881/iflow?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
    SPRING_DATASOURCE_USERNAME=health_user \
    SPRING_DATASOURCE_PASSWORD="Helian@2022"

WORKDIR /app

COPY iflow-web/target/iflow.jar /app/iflow.jar
COPY docs/init_mysql /app/sql

EXPOSE 8099

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dprofiles.active=${PROFILES_ACTIVE} -jar /app/iflow.jar"]
