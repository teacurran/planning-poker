# Script Usage Examples

This document provides practical examples for using the automation scripts.

## Basic Usage

### Install Dependencies

```bash
# Full installation
node tools/install.cjs

# What it does:
# ✓ Validates Java 17+ and Maven
# ✓ Installs Maven dependencies
# ✓ Installs npm dependencies in frontend/
# ✓ Smart detection: only reinstalls when needed
```

**Output:**
```
[INFO] Starting dependency installation...
[INFO] Project root: /Users/tea/dev/github/planning-poker
[INFO] Installing backend dependencies...
[INFO] Installing Maven dependencies in backend/
[INFO] Backend dependencies installed successfully
[INFO] Installing frontend dependencies...
[INFO] Installing npm dependencies in frontend/
[INFO] node_modules is up-to-date
[INFO] All dependencies installed successfully
```

### Run Application

```bash
# Start Quarkus in dev mode
node tools/run.cjs

# What it does:
# ✓ Ensures dependencies are installed
# ✓ Starts Quarkus backend with hot-reload
# ✓ Available at http://localhost:8080
```

**Stopping:**
Press `Ctrl+C` to stop the application gracefully.

### Lint Code

```bash
# Lint all code (Java + TypeScript/React)
node tools/lint.cjs

# What it does:
# ✓ Silently ensures dependencies are installed
# ✓ Lints Java code with Maven Compiler
# ✓ Lints TypeScript/React with ESLint
# ✓ Outputs JSON array to stdout
# ✓ Progress messages go to stderr
```

**Success Output (no errors):**
```json
[]
```

**Error Output:**
```json
[
  {
    "type": "error",
    "path": "/path/to/File.java",
    "obj": "",
    "message": "';' expected",
    "line": "42",
    "column": "10"
  },
  {
    "type": "error",
    "path": "/path/to/Component.tsx",
    "obj": "@typescript-eslint/no-unused-vars",
    "message": "'foo' is defined but never used.",
    "line": "15",
    "column": "7"
  }
]
```

### Run Tests

```bash
# Run all tests (backend + frontend)
node tools/test.cjs

# What it does:
# ✓ Ensures dependencies are installed
# ✓ Runs Maven tests (backend)
# ✓ Runs npm tests (frontend)
# ✓ Reports aggregated results
```

**Output:**
```
[INFO] Starting test execution...
[INFO] Project root: /Users/tea/dev/github/planning-poker
[INFO] Ensuring dependencies are up-to-date...
[INFO] Dependencies are up-to-date
[INFO] Running backend tests...
[INFO] Running Maven tests in backend/
... test output ...
[INFO] Backend tests passed
[INFO] Running frontend tests...
[INFO] Running npm tests in frontend/
... test output ...
[INFO] Frontend tests passed
[INFO] All tests passed successfully
```

## Advanced Usage

### CI/CD Integration

#### GitHub Actions

```yaml
name: CI

on: [push, pull_request]

jobs:
  lint-and-test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'

      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Install dependencies
        run: node tools/install.cjs

      - name: Lint code
        run: |
          LINT_OUTPUT=$(node tools/lint.cjs)
          echo "$LINT_OUTPUT" | jq .
          ERROR_COUNT=$(echo "$LINT_OUTPUT" | jq 'length')
          if [ "$ERROR_COUNT" -gt 0 ]; then
            echo "Found $ERROR_COUNT lint error(s)"
            exit 1
          fi

      - name: Run tests
        run: node tools/test.cjs
```

#### GitLab CI

```yaml
stages:
  - install
  - lint
  - test

install-dependencies:
  stage: install
  script:
    - node tools/install.cjs
  artifacts:
    paths:
      - backend/target/
      - frontend/node_modules/

lint-code:
  stage: lint
  script:
    - node tools/lint.cjs > lint-results.json
    - cat lint-results.json | jq .
    - test $(cat lint-results.json | jq 'length') -eq 0
  artifacts:
    reports:
      codequality: lint-results.json

run-tests:
  stage: test
  script:
    - node tools/test.cjs
```

### Parse Lint Results

```bash
# Get lint results as JSON
node tools/lint.cjs 2>/dev/null > lint-results.json

# Count errors
jq 'length' lint-results.json

# Filter errors by file type
jq '[.[] | select(.path | endswith(".java"))]' lint-results.json
jq '[.[] | select(.path | endswith(".tsx"))]' lint-results.json

# Group errors by file
jq 'group_by(.path) | map({file: .[0].path, count: length})' lint-results.json

# Get only error messages
jq '.[].message' lint-results.json
```

### Selective Operations

```bash
# Install only (skip linting/testing)
node tools/install.cjs

# Lint only (uses cached dependencies)
node tools/lint.cjs

# Test only specific module
# Note: These scripts run both backend and frontend
# To test specific modules, use Maven/npm directly:
cd backend && mvn test
cd frontend && npm test
```

### Scripting with the Tools

#### Bash Script Example

```bash
#!/bin/bash
set -e

echo "==> Installing dependencies..."
node tools/install.cjs

echo "==> Linting code..."
if ! node tools/lint.cjs 2>/dev/null > lint-results.json; then
  echo "❌ Lint errors found:"
  jq '.' lint-results.json
  exit 1
fi

echo "✅ No lint errors"

echo "==> Running tests..."
if ! node tools/test.cjs; then
  echo "❌ Tests failed"
  exit 1
fi

echo "✅ All tests passed"

echo "==> Starting application..."
node tools/run.cjs
```

