# Tools Directory

Cross-platform automation scripts for the Planning Poker project.

## Quick Start

```bash
# Install all dependencies
node tools/install.cjs

# Run the application
node tools/run.cjs

# Lint the codebase
node tools/lint.cjs

# Run tests
node tools/test.cjs
```

## Scripts

| Script | Purpose | Exit Code 0 | Exit Code 1+ |
|--------|---------|-------------|--------------|
| `install.cjs` | Install/update all dependencies | Success | Error |
| `run.cjs` | Run Quarkus backend in dev mode | Success | Error |
| `lint.cjs` | Lint code, output JSON | No errors | Errors found (exit 2) |
| `test.cjs` | Run all tests | Tests passed | Tests failed |

## Requirements

- **Node.js** 18+
- **Java** 17+
- **Maven** 3.8+
- **npm** (bundled with Node.js)

## Platform Support

✅ Windows
✅ macOS
✅ Linux

## Documentation

See [SCRIPTS_DOCUMENTATION.md](./SCRIPTS_DOCUMENTATION.md) for detailed documentation.

## Legacy Scripts

The original bash scripts are still available:
- `install.sh`
- `run.sh`
- `lint.sh`
- `test.sh`

The new `.cjs` scripts are recommended for better cross-platform support.
