#!/usr/bin/env bash
#
# install.sh - Environment setup and dependency installation
#
# This script ensures all dependencies are installed and up-to-date for both
# the backend (Maven/Java) and frontend (npm/Node.js) components.
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

# Install backend dependencies (Maven/Java)
install_backend() {
    log_info "Installing backend dependencies..."

    # Check if Maven is available
    if ! command_exists mvn; then
        log_error "Maven (mvn) not found. Please install Maven 3.8+ to continue."
        return 1
    fi

    # Check if Java is available
    if ! command_exists java; then
        log_error "Java not found. Please install Java 17+ to continue."
        return 1
    fi

    # Verify Java version
    local java_version
    java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "${java_version}" -lt 17 ]; then
        log_error "Java 17+ is required. Current version: ${java_version}"
        return 1
    fi

    # Navigate to backend directory if it exists
    if [ -d "${BACKEND_DIR}" ]; then
        cd "${BACKEND_DIR}"
        log_info "Installing Maven dependencies in backend/"
        mvn dependency:resolve dependency:resolve-plugins -q
        log_info "Backend dependencies installed successfully"
        cd "${PROJECT_ROOT}"
    else
        # Fall back to root pom.xml if backend directory doesn't exist
        if [ -f "${PROJECT_ROOT}/pom.xml" ]; then
            log_info "Installing Maven dependencies from root pom.xml"
            cd "${PROJECT_ROOT}"
            mvn dependency:resolve dependency:resolve-plugins -q
            log_info "Maven dependencies installed successfully"
        else
            log_warn "No Maven project found (no pom.xml)"
        fi
    fi
}

# Install frontend dependencies (npm/Node.js)
install_frontend() {
    log_info "Installing frontend dependencies..."

    # Check if npm is available
    if ! command_exists npm; then
        log_error "npm not found. Please install Node.js 18+ with npm to continue."
        return 1
    fi

    # Check if Node.js is available
    if ! command_exists node; then
        log_error "Node.js not found. Please install Node.js 18+ to continue."
        return 1
    fi

    # Navigate to frontend directory
    if [ -d "${FRONTEND_DIR}" ] && [ -f "${FRONTEND_DIR}/package.json" ]; then
        cd "${FRONTEND_DIR}"
        log_info "Installing npm dependencies in src/main/webui/"

        # Check if node_modules exists and package.json has changed
        if [ ! -d "node_modules" ]; then
            log_info "node_modules not found, running npm install..."
            npm install --silent
        else
            # Check if package.json is newer than node_modules
            if [ "package.json" -nt "node_modules" ]; then
                log_info "package.json has been modified, updating dependencies..."
                npm install --silent
            else
                log_info "node_modules is up-to-date"
            fi
        fi

        log_info "Frontend dependencies installed successfully"
        cd "${PROJECT_ROOT}"
    else
        log_warn "No frontend project found (no src/main/webui/package.json)"
    fi
}

# Main installation process
main() {
    log_info "Starting dependency installation..."
    log_info "Project root: ${PROJECT_ROOT}"

    # Install backend dependencies
    if ! install_backend; then
        log_error "Backend dependency installation failed"
        exit 1
    fi

    # Install frontend dependencies
    if ! install_frontend; then
        log_error "Frontend dependency installation failed"
        exit 1
    fi

    log_info "All dependencies installed successfully"
    exit 0
}

# Run main function
main "$@"
