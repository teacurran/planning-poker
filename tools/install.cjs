#!/usr/bin/env node

/**
 * install.cjs - Cross-platform environment setup and dependency installation
 *
 * This script ensures all dependencies are installed and up-to-date for both
 * the backend (Maven/Java) and frontend (npm/Node.js) components.
 *
 * Supports: Windows, macOS, Linux
 */

const { execSync, spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

// ANSI color codes for output
const colors = {
  RED: '\x1b[0;31m',
  GREEN: '\x1b[0;32m',
  YELLOW: '\x1b[1;33m',
  NC: '\x1b[0m', // No Color
};

// Project directories
const PROJECT_ROOT = path.resolve(__dirname, '..');
const BACKEND_DIR = path.join(PROJECT_ROOT, 'backend');
const FRONTEND_DIR = path.join(PROJECT_ROOT, 'frontend');

// Logging functions
function logInfo(message) {
  console.error(`${colors.GREEN}[INFO]${colors.NC} ${message}`);
}

function logWarn(message) {
  console.error(`${colors.YELLOW}[WARN]${colors.NC} ${message}`);
}

function logError(message) {
  console.error(`${colors.RED}[ERROR]${colors.NC} ${message}`);
}

/**
 * Check if a command exists on the system
 * @param {string} command - Command name to check
 * @returns {boolean} - True if command exists
 */
function commandExists(command) {
  try {
    const checkCmd = process.platform === 'win32' ? 'where' : 'which';
    execSync(`${checkCmd} ${command}`, { stdio: 'ignore' });
    return true;
  } catch (error) {
    return false;
  }
}

/**
 * Get Java version
 * @returns {number|null} - Java major version or null if not found
 */
function getJavaVersion() {
  try {
    const output = execSync('java -version 2>&1', { encoding: 'utf8' });
    const match = output.match(/version "?([0-9]+)/);
    if (match) {
      return parseInt(match[1], 10);
    }
  } catch (error) {
    // Java not found
  }
  return null;
}

/**
 * Install backend dependencies (Maven/Java)
 * @returns {boolean} - True if successful
 */
function installBackend() {
  logInfo('Installing backend dependencies...');

  // Check if Maven is available
  if (!commandExists('mvn')) {
    logError('Maven (mvn) not found. Please install Maven 3.8+ to continue.');
    return false;
  }

  // Check if Java is available
  if (!commandExists('java')) {
    logError('Java not found. Please install Java 17+ to continue.');
    return false;
  }

  // Verify Java version
  const javaVersion = getJavaVersion();
  if (javaVersion === null || javaVersion < 17) {
    logError(`Java 17+ is required. Current version: ${javaVersion || 'unknown'}`);
    return false;
  }

  // Determine Maven project location
  let pomLocation = null;
  if (fs.existsSync(BACKEND_DIR) && fs.existsSync(path.join(BACKEND_DIR, 'pom.xml'))) {
    pomLocation = BACKEND_DIR;
    logInfo('Installing Maven dependencies in backend/');
  } else if (fs.existsSync(path.join(PROJECT_ROOT, 'pom.xml'))) {
    pomLocation = PROJECT_ROOT;
    logInfo('Installing Maven dependencies from root pom.xml');
  } else {
    logWarn('No Maven project found (no pom.xml)');
    return true; // Not an error, just no Maven project
  }

  try {
    // Run Maven dependency resolution
    execSync('mvn dependency:resolve dependency:resolve-plugins -q', {
      cwd: pomLocation,
      stdio: 'inherit',
    });
    logInfo('Backend dependencies installed successfully');
    return true;
  } catch (error) {
    logError('Failed to install Maven dependencies');
    return false;
  }
}

/**
 * Install frontend dependencies (npm/Node.js)
 * @returns {boolean} - True if successful
 */
function installFrontend() {
  logInfo('Installing frontend dependencies...');

  // Check if npm is available
  if (!commandExists('npm')) {
    logError('npm not found. Please install Node.js 18+ with npm to continue.');
    return false;
  }

  // Check if Node.js is available
  if (!commandExists('node')) {
    logError('Node.js not found. Please install Node.js 18+ to continue.');
    return false;
  }

  // Check if frontend project exists
  const packageJsonPath = path.join(FRONTEND_DIR, 'package.json');
  if (!fs.existsSync(FRONTEND_DIR) || !fs.existsSync(packageJsonPath)) {
    logWarn('No frontend project found (no frontend/package.json)');
    return true; // Not an error, just no frontend project
  }

  logInfo('Installing npm dependencies in frontend/');

  const nodeModulesPath = path.join(FRONTEND_DIR, 'node_modules');

  try {
    // Check if node_modules exists
    if (!fs.existsSync(nodeModulesPath)) {
      logInfo('node_modules not found, running npm install...');
      execSync('npm install --silent', {
        cwd: FRONTEND_DIR,
        stdio: 'inherit',
      });
    } else {
      // Check if package.json is newer than node_modules
      const packageJsonStat = fs.statSync(packageJsonPath);
      const nodeModulesStat = fs.statSync(nodeModulesPath);

      if (packageJsonStat.mtime > nodeModulesStat.mtime) {
        logInfo('package.json has been modified, updating dependencies...');
        execSync('npm install --silent', {
          cwd: FRONTEND_DIR,
          stdio: 'inherit',
        });
      } else {
        logInfo('node_modules is up-to-date');
      }
    }

    logInfo('Frontend dependencies installed successfully');
    return true;
  } catch (error) {
    logError('Failed to install npm dependencies');
    return false;
  }
}

/**
 * Main installation process
 */
function main() {
  logInfo('Starting dependency installation...');
  logInfo(`Project root: ${PROJECT_ROOT}`);

  // Install backend dependencies
  if (!installBackend()) {
    logError('Backend dependency installation failed');
    process.exit(1);
  }

  // Install frontend dependencies
  if (!installFrontend()) {
    logError('Frontend dependency installation failed');
    process.exit(1);
  }

  logInfo('All dependencies installed successfully');
  process.exit(0);
}

// Run main function
main();
