#!/usr/bin/env bash
#
# test.sh - Run project tests
#
# This script ensures dependencies are up-to-date and then runs all project tests.
# For Maven projects, it runs unit tests and integration tests.
# For npm projects, it runs the test script defined in package.json.
#

set -e
set -u

# Color codes for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly NC='\033[0m' # No Color

# Project root directory
readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly BACKEND_DIR="${PROJECT_ROOT}/backend"
readonly FRONTEND_DIR="${PROJECT_ROOT}/src/main/webui"
readonly TOOLS_DIR="${PROJECT_ROOT}/tools"

# Logging functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $*" >&2
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

# Check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Run install.sh to ensure dependencies are up-to-date
ensure_dependencies() {
    log_info "Ensuring dependencies are up-to-date..."

    if [ -x "${TOOLS_DIR}/install.sh" ]; then
        if ! "${TOOLS_DIR}/install.sh" >/dev/null 2>&1; then
            log_error "Failed to install dependencies"
            return 1
        fi
        log_info "Dependencies are up-to-date"
    else
        log_warn "install.sh not found or not executable, skipping dependency check"
    fi
}

# Run backend tests (Maven)
test_backend() {
    local test_failed=0

    log_info "Running backend tests..."

    if ! command_exists mvn; then
        log_error "Maven (mvn) not found. Please install Maven to run backend tests."
        return 1
    fi

    # Navigate to backend directory if it exists
    if [ -d "${BACKEND_DIR}" ] && [ -f "${BACKEND_DIR}/pom.xml" ]; then
        cd "${BACKEND_DIR}"
        log_info "Running Maven tests in backend/"

        # Run Maven test phase (includes unit tests)
        if ! mvn test; then
            log_error "Backend tests failed"
            test_failed=1
        else
            log_info "Backend tests passed"
        fi

        cd "${PROJECT_ROOT}"
    elif [ -f "${PROJECT_ROOT}/pom.xml" ]; then
        # Fall back to root pom.xml if backend directory doesn't exist
        cd "${PROJECT_ROOT}"
        log_info "Running Maven tests from root"

        # Run Maven test phase (includes unit tests)
        if ! mvn test; then
            log_error "Backend tests failed"
            test_failed=1
        else
            log_info "Backend tests passed"
        fi
    else
        log_warn "No Maven project found (no pom.xml), skipping backend tests"
    fi

    return ${test_failed}
}

# Run frontend tests (npm)
test_frontend() {
    local test_failed=0

    log_info "Running frontend tests..."

    # Check if npm is available
    if ! command_exists npm; then
        log_warn "npm not found, skipping frontend tests"
        return 0
    fi

    # Navigate to frontend directory
    if [ -d "${FRONTEND_DIR}" ] && [ -f "${FRONTEND_DIR}/package.json" ]; then
        cd "${FRONTEND_DIR}"
        log_info "Running npm tests in src/main/webui/"

        # Check if test script exists in package.json
        if grep -q '"test"' package.json; then
            if ! npm test; then
                log_error "Frontend tests failed"
                test_failed=1
            else
                log_info "Frontend tests passed"
            fi
        else
            log_warn "No test script found in package.json, skipping frontend tests"
        fi

        cd "${PROJECT_ROOT}"
    else
        log_warn "No frontend project found (no src/main/webui/package.json), skipping frontend tests"
    fi

    return ${test_failed}
}

# Main test execution
main() {
    local exit_code=0

    log_info "Starting test execution..."
    log_info "Project root: ${PROJECT_ROOT}"

    # Ensure all dependencies are installed
    if ! ensure_dependencies; then
        log_error "Dependency check failed"
        exit 1
    fi

    # Run backend tests
    if ! test_backend; then
        exit_code=1
    fi

    # Run frontend tests
    if ! test_frontend; then
        exit_code=1
    fi

    # Final summary
    if [ ${exit_code} -eq 0 ]; then
        log_info "All tests passed successfully"
    else
        log_error "Some tests failed"
    fi

    exit ${exit_code}
}

# Run main function
main "$@"
