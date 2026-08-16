# A reviewer convenience, never a build dependency (ADR 0007). Nothing in the
# Gradle build or any test refers to this file.
#
# JDK on both stages: classes compiled with --enable-preview only run on the
# same release, and there is no JRE image to pair with a preview build.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY src src
RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY --from=build /app/build/install/lead-validation ./
# The start script carries --enable-preview from applicationDefaultJvmArgs.
ENTRYPOINT ["./bin/lead-validation"]
