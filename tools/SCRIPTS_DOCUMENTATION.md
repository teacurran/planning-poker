# Automation Scripts Documentation

This directory contains cross-platform Node.js automation scripts for the Planning Poker project.

## Overview

Four CommonJS scripts (`.cjs`) have been generated to automate project tasks:

1. **install.cjs** - Environment setup and dependency installation
2. **run.cjs** - Project execution
3. **lint.cjs** - Code linting with JSON output
4. **test.cjs** - Test execution

## Prerequisites

- **Node.js** 18+ (required for all scripts)
- **Java** 17+ (for backend)
- **Maven** 3.8+ (for backend)
- **npm** (for frontend)

## Scripts

### 1. install.cjs

**Purpose:** Single source of truth for environment setup and dependency installation.

**Features:**
- Detects and validates Java/Maven for backend
- Installs Maven dependencies from `backend/pom.xml` or root `pom.xml`
- Installs npm dependencies in `frontend/` directory
- Idempotent: re-running ensures dependencies are up-to-date
- Smart detection: only reinstalls when `package.json` is newer than `node_modules`

**Usage:**
```bash
node tools/install.cjs
# or
./tools/install.cjs
```

**Exit Codes:**
- `0` - Success
- `1` - Failure (missing tools or installation errors)

---

### 2. run.cjs

**Purpose:** Run the main project application (Quarkus backend in dev mode).

**Features:**
- Automatically runs `install.cjs` to ensure dependencies are current
- Detects Maven project location (`backend/pom.xml` or root `pom.xml`)
- Launches Quarkus in development mode with hot-reload
- Properly handles process signals (SIGINT, SIGTERM)

**Usage:**
```bash
node tools/run.cjs
# or
./tools/run.cjs
```

**Exit Codes:**
- `0` - Success
- `1` - Failure (missing tools, dependency errors, or application errors)

**Note:** The application will run until manually terminated (Ctrl+C).

---

### 3. lint.cjs

**Purpose:** Lint project source code and output results in JSON format.

**Features:**
- Automatically runs `install.cjs` silently to ensure dependencies
- **Java Linting:** Uses Maven Compiler to detect syntax errors
- **TypeScript/React Linting:** Uses ESLint with existing configuration
- Auto-installs ESLint if not present in frontend
- Reports only errors (severity 2)
- **JSON Output:** All errors output to stdout in JSON array format
- **Logging:** Progress messages go to stderr (not stdout)

**Usage:**
```bash
node tools/lint.cjs
# or
./tools/lint.cjs
```

**Output Format:**
```json
[
  {
    "type": "error",
    "path": "/path/to/file.java",
    "obj": "",
    "message": "error message",
    "line": "42",
    "column": "10"
  },
  {
    "type": "error",
    "path": "/path/to/file.tsx",
    "obj": "no-unused-vars",
    "message": "'foo' is defined but never used",
    "line": "15",
    "column": "7"
  }
]
```

**Exit Codes:**
- `0` - No lint errors found
- `2` - Lint errors found

---

### 4. test.cjs

**Purpose:** Run all project tests (backend Maven tests and frontend npm tests).

**Features:**
- Automatically runs `install.cjs` to ensure dependencies are current
- **Backend Tests:** Runs `mvn test` from `backend/` or root
- **Frontend Tests:** Runs `npm test` from `frontend/` if test script exists
- Aggregates results from both test suites
- Gracefully skips test suites when not applicable

**Usage:**
```bash
node tools/test.cjs
# or
./tools/test.cjs
```

**Exit Codes:**
- `0` - All tests passed
- `1` - One or more tests failed

---

## Cross-Platform Compatibility

All scripts are designed to work on **Windows**, **macOS**, and **Linux**:

### Platform-Specific Handling

1. **Command Detection:**
   - Windows: uses `where` command
   - Unix: uses `which` command

2. **Path Handling:**
   - Uses `path.join()` for all path operations
   - Properly handles Windows backslashes and Unix forward slashes

3. **Command Execution:**
   - Windows: automatically appends `.cmd` to Maven commands when needed
   - Unix: uses standard command names

4. **File Permissions:**
   - Scripts include shebang (`#!/usr/bin/env node`) for Unix
   - Scripts are made executable with `chmod +x` on Unix
   - Can be run with `node script.cjs` on any platform

## Project Structure Detection

The scripts intelligently detect the project structure:

### Backend Detection
```
Checks in order:
1. backend/pom.xml (preferred)
2. pom.xml in root (fallback)
```

### Frontend Detection
```
Checks:
1. frontend/package.json
```

## Error Handling

All scripts implement robust error handling:

- **Prerequisite Checks:** Validates required tools before execution
- **Version Validation:** Ensures Java 17+ is installed
- **Graceful Degradation:** Skips optional components if not present
- **Clear Error Messages:** Color-coded output (green=info, yellow=warn, red=error)
- **Proper Exit Codes:** Consistent exit codes for CI/CD integration

## Integration with CI/CD

These scripts are designed for CI/CD pipeline integration:

### Example GitHub Actions Workflow

```yaml
name: CI

on: [push, pull_request]

jobs:
  build:
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
        run: node tools/lint.cjs

      - name: Run tests
        run: node tools/test.cjs
```

## Migration from Bash Scripts

The original bash scripts (`.sh` files) are still present for backward compatibility:
- `install.sh`
- `run.sh`
- `lint.sh`
- `test.sh`

The new `.cjs` scripts provide:
- Better Windows compatibility
- More robust error handling
- Consistent behavior across platforms
- Easier maintenance (single language: JavaScript)

## Troubleshooting

### Script won't execute
```bash
# Make sure Node.js is installed
node --version

# Make script executable (Unix/macOS/Linux)
chmod +x tools/*.cjs
```

### Maven not found
```bash
# Install Maven 3.8+
# macOS: brew install maven
# Linux: apt-get install maven
# Windows: Download from https://maven.apache.org/
```

### ESLint errors in frontend
```bash
# The script will auto-install ESLint if needed
# Or manually install:
cd frontend
npm install --save-dev eslint
```

### Java version issues
```bash
# Check Java version
java -version

# Install Java 17+ if needed
# Use SDKMAN: sdk install java 17.0.9-tem
# Or download from https://adoptium.net/
```

## Development

### Modifying Scripts

When modifying these scripts:
1. Maintain cross-platform compatibility
2. Test on Windows, macOS, and Linux
3. Use `path.join()` for paths
4. Use `process.platform` checks when needed
5. Keep error messages clear and actionable
6. Preserve exit code conventions

### Testing Changes

```bash
# Test install
node tools/install.cjs

# Test lint
node tools/lint.cjs

# Test run (will start server)
node tools/run.cjs

# Test tests
node tools/test.cjs
```

## License

Same as parent project.
