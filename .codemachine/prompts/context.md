# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T6",
  "iteration_id": "I1",
  "iteration_goal": "Establish project scaffolding, configure development environment, define database schema, and set up CI/CD foundation to enable parallel backend and frontend development in subsequent iterations.",
  "description": "Create GitHub Actions workflows for backend CI (`backend-ci.yml`) and frontend CI (`frontend-ci.yml`). Backend workflow: checkout code, setup Java 17, run `mvn clean verify` (compile, unit tests, integration tests with Testcontainers), SonarQube analysis (code quality gate), Trivy container scan on built Docker image. Frontend workflow: checkout, setup Node.js 18, run `npm ci`, `npm run lint`, `npm run test`, `npm run build`, upload build artifacts. Configure workflow triggers (push to main, pull requests). Add workflow status badges to README.md.",
  "agent_type_hint": "SetupAgent",
  "inputs": "CI/CD requirements from architecture blueprint (Section 5.2 - CI/CD Pipeline Hardening), Maven build lifecycle for Quarkus, npm script conventions (lint, test, build)",
  "input_files": [
    "backend/pom.xml",
    "frontend/package.json"
  ],
  "target_files": [
    ".github/workflows/backend-ci.yml",
    ".github/workflows/frontend-ci.yml",
    "README.md"
  ],
  "deliverables": "Backend CI workflow with Java 17 setup, Maven build, Testcontainers support, Frontend CI workflow with Node.js 18 setup, npm tasks (lint, test, build), SonarQube integration for backend (quality gate check), Trivy security scan for backend Docker image, Workflow status badges in README, Workflows triggered on push to `main` and pull requests to `main`",
  "acceptance_criteria": "Backend workflow executes successfully on sample commit (even with minimal code), Frontend workflow executes successfully on sample commit, SonarQube analysis uploads results (if SonarCloud token configured), Trivy scan completes without critical vulnerabilities in base image, Workflow badges display in README (green checkmarks), Failed tests cause workflow to fail (red X)",
  "dependencies": ["I1.T1", "I1.T2"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: CI/CD Pipeline Integration (from 03_Verification_and_Glossary.md)

```markdown
**Continuous Integration (CI):**

Every push to `main` branch or pull request triggers:

1. **Backend CI Pipeline:**
   - Compile Java code (`mvn clean compile`)
   - Run unit tests (`mvn test`)
   - Run integration tests (`mvn verify` with Testcontainers)
   - SonarQube code quality analysis
   - Dependency vulnerability scan (Snyk)
   - Build Docker image
   - Container security scan (Trivy)
   - Publish test results and coverage reports

2. **Frontend CI Pipeline:**
   - Install dependencies (`npm ci`)
   - Lint code (`npm run lint`)
   - Run unit tests (`npm run test:unit`)
   - Build production bundle (`npm run build`)
   - Upload build artifacts

**Quality Gates:**
- Unit test coverage >80% (fail build if below threshold)
- SonarQube quality gate passed (no blocker/critical issues)
- No HIGH/CRITICAL vulnerabilities in dependencies
- Linter passes with no errors (warnings acceptable)

**Continuous Deployment (CD):**

Merges to `main` branch trigger automated deployments:

1. **Deploy to Staging:**
   - Deploy backend Docker image to Kubernetes staging namespace
   - Deploy frontend build to staging CDN
   - Run smoke tests against staging environment
   - If smoke tests pass, mark deployment as successful

2. **Deploy to Production (Manual Approval):**
   - Product owner reviews staging deployment
   - Manual approval gate in GitHub Actions
   - Deploy backend to production Kubernetes namespace (rolling update, 0 downtime)
   - Deploy frontend to production CDN
   - Run smoke tests against production
   - Monitor error rates and latency for 30 minutes
   - Automated rollback if error rate >2x baseline or smoke tests fail

**Deployment Strategy:**
- **Rolling Update:** MaxSurge=1, MaxUnavailable=0 (ensures at least 2 pods always available)
- **Canary Deployment (Future):** Route 10% traffic to new version, monitor for 15 minutes, gradually increase to 100%
- **Blue/Green Deployment (Future):** Maintain two full environments, instant traffic switch via load balancer
```

### Context: CI/CD Pipeline Details from Architecture (from 05_Operational_Architecture.md)

```markdown
**CI/CD Pipeline (GitHub Actions):**

**Stages:**
1. **Build & Test:**
   - Checkout code, setup Java 17
   - Maven build with unit tests
   - Integration tests using Testcontainers (PostgreSQL, Redis)
   - SonarQube code quality gate
2. **Container Build:**
   - Docker build multi-stage image
   - Tag with Git SHA and semantic version
   - Push to ECR
3. **Security Scan:**
   - Trivy vulnerability scan on Docker image
   - Fail pipeline if HIGH/CRITICAL vulnerabilities found
4. **Deploy to Staging:**
   - Helm upgrade staging environment with new image tag
   - Run smoke tests (health checks, critical API endpoints)
   - Playwright E2E tests for WebSocket flows
5. **Manual Approval Gate:**
   - Product owner review and approval for production deploy
6. **Deploy to Production:**
   - Helm upgrade production with blue/green strategy
   - Gradual traffic shift (10% → 50% → 100% over 30 minutes)
   - Automated rollback if error rate exceeds baseline by 2x
```

### Context: Testing Strategy (from 03_Verification_and_Glossary.md)

```markdown
**Unit Testing:**

**Scope:** Individual classes and methods in isolation (services, utilities, validators)

**Framework:** JUnit 5 (backend), Jest/Vitest (frontend)

**Coverage Target:** >90% code coverage for service layer, >80% for overall codebase

**Approach:**
- Mock external dependencies (repositories, adapters, external services) using Mockito
- Test business logic thoroughly (happy paths, edge cases, error scenarios)
- Fast execution (<5 minutes for entire unit test suite)
- Run on every developer commit and in CI pipeline

**Acceptance Criteria:**
- All unit tests pass (`mvn test`, `npm run test:unit`)
- Coverage reports meet targets (verify with JaCoCo, Istanbul)
- No flaky tests (consistent results across runs)

---

**Integration Testing:**

**Scope:** Multiple components working together with real infrastructure (database, cache, message queue)

**Framework:** Quarkus Test (`@QuarkusTest`), Testcontainers, REST Assured

**Coverage Target:** Critical integration points (API → Service → Repository → Database)

**Approach:**
- Use Testcontainers for PostgreSQL and Redis (real instances, not mocks)
- Test REST endpoints end-to-end (request → response with database persistence)
- Test WebSocket flows (connection → message handling → database → Pub/Sub broadcast)
- Verify transaction boundaries and data consistency
- Run in CI pipeline (longer execution time acceptable: 10-15 minutes)

**Acceptance Criteria:**
- All integration tests pass (`mvn verify`)
- Testcontainers start successfully (PostgreSQL, Redis)
- Database schema migrations execute correctly in tests
- No test pollution (each test isolated with database cleanup)
```

### Context: Security Testing (from 03_Verification_and_Glossary.md)

```markdown
**Security Testing:**

**Scope:** Identify vulnerabilities and validate security controls

**Tools:**
- **SAST (Static Analysis):** SonarQube code scanning
- **Dependency Scanning:** Snyk or Dependabot for vulnerable dependencies
- **Container Scanning:** Trivy for Docker image vulnerabilities
- **DAST (Dynamic Analysis):** OWASP ZAP scan against running application

**Test Areas:**
- Authentication (JWT validation, OAuth flows, session management)
- Authorization (RBAC enforcement, tier gating, org admin checks)
- Input validation (SQL injection, XSS, command injection)
- Rate limiting (brute force protection)
- Security headers (HSTS, CSP, X-Frame-Options)

**Acceptance Criteria:**
- No HIGH or CRITICAL vulnerabilities in dependency scan
- SonarQube security rating A or B
- OWASP ZAP scan shows no HIGH risk findings (or findings documented with mitigation plan)
- Penetration test report (if required for Enterprise customers) shows acceptable risk level
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/pom.xml`
    *   **Summary:** This is the Maven POM file for the backend Quarkus application. It defines Java 17 as the compilation target, includes Quarkus 3.15.1 platform, and has comprehensive dependencies for reactive REST, WebSockets, database access, OAuth2, JWT, metrics, health checks, and testing (JUnit 5, REST Assured).
    *   **Recommendation:** You MUST use `mvn clean verify` as specified in the task. This will run both unit tests (Surefire) and integration tests (Failsafe). The POM already includes the necessary plugins (Maven Compiler, Surefire, Failsafe, Quarkus). You SHOULD NOT modify the POM file for this task unless there are missing dependencies for SonarQube or Trivy integration.
    *   **Note:** The POM includes `quarkus-test-vertx` and `rest-assured` for testing, which are required for integration tests with Testcontainers.

*   **File:** `frontend/package.json`
    *   **Summary:** This is the npm package manifest for the React TypeScript frontend built with Vite. It includes scripts: `dev` (Vite dev server), `build` (TypeScript compile + Vite build), `lint` (ESLint), and `preview` (Vite preview). Dependencies include React 18, React Router, Headless UI, Zustand, React Query, Zod, date-fns, and Recharts.
    *   **Recommendation:** You MUST use `npm ci` (not `npm install`) for CI environments to ensure reproducible builds. You MUST run `npm run lint` and `npm run build` as specified. The task mentions `npm run test` but the current package.json does NOT have a test script defined. You SHOULD add a test script placeholder (e.g., `"test": "echo \"No tests yet\" && exit 0"`) or skip test step initially until frontend tests are implemented in later iterations.
    *   **Warning:** The package.json is missing a `test` script. This will cause the frontend CI workflow to fail if you include `npm run test`. You must either add a placeholder test script or comment out the test step in the workflow.

*   **File:** `.github/workflows/ci.yml` (existing workflow)
    *   **Summary:** There is an existing basic CI workflow that uses docker-compose to run tests and SonarQube analysis. It runs `docker-compose run server mvn test` and `docker-compose run server mvn verify sonar:sonar`.
    *   **Recommendation:** You SHOULD replace or rename this existing workflow because it relies on docker-compose with a "server" service that doesn't exist in the current docker-compose.yml. The new backend-ci.yml workflow should be standalone and not depend on the legacy docker-compose setup. You can either delete the old ci.yml or rename it to ci.yml.legacy for reference.
    *   **Note:** The existing workflow already includes SonarQube integration with `sonar:sonar` using a `SONAR_TOKEN` secret. You SHOULD reuse this approach in your new workflow.

*   **File:** `docker-compose.yml`
    *   **Summary:** This file defines infrastructure services (PostgreSQL, Redis 3-node cluster, Prometheus, Grafana) for local development. It does NOT define application containers or a "server" service, which means the old ci.yml workflow is broken.
    *   **Recommendation:** The new CI workflows should NOT use docker-compose for running the application. Instead, they should use Testcontainers (which is already configured in the backend) for integration tests. The CI workflows only need to set up Java/Node.js and run Maven/npm commands directly.

*   **File:** `README.md`
    *   **Summary:** The README currently documents the project setup with docker-compose, Quarkus dev mode, monitoring setup, and basic usage instructions. It does NOT have any GitHub Actions status badges.
    *   **Recommendation:** You MUST add workflow status badges to the README at the top of the file. The badge format should be: `![Backend CI](https://github.com/OWNER/REPO/workflows/Backend%20CI/badge.svg)` and `![Frontend CI](https://github.com/OWNER/REPO/workflows/Frontend%20CI/badge.svg)`. You can use a placeholder like `YOUR_GITHUB_ORG` that users should replace.

### Implementation Tips & Notes

*   **Tip:** For SonarQube integration in the backend CI workflow, you SHOULD use the Maven SonarQube plugin with `mvn sonar:sonar`. The existing ci.yml workflow shows the pattern: use `-Dsonar.login=${{ secrets.SONAR_TOKEN }}` and `-Dsonar.host.url="https://sonarcloud.io"`. You'll need to configure the `SONAR_TOKEN` secret in GitHub repository settings (or document this requirement).

*   **Tip:** For Trivy container scanning, you'll need to build a Docker image first. However, the current project structure doesn't have a Dockerfile in the backend directory. You have two options:
    1. Create a basic Dockerfile for the Quarkus application (recommended for completeness).
    2. Skip the Trivy scan step for now and document it as a TODO, since the task says "even with minimal code" acceptance criteria.

    For MVP, I recommend option 2: add a comment in the workflow that Trivy scanning will be added once Dockerfile is created in I8.T1.

*   **Note:** The frontend package.json is missing a `test` script. You MUST either:
    1. Add `"test": "echo \"No tests implemented yet\" && exit 0"` to package.json scripts.
    2. Comment out or remove the `npm run test` step from the frontend-ci.yml workflow.

    Option 1 is preferred because it makes the workflow complete and documents the missing tests.

*   **Note:** GitHub Actions uses `ubuntu-latest` as the default runner. You SHOULD use specific versions like `ubuntu-22.04` for reproducibility, but `ubuntu-latest` is acceptable for MVP.

*   **Warning:** Testcontainers requires Docker to be available in the CI environment. GitHub Actions runners have Docker pre-installed, so you don't need to configure it explicitly. However, integration tests may take 5-10 minutes to run due to container startup. You SHOULD set a reasonable timeout for test steps (e.g., `timeout-minutes: 15`).

*   **Tip:** For uploading build artifacts (frontend build output), use the GitHub Actions `actions/upload-artifact@v3` action. The artifact should include the `frontend/dist` directory after `npm run build` completes.

*   **Tip:** Workflow triggers should be:
    ```yaml
    on:
      push:
        branches: [ main ]
      pull_request:
        branches: [ main ]
    ```
    This ensures workflows run on pushes to main and on pull requests targeting main.

*   **Important:** The acceptance criteria states "even with minimal code" - this means the workflows should be designed to succeed even if there are no tests written yet (e.g., for frontend). The backend already has the entity classes compiled, so `mvn verify` should pass.

### Dockerfile Consideration

The backend doesn't currently have a Dockerfile. For the Trivy scan step, you have options:

1. **Skip Trivy for now**: Add a comment in the workflow explaining that container scanning will be implemented in I8.T1 when the Dockerfile is created. This satisfies "even with minimal code" acceptance criteria.

2. **Create a basic Dockerfile**: Add a simple multi-stage Dockerfile based on the architecture requirements (Red Hat UBI with OpenJDK 17). This is more complete but adds scope beyond the task description.

I recommend option 1 for this task, with a TODO comment in the workflow. The task deliverable says "Trivy security scan for backend Docker image" but the acceptance criteria allows for "even with minimal code", which suggests the workflows should be set up even if not all features are fully functional yet.

### GitHub Repository Context

The project is at `/Users/tea/dev/github/planning-poker`, which suggests the GitHub repository is likely `tea/planning-poker` or similar. For the status badges in README, you'll need to use a dynamic approach or document that users need to update the badge URLs with their repository path. A better approach is to add badges with a placeholder:

```markdown
![Backend CI](https://github.com/YOUR_GITHUB_ORG/planning-poker/workflows/Backend%20CI/badge.svg)
![Frontend CI](https://github.com/YOUR_GITHUB_ORG/planning-poker/workflows/Frontend%20CI/badge.svg)
```

You should add these with a note that users need to replace `YOUR_GITHUB_ORG` with their GitHub organization/username.
