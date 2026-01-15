#!/bin/bash
# Verification script for I1.T1 - Quarkus project setup

set -e

echo "==========================================="
echo "Verifying I1.T1 Acceptance Criteria"
echo "==========================================="

cd "$(dirname "$0")"

# 1. Verify Maven clean compile
echo ""
echo "[1/4] Testing 'mvn clean compile'..."
../mvnw clean compile -q -DskipTests
if [ $? -eq 0 ]; then
    echo "✓ Maven clean compile successful"
else
    echo "✗ Maven clean compile failed"
    exit 1
fi

# 2. Verify all required Quarkus extensions in pom.xml
echo ""
echo "[2/4] Verifying required Quarkus extensions in pom.xml..."
REQUIRED_EXTENSIONS=(
    "quarkus-hibernate-reactive-panache"
    "quarkus-reactive-pg-client"
    "quarkus-redis-client"
    "quarkus-websockets"
    "quarkus-oidc"
    "quarkus-smallrye-jwt"
    "quarkus-micrometer-registry-prometheus"
)

MISSING_EXTENSIONS=()
for ext in "${REQUIRED_EXTENSIONS[@]}"; do
    if ! grep -q "<artifactId>$ext</artifactId>" pom.xml; then
        MISSING_EXTENSIONS+=("$ext")
    fi
done

if [ ${#MISSING_EXTENSIONS[@]} -eq 0 ]; then
    echo "✓ All required Quarkus extensions found in pom.xml"
else
    echo "✗ Missing extensions: ${MISSING_EXTENSIONS[*]}"
    exit 1
fi

# 3. Verify package structure
echo ""
echo "[3/4] Verifying package structure..."
REQUIRED_PACKAGES=(
    "src/main/java/com/scrumpoker/api"
    "src/main/java/com/scrumpoker/domain"
    "src/main/java/com/scrumpoker/repository"
    "src/main/java/com/scrumpoker/integration"
    "src/main/java/com/scrumpoker/event"
    "src/main/java/com/scrumpoker/config"
    "src/main/java/com/scrumpoker/security"
)

MISSING_PACKAGES=()
for pkg in "${REQUIRED_PACKAGES[@]}"; do
    if [ ! -d "$pkg" ]; then
        MISSING_PACKAGES+=("$pkg")
    fi
done

PACKAGE_COUNT=$(find src/main/java/com/scrumpoker -maxdepth 1 -type d | wc -l | tr -d ' ')
# Subtract 1 for the scrumpoker directory itself
PACKAGE_COUNT=$((PACKAGE_COUNT - 1))

if [ ${#MISSING_PACKAGES[@]} -eq 0 ] && [ $PACKAGE_COUNT -ge 6 ]; then
    echo "✓ Package structure verified ($PACKAGE_COUNT top-level packages)"
else
    echo "✗ Missing packages: ${MISSING_PACKAGES[*]}"
    exit 1
fi

# 4. Verify application.properties configuration
echo ""
echo "[4/4] Verifying application.properties configuration..."
CONFIG_FILE="src/main/resources/application.properties"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "✗ application.properties not found"
    exit 1
fi

REQUIRED_CONFIGS=(
    "quarkus.datasource.db-kind=postgresql"
    "quarkus.redis"
    "mp.jwt.verify.issuer"
)

MISSING_CONFIGS=()
for cfg in "${REQUIRED_CONFIGS[@]}"; do
    if ! grep -q "$cfg" "$CONFIG_FILE"; then
        MISSING_CONFIGS+=("$cfg")
    fi
done

if [ ${#MISSING_CONFIGS[@]} -eq 0 ]; then
    echo "✓ Application properties configured with placeholders"
else
    echo "✗ Missing configurations: ${MISSING_CONFIGS[*]}"
    exit 1
fi

echo ""
echo "==========================================="
echo "✓ All acceptance criteria verified!"
echo "==========================================="
echo ""
echo "Note: Skipping 'mvn quarkus:dev' health check test"
echo "      (requires Docker for PostgreSQL/Redis services)"
