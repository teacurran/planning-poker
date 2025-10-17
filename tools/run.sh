#!/usr/bin/env bash
#
# run.sh - Run the project application
#
# This script ensures dependencies are up-to-date and then runs the main application.
# For Quarkus projects, it runs the backend in dev mode.
# For projects with frontend, it can optionally start the frontend dev server.
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

# Run backend (Quarkus in dev mode)
run_backend() {
    log_info "Starting backend application..."

    if ! command_exists mvn; then
        log_error "Maven (mvn) not found. Please install Maven to run the backend."
        return 1
    fi

    # Navigate to backend directory if it exists
    if [ -d "${BACKEND_DIR}" ] && [ -f "${BACKEND_DIR}/pom.xml" ]; then
        cd "${BACKEND_DIR}"
        log_info "Running Quarkus backend in dev mode from backend/"
        exec mvn quarkus:dev
    elif [ -f "${PROJECT_ROOT}/pom.xml" ]; then
        # Fall back to root pom.xml if backend directory doesn't exist
        cd "${PROJECT_ROOT}"
        log_info "Running Quarkus backend in dev mode from root"
        exec mvn quarkus:dev
    else
        log_error "No Maven project found (no pom.xml)"
        return 1
    fi
}

# Main execution
main() {
    log_info "Starting application..."
    log_info "Project root: ${PROJECT_ROOT}"

    # Ensure all dependencies are installed
    if ! ensure_dependencies; then
        log_error "Dependency check failed"
        exit 1
    fi

    # Run the backend
    if ! run_backend; then
        log_error "Failed to start backend"
        exit 1
    fi
}

# Run main function
main "$@"