#### PowerShell Script Example

```powershell
# install-and-run.ps1

Write-Host "==> Installing dependencies..." -ForegroundColor Green
node tools/install.cjs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Linting code..." -ForegroundColor Green
$lintOutput = node tools/lint.cjs 2>$null | ConvertFrom-Json
if ($lintOutput.Count -gt 0) {
    Write-Host "❌ Found $($lintOutput.Count) lint error(s)" -ForegroundColor Red
    $lintOutput | ConvertTo-Json | Write-Host
    exit 1
}
Write-Host "✅ No lint errors" -ForegroundColor Green

Write-Host "==> Running tests..." -ForegroundColor Green
node tools/test.cjs
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Tests failed" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "✅ All tests passed" -ForegroundColor Green

Write-Host "==> Starting application..." -ForegroundColor Green
node tools/run.cjs
```

## Platform-Specific Examples

### Windows (PowerShell)

```powershell
# Install
node tools/install.cjs

# Run
node tools/run.cjs

# Lint (capture JSON)
$errors = node tools/lint.cjs 2>$null | ConvertFrom-Json
Write-Host "Found $($errors.Count) error(s)"

# Test
node tools/test.cjs
```

### Windows (Command Prompt)

```cmd
REM Install
node tools\install.cjs

REM Run
node tools\run.cjs

REM Lint
node tools\lint.cjs > lint-results.json 2>nul

REM Test
node tools\test.cjs
```

### macOS / Linux

```bash
# Make scripts executable (one-time)
chmod +x tools/*.cjs

# Install
./tools/install.cjs
# or
node tools/install.cjs

# Run
./tools/run.cjs

# Lint (suppress stderr)
./tools/lint.cjs 2>/dev/null | jq .

# Test
./tools/test.cjs
```

## Troubleshooting Examples

### Check Prerequisites

```bash
# Check Node.js
node --version  # Should be 18+

# Check Java
java -version   # Should be 17+

# Check Maven
mvn --version   # Should be 3.8+

# Check npm
npm --version
```

### Debug Mode

```bash
# See all output (including dependency installation)
node tools/lint.cjs

# See only JSON output
node tools/lint.cjs 2>/dev/null

# See only log messages
node tools/lint.cjs 2>&1 >/dev/null
```

### Force Reinstall

```bash
# Remove existing dependencies
rm -rf backend/target/
rm -rf frontend/node_modules/

# Reinstall
node tools/install.cjs
```

### Manual Dependency Check

```bash
# Check if dependencies are installed
ls backend/target/
ls frontend/node_modules/

# Check if package.json is newer than node_modules
stat -c %Y frontend/package.json  # Unix
stat -f %m frontend/package.json  # macOS
```

## Common Patterns

### Pre-commit Hook

```bash
#!/bin/bash
# .git/hooks/pre-commit

echo "Running lint before commit..."
if ! node tools/lint.cjs 2>/dev/null > /tmp/lint-results.json; then
  ERROR_COUNT=$(jq 'length' /tmp/lint-results.json)
  echo "❌ Cannot commit: Found $ERROR_COUNT lint error(s)"
  jq '.' /tmp/lint-results.json
  exit 1
fi

echo "✅ Lint passed"
exit 0
```

### Watch Mode (for development)

```bash
# Install fswatch or similar
# macOS: brew install fswatch
# Linux: apt-get install inotify-tools

# Watch for changes and re-lint
fswatch -o frontend/src backend/src/main/java | xargs -n1 -I{} node tools/lint.cjs 2>/dev/null | jq .
```

### Parallel Execution

```bash
# Run lint and test in parallel
node tools/lint.cjs 2>/dev/null > lint-results.json &
LINT_PID=$!

node tools/test.cjs &
TEST_PID=$!

# Wait for both
wait $LINT_PID
LINT_EXIT=$?

wait $TEST_PID
TEST_EXIT=$?

# Check results
if [ $LINT_EXIT -ne 0 ] || [ $TEST_EXIT -ne 0 ]; then
  echo "❌ Checks failed"
  exit 1
fi

echo "✅ All checks passed"
```

## Exit Code Reference

| Exit Code | Script | Meaning |
|-----------|--------|---------|
| 0 | All | Success |
| 1 | install.cjs | Installation failed |
| 1 | run.cjs | Application failed to start |
| 1 | test.cjs | Tests failed |
| 2 | lint.cjs | Lint errors found |

## Performance Tips

1. **Cached Dependencies:** The `install.cjs` script only reinstalls when needed
2. **Parallel CI Jobs:** Run lint and test in parallel in CI
3. **Skip Dependency Check:** For rapid iteration, use Maven/npm directly after initial install
4. **Incremental Builds:** Quarkus dev mode (`run.cjs`) supports hot-reload

## Next Steps

- Review [SCRIPTS_DOCUMENTATION.md](./SCRIPTS_DOCUMENTATION.md) for detailed technical documentation
- Check [README.md](./README.md) for quick reference
- See project root [README.md](../README.md) for application-specific documentation
