FROM openjdk:8-jre-slim

RUN \
    addgroup --system s2stacutils \
    && adduser --system --disabled-login --disabled-password \
    --home /var/lib/s2stacutils \
    --shell /sbin/nologin \
    --ingroup s2stacutils \
    -u 1000 \
    s2stacutils

COPY ./s2stac/target/scala-2.12/s2stac-assembly-0.1.0-SNAPSHOT.jar /var/lib/s2stacutils/

USER s2stacutils
WORKDIR /var/lib/s2stacutils

ENTRYPOINT ["java", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-jar", "s2stac-assembly-0.1.0-SNAPSHOT.jar"]