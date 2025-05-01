# --- Build Stage ---
FROM maven:3.9-eclipse-temurin-17 as builder
WORKDIR /build

# === NEW: Section to handle local JAR ===
# 1. Create a directory for the local repo inside the container (optional but clean)
# RUN mkdir -p /build/local-repo
# 2. Copy the local JAR from the build context into the container
COPY libs/cloud-transfer-lib-1.0-SNAPSHOT.jar /build/cloud-transfer-lib.jar
# 3. Install the JAR into the container's Maven repository
#    Ensure group/artifact/version match your pom.xml dependency EXACTLY
RUN mvn install:install-file \
    -Dfile=/build/cloud-transfer-lib.jar \
    -DgroupId=org.manishsharan.cloudtransfer \
    -DartifactId=cloud-transfer-lib \
    -Dversion=1.0-SNAPSHOT \
    -Dpackaging=jar \
    -DgeneratePom=true
# === END NEW SECTION ===

# Copy the pom.xml file AFTER installing the local dependency
COPY pom.xml .

# Download dependencies (now it should find the local one installed above)
RUN mvn dependency:go-offline --fail-never || true

# Copy the rest of the source code
COPY src ./src

# Package the application
RUN mvn package -Dmaven.test.skip=true

# --- Runtime Stage ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/cloud-transfer-webapp-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:+UseStringDeduplication", "-jar", "app.jar"]
# Optional: Add non-root user
# RUN addgroup -S appgroup && adduser -S appuser -G appgroup
# USER appuser