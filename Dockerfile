FROM --platform=$BUILDPLATFORM maven:3.9.11-eclipse-temurin-25 AS build

# download maven dependencies (module poms only, so this layer survives source changes)
RUN mkdir -p /usr/src/build
COPY pom.xml /usr/src/build
COPY sparta-hss-base/pom.xml /usr/src/build/sparta-hss-base/pom.xml
COPY sparta-hss-spring-boot/pom.xml /usr/src/build/sparta-hss-spring-boot/pom.xml
WORKDIR /usr/src/build
RUN mvn -B de.qaware.maven:go-offline-maven-plugin:1.2.8:resolve-dependencies

# compile the jars and collect the runtime classpath
COPY sparta-hss-base /usr/src/build/sparta-hss-base
COPY sparta-hss-spring-boot /usr/src/build/sparta-hss-spring-boot
ARG MVN_ADDITIONAL_ARGS
RUN mvn -B clean package dependency:copy-dependencies -DincludeScope=runtime \
        -DexcludeArtifactIds=spring-boot-devtools $MVN_ADDITIONAL_ARGS

#############################################################

FROM eclipse-temurin:25-jre
RUN mkdir -p /app/lib
WORKDIR /app
COPY --from=build /usr/src/build/sparta-hss-spring-boot/target/sparta-hss-spring-boot.jar /app/sparta-hss.jar
COPY --from=build /usr/src/build/sparta-hss-spring-boot/target/dependency/ /app/lib/

# Default Cx user-profile template (sipgate.ims.userProfile.path points here)
# and default S6a subscription-data profile (sipgate.profileDir points here);
# mount your own over them.
COPY docker/user-profile.xml /app/user-profile.xml
COPY docker/imsiProfiles /app/imsiProfiles

RUN useradd --system --uid 10001 --user-group --no-create-home sparta \
    && mkdir -p /var/lib/sparta-hss \
    && chown sparta:sparta /var/lib/sparta-hss
ENV SPRING_DATASOURCE_URL="jdbc:sqlite:/var/lib/sparta-hss/sparta-hss.db"
VOLUME /var/lib/sparta-hss
USER sparta

# HTTP/management endpoints; Diameter connects outbound to its configured peers
EXPOSE 8080

ENTRYPOINT ["java", "-cp", "/app/sparta-hss.jar:/app/lib/*", "com.sipgate.sparta.hss.SpartaHssApplication"]
