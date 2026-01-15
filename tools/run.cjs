#!/usr/bin/env node

/**
 * run.cjs - Cross-platform project execution script
 *
 * This script ensures dependencies are up-to-date and then runs the main application.
 * For Quarkus projects, it runs the backend in dev mode.
 *
 * Supports: Windows, macOS, Linux
 */

const { execSync, spawn } = require('child_process');
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
 * Run backend (Quarkus in dev mode)
 */
function runBackend() {
  logInfo('Starting backend application...');

  if (!commandExists('mvn')) {
    logError('Maven (mvn) not found. Please install Maven to run the backend.');
    process.exit(1);
  }

  // Determine Maven project location
  let pomLocation = null;
  if (fs.existsSync(BACKEND_DIR) && fs.existsSync(path.join(BACKEND_DIR, 'pom.xml'))) {
    pomLocation = BACKEND_DIR;
    logInfo('Running Quarkus backend in dev mode from backend/');
  } else if (fs.existsSync(path.join(PROJECT_ROOT, 'pom.xml'))) {
    pomLocation = PROJECT_ROOT;
    logInfo('Running Quarkus backend in dev mode from root');
  } else {
    logError('No Maven project found (no pom.xml)');
    process.exit(1);
  }

  // Use spawn to run Maven and inherit stdio for interactive mode
  const mvnCmd = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';
  const child = spawn(mvnCmd, ['quarkus:dev'], {
    cwd: pomLocation,
    stdio: 'inherit',
    shell: true,
  });

  child.on('error', (error) => {
    logError(`Failed to start backend: ${error.message}`);
    process.exit(1);
  });

  child.on('exit', (code) => {
    if (code !== 0) {
      logError(`Backend exited with code ${code}`);
      process.exit(code || 1);
    }
    process.exit(0);
  });

  // Handle termination signals
  process.on('SIGINT', () => {
    child.kill('SIGINT');
  });

  process.on('SIGTERM', () => {
    child.kill('SIGTERM');
  });
}

/**
 * Main execution
 */
function main() {
  logInfo('Starting application...');
  logInfo(`Project root: ${PROJECT_ROOT}`);

  // Ensure all dependencies are installed
  if (!ensureDependencies()) {
    logError('Dependency check failed');
    process.exit(1);
  }

  // Run the backend
  runBackend();
}

// Run main function
main();
