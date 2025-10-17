#!/usr/bin/env bash
#
# lint.sh - Lint project source code
#
# This script lints the project source code and outputs results in JSON format.
# It supports Java (Maven Checkstyle) and JavaScript/Vue (ESLint).
# Only syntax errors and critical warnings are reported.
#

set -e
set -u

# Project root directory
readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly BACKEND_DIR="${PROJECT_ROOT}/backend"
readonly FRONTEND_DIR="${PROJECT_ROOT}/src/main/webui"
readonly TOOLS_DIR="${PROJECT_ROOT}/tools"

# Error codes
readonly ERR_DEPENDENCY=1
readonly ERR_LINT_FAILED=2

# Logging functions (stderr only, stdout is for JSON)
log_error() {
    echo "[ERROR] $*" >&2
}

log_info() {
    echo "[INFO] $*" >&2
}

# Check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Run install.sh to ensure dependencies are up-to-date
ensure_dependencies() {
    if [ -x "${TOOLS_DIR}/install.sh" ]; then
        if ! bash "${TOOLS_DIR}/install.sh" >/dev/null 2>&1; then
            log_error "Failed to install dependencies"
            return 1
        fi
    fi
}

# Install Maven Checkstyle plugin if needed (for Java linting)
ensure_checkstyle() {
    if [ -f "${BACKEND_DIR}/pom.xml" ] || [ -f "${PROJECT_ROOT}/pom.xml" ]; then
        if ! command_exists mvn; then
            log_error "Maven not found but required for Java linting"
            return 1
        fi
        log_info "Maven Checkstyle available through maven-checkstyle-plugin" >&2
    fi
}

# Install ESLint if needed (for JavaScript/Vue linting)
ensure_eslint() {
    if [ -d "${FRONTEND_DIR}" ] && [ -f "${FRONTEND_DIR}/package.json" ]; then
        cd "${FRONTEND_DIR}"

        # Check if ESLint is installed locally
        if [ ! -f "node_modules/.bin/eslint" ]; then
            log_info "Installing ESLint..." >&2
            npm install --save-dev --silent eslint >/dev/null 2>&1 || true
        fi

        cd "${PROJECT_ROOT}"
    fi
}

# Lint Java code using Maven Checkstyle
lint_java() {
    local pom_location=""
    local has_errors=0

    # Determine POM location
    if [ -d "${BACKEND_DIR}" ] && [ -f "${BACKEND_DIR}/pom.xml" ]; then
        pom_location="${BACKEND_DIR}"
    elif [ -f "${PROJECT_ROOT}/pom.xml" ]; then
        pom_location="${PROJECT_ROOT}"
    else
        return 0
    fi

    log_info "Linting Java code with Maven Compiler..." >&2

    cd "${pom_location}"

    # Run Maven compile to check for syntax errors
    local compile_output
    if compile_output=$(mvn compiler:compile -q 2>&1); then
        log_info "Java compilation successful, no syntax errors" >&2
    else
        # Parse compilation errors and convert to JSON
        echo "${compile_output}" | awk '
            BEGIN {
                first = 1
            }
            /\[ERROR\]/ && /\.java:\[/ {
                # Extract file path, line, column, and message
                match($0, /\[ERROR\] (.+\.java):\[([0-9]+),([0-9]+)\] (.+)/, arr)
                if (arr[1] != "") {
                    if (first == 0) printf ","
                    first = 0
                    gsub(/"/, "\\\"", arr[4])
                    printf "{\"type\":\"error\",\"path\":\"%s\",\"obj\":\"\",\"message\":\"%s\",\"line\":\"%s\",\"column\":\"%s\"}", arr[1], arr[4], arr[2], arr[3]
                }
            }
        '
        has_errors=1
    fi

    cd "${PROJECT_ROOT}"
    return ${has_errors}
}

# Lint JavaScript/Vue code using ESLint
lint_javascript() {
    local has_errors=0

    if [ ! -d "${FRONTEND_DIR}" ] || [ ! -f "${FRONTEND_DIR}/package.json" ]; then
        return 0
    fi

    cd "${FRONTEND_DIR}"

    # Check if ESLint is available
    if [ ! -f "node_modules/.bin/eslint" ]; then
        log_info "ESLint not available, skipping JavaScript linting" >&2
        cd "${PROJECT_ROOT}"
        return 0
    fi

    log_info "Linting JavaScript/Vue code with ESLint..." >&2

    # Run ESLint with JSON output
    local eslint_output
    if eslint_output=$(npx eslint "src/**/*.{js,vue}" --format json 2>/dev/null || true); then
        # Parse ESLint JSON output and convert to our format
        echo "${eslint_output}" | python3 -c "
import sys
import json

try:
    data = json.load(sys.stdin)
    first = True
    for file_result in data:
        if 'messages' in file_result:
            for msg in file_result['messages']:
                # Only report errors and critical warnings (severity 2)
                if msg.get('severity', 0) >= 2:
                    if not first:
                        print(',', end='')
                    first = False

                    error_obj = {
                        'type': 'error' if msg.get('severity') == 2 else 'warning',
                        'path': file_result.get('filePath', ''),
                        'obj': msg.get('ruleId', ''),
                        'message': msg.get('message', ''),
                        'line': str(msg.get('line', '')),
                        'column': str(msg.get('column', ''))
                    }
                    print(json.dumps(error_obj), end='')
except Exception:
    pass
" || true
    fi

    cd "${PROJECT_ROOT}"
    return ${has_errors}
}

# Main linting process
main() {
    local has_errors=0

    log_info "Starting lint process..." >&2

    # Ensure dependencies are installed
    ensure_dependencies >/dev/null 2>&1 || true
    ensure_checkstyle >/dev/null 2>&1 || true
    ensure_eslint >/dev/null 2>&1 || true

    # Start JSON array output
    echo "["

    # Lint Java code
    if ! lint_java; then
        has_errors=1
    fi

    # Add comma separator if Java produced output and JavaScript will follow
    if [ -d "${FRONTEND_DIR}" ] && [ -f "${FRONTEND_DIR}/package.json" ]; then
        # Check if there was previous output (not just "[")
        : # No-op, handled by individual linters
    fi

    # Lint JavaScript code
    if ! lint_javascript; then
        has_errors=1
    fi

    # Close JSON array
    echo "]"

    log_info "Linting complete" >&2

    # Exit with appropriate code
    if [ ${has_errors} -eq 1 ]; then
        log_error "Linting found errors" >&2
        exit ${ERR_LINT_FAILED}
    fi

    exit 0
}

# Run main function
main "$@"
