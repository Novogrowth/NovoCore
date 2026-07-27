# syntax=docker/dockerfile:1
#
# Build context is the REPOSITORY ROOT, not this directory, so that the whole backend tree
# is available. See docker/compose.yml and the root .dockerignore.

# ---------------------------------------------------------------------------------------
# Build stage
# ---------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build
COPY backend/ ./

# Tests are deliberately SKIPPED here, and this image build is therefore NOT the test gate.
# The suite runs against real PostgreSQL via Testcontainers, which would need a Docker
# daemon inside this build. `mvn verify` on a developer machine and in CI is what enforces
# correctness; this stage only produces the artifact.
#
# The cache mount keeps the Maven repository between builds so a code change does not
# re-download the dependency tree. It requires BuildKit, which is the default in current
# Docker versions.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests clean package

# ---------------------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

# curl is here solely so the container healthcheck below can work; the JRE image ships
# without it. Kept to a single package with no recommends.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Never run as root. A fixed uid keeps file ownership predictable across rebuilds and hosts.
RUN groupadd --system --gid 10001 novocore \
    && useradd --system --uid 10001 --gid 10001 --no-create-home novocore

WORKDIR /app

# The `-exec` classifier is not incidental: `app/pom.xml` configures it so the module's main
# artifact stays an ordinary jar, which is what lets the ArchUnit suite see the app module's
# classes at all. Changing one without the other breaks the other.
COPY --from=build /build/app/target/novocore-app-*-exec.jar /app/novocore.jar

USER 10001:10001

EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx so the heap tracks whatever the container limit
# is set to, instead of silently ignoring it.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl --fail --silent http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/novocore.jar"]
