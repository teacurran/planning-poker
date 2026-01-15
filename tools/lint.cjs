#!/usr/bin/env node

/**
 * lint.cjs - Cross-platform linting script
 *
 * This script lints the project source code and outputs results in JSON format.
 * It supports Java (Maven Compiler) and TypeScript/React (ESLint).
 * Only syntax errors and critical warnings are reported.
 *
 * Supports: Windows, macOS, Linux
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

// Project directories
const PROJECT_ROOT = path.resolve(__dirname, '..');
const BACKEND_DIR = path.join(PROJECT_ROOT, 'backend');
const FRONTEND_DIR = path.join(PROJECT_ROOT, 'frontend');
const TOOLS_DIR = path.join(PROJECT_ROOT, 'tools');

// Exit codes
const ERR_LINT_FAILED = 2;

/**
 * Log to stderr (stdout is reserved for JSON)
 * @param {string} message - Message to log
 */
function logError(message) {
  console.error(`[ERROR] ${message}`);
}

function logInfo(message) {
  console.error(`[INFO] ${message}`);
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
 */
function ensureDependencies() {
  const installScript = path.join(TOOLS_DIR, 'install.cjs');

  if (fs.existsSync(installScript)) {
    try {
      execSync(`node "${installScript}"`, { stdio: 'ignore' });
    } catch (error) {
      logError('Failed to install dependencies');
    }
  }
}

/**
 * Ensure ESLint is installed in the frontend project
 */
function ensureEslint() {
  const packageJsonPath = path.join(FRONTEND_DIR, 'package.json');

  if (!fs.existsSync(FRONTEND_DIR) || !fs.existsSync(packageJsonPath)) {
    return;
  }

  const eslintPath = path.join(FRONTEND_DIR, 'node_modules', '.bin', 'eslint');
  const eslintCmdPath = path.join(FRONTEND_DIR, 'node_modules', '.bin', 'eslint.cmd');

  // Check if ESLint is installed
  if (!fs.existsSync(eslintPath) && !fs.existsSync(eslintCmdPath)) {
    logInfo('Installing ESLint...');
    try {
      execSync('npm install --save-dev eslint', {
        cwd: FRONTEND_DIR,
        stdio: 'ignore',
      });
    } catch (error) {
      logError('Failed to install ESLint');
    }
  }
}

/**
 * Lint Java code using Maven Compiler
 * @returns {Array<Object>} - Array of error objects
 */
function lintJava() {
  const errors = [];

  // Determine POM location
  let pomLocation = null;
  if (fs.existsSync(BACKEND_DIR) && fs.existsSync(path.join(BACKEND_DIR, 'pom.xml'))) {
    pomLocation = BACKEND_DIR;
  } else if (fs.existsSync(path.join(PROJECT_ROOT, 'pom.xml'))) {
    pomLocation = PROJECT_ROOT;
  } else {
    return errors; // No Maven project
  }

  if (!commandExists('mvn')) {
    logError('Maven not found but required for Java linting');
    return errors;
  }

  logInfo('Linting Java code with Maven Compiler...');

  try {
    // Run Maven compile to check for syntax errors
    const mvnCmd = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';
    execSync(`${mvnCmd} compiler:compile -q`, {
      cwd: pomLocation,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    logInfo('Java compilation successful, no syntax errors');
  } catch (error) {
    // Parse compilation errors
    const output = error.stderr || error.stdout || '';
    const lines = output.split('\n');

    for (const line of lines) {
      // Match error pattern: [ERROR] /path/to/File.java:[line,column] message
      const match = line.match(/\[ERROR\]\s+(.+\.java):\[(\d+),(\d+)\]\s+(.+)/);
      if (match) {
        errors.push({
          type: 'error',
          path: match[1],
          obj: '',
          message: match[4].trim(),
          line: match[2],
          column: match[3],
        });
      }
    }
  }

  return errors;
}

/**
 * Lint TypeScript/React code using ESLint
 * @returns {Array<Object>} - Array of error objects
 */
function lintJavaScript() {
  const errors = [];

  const packageJsonPath = path.join(FRONTEND_DIR, 'package.json');
  if (!fs.existsSync(FRONTEND_DIR) || !fs.existsSync(packageJsonPath)) {
    return errors; // No frontend project
  }

  // Check if ESLint is available
  const eslintBin = process.platform === 'win32'
    ? path.join(FRONTEND_DIR, 'node_modules', '.bin', 'eslint.cmd')
    : path.join(FRONTEND_DIR, 'node_modules', '.bin', 'eslint');

  if (!fs.existsSync(eslintBin)) {
    logInfo('ESLint not available, skipping JavaScript linting');
    return errors;
  }

  logInfo('Linting TypeScript/React code with ESLint...');

  try {
    // Run ESLint with JSON output
    const output = execSync(
      `"${eslintBin}" "src/**/*.{ts,tsx,js,jsx}" --format json`,
      {
        cwd: FRONTEND_DIR,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
      }
    );

    // Parse ESLint JSON output
    const results = JSON.parse(output);

    for (const fileResult of results) {
      if (fileResult.messages) {
        for (const msg of fileResult.messages) {
          // Only report errors (severity 2)
          if (msg.severity === 2) {
            errors.push({
              type: 'error',
              path: fileResult.filePath || '',
              obj: msg.ruleId || '',
              message: msg.message || '',
              line: String(msg.line || ''),
              column: String(msg.column || ''),
            });
          }
        }
      }
    }
  } catch (error) {
    // ESLint returns non-zero exit code when errors are found
    if (error.stdout) {
      try {
        const results = JSON.parse(error.stdout);

        for (const fileResult of results) {
          if (fileResult.messages) {
            for (const msg of fileResult.messages) {
              // Only report errors (severity 2)
              if (msg.severity === 2) {
                errors.push({
                  type: 'error',
                  path: fileResult.filePath || '',
                  obj: msg.ruleId || '',
                  message: msg.message || '',
                  line: String(msg.line || ''),
                  column: String(msg.column || ''),
                });
              }
            }
          }
        }
      } catch (parseError) {
        logError('Failed to parse ESLint output');
      }
    }
  }

  return errors;
}

/**
 * Main linting process
 */
function main() {
  logInfo('Starting lint process...');

  // Ensure dependencies and tools are installed
  ensureDependencies();
  ensureEslint();

  // Collect all lint errors
  const allErrors = [];

  // Lint Java code
  const javaErrors = lintJava();
  allErrors.push(...javaErrors);

  // Lint JavaScript/TypeScript code
  const jsErrors = lintJavaScript();
  allErrors.push(...jsErrors);

  // Output JSON to stdout
  console.log(JSON.stringify(allErrors, null, 2));

  logInfo('Linting complete');

  // Exit with appropriate code
  if (allErrors.length > 0) {
    logError(`Linting found ${allErrors.length} error(s)`);
    process.exit(ERR_LINT_FAILED);
  }

  process.exit(0);
}

// Run main function
main();
