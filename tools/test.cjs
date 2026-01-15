#!/usr/bin/env node

/**
 * test.cjs - Cross-platform test execution script
 *
 * This script ensures dependencies are up-to-date and then runs all project tests.
 * For Maven projects, it runs unit tests.
 * For npm projects, it runs the test script defined in package.json.
 *
 * Supports: Windows, macOS, Linux
 */

const { execSync } = require('child_process');
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
const TOOLS_DIR = path.join(PROJECT_ROOT, 'tools');

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
 * Run install.cjs to ensure dependencies are up-to-date
 * @returns {boolean} - True if successful
 */
function ensureDependencies() {
  logInfo('Ensuring dependencies are up-to-date...');

  const installScript = path.join(TOOLS_DIR, 'install.cjs');

  if (!fs.existsSync(installScript)) {
    logWarn('install.cjs not found, skipping dependency check');
    return true;
  }

  try {
    execSync(`node "${installScript}"`, {
      stdio: 'inherit',
    });
    logInfo('Dependencies are up-to-date');
    return true;
  } catch (error) {
    logError('Failed to install dependencies');
    return false;
  }
}

/**
 * Run backend tests (Maven)
 * @returns {boolean} - True if tests passed
 */
function testBackend() {
  logInfo('Running backend tests...');

  if (!commandExists('mvn')) {
    logError('Maven (mvn) not found. Please install Maven to run backend tests.');
    return false;
  }

  // Determine Maven project location
  let pomLocation = null;
  if (fs.existsSync(BACKEND_DIR) && fs.existsSync(path.join(BACKEND_DIR, 'pom.xml'))) {
    pomLocation = BACKEND_DIR;
    logInfo('Running Maven tests in backend/');
  } else if (fs.existsSync(path.join(PROJECT_ROOT, 'pom.xml'))) {
    pomLocation = PROJECT_ROOT;
    logInfo('Running Maven tests from root');
  } else {
    logWarn('No Maven project found (no pom.xml), skipping backend tests');
    return true; // Not an error, just no Maven project
  }

  try {
    const mvnCmd = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';
    execSync(`${mvnCmd} test`, {
      cwd: pomLocation,
      stdio: 'inherit',
    });
    logInfo('Backend tests passed');
    return true;
  } catch (error) {
    logError('Backend tests failed');
    return false;
  }
}

/**
 * Run frontend tests (npm)
 * @returns {boolean} - True if tests passed
 */
function testFrontend() {
  logInfo('Running frontend tests...');

  if (!commandExists('npm')) {
    logWarn('npm not found, skipping frontend tests');
    return true; // Not an error, just no npm
  }

  const packageJsonPath = path.join(FRONTEND_DIR, 'package.json');
  if (!fs.existsSync(FRONTEND_DIR) || !fs.existsSync(packageJsonPath)) {
    logWarn('No frontend project found (no frontend/package.json), skipping frontend tests');
    return true; // Not an error, just no frontend project
  }

  logInfo('Running npm tests in frontend/');

  try {
    // Check if test script exists in package.json
    const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));

    if (!packageJson.scripts || !packageJson.scripts.test) {
      logWarn('No test script found in package.json, skipping frontend tests');
      return true;
    }

    execSync('npm test', {
      cwd: FRONTEND_DIR,
      stdio: 'inherit',
    });
    logInfo('Frontend tests passed');
    return true;
  } catch (error) {
    logError('Frontend tests failed');
    return false;
  }
}

/**
 * Main test execution
 */
function main() {
  let exitCode = 0;

  logInfo('Starting test execution...');
  logInfo(`Project root: ${PROJECT_ROOT}`);

  // Ensure all dependencies are installed
  if (!ensureDependencies()) {
    logError('Dependency check failed');
    process.exit(1);
  }

  // Run backend tests
  if (!testBackend()) {
    exitCode = 1;
  }

  // Run frontend tests
  if (!testFrontend()) {
    exitCode = 1;
  }

  // Final summary
  if (exitCode === 0) {
    logInfo('All tests passed successfully');
  } else {
    logError('Some tests failed');
  }

  process.exit(exitCode);
}

// Run main function
main();
