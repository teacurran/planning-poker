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
  "inputs": "*   CI/CD requirements from architecture blueprint (Section 5.2 - CI/CD Pipeline Hardening)\n        *   Maven build lifecycle for Quarkus\n        *   npm script conventions (lint, test, build)",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   Backend CI workflow with Java 17 setup, Maven build, Testcontainers support\n        *   Frontend CI workflow with Node.js 18 setup, npm tasks (lint, test, build)\n        *   SonarQube integration for backend (quality gate check)\n        *   Trivy security scan for backend Docker image\n        *   Workflow status badges in README\n        *   Workflows triggered on push to `main` and pull requests to `main`",
  "acceptance_criteria": "*   Backend workflow executes successfully on sample commit (even with minimal code)\n        *   Frontend workflow executes successfully on sample commit\n        *   SonarQube analysis uploads results (if SonarCloud token configured)\n        *   Trivy scan completes without critical vulnerabilities in base image\n        *   Workflow badges display in README (green checkmarks)\n        *   Failed tests cause workflow to fail (red X)",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: deployment-strategy (from 05_Operational_Architecture.md)

```markdown
The blueprint section referenced in the manifest (05_Operational_Architecture.md ▸ deployment-strategy) is absent from this repository, so the precise wording cannot be quoted. Based on the manifest summary, this section governs Docker-based builds promoted through CI/CD into Kubernetes with zero-downtime rolling updates. When implementing the workflows, favor container-friendly build steps (producing images, scanning them, and surfacing artifacts) because they map directly onto that intended deployment strategy.
```

### Context: ci-cd-pipeline (from 03_Verification_and_Glossary.md)

```markdown
The planning document that should describe the CI/CD quality gates (03_Verification_and_Glossary.md ▸ ci-cd-pipeline) is also missing locally. The manifest indicates it covers integrating automated tests, SonarQube quality gates, and security scans into GitHub Actions. Use the task requirements as the authoritative guidance: Maven `clean verify` must run with Testcontainers, Sonar analysis should execute (gated on secrets availability), and Trivy scanning must upload SARIF so GitHub Security can render findings.
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `.github/workflows/backend-ci.yml`
    *   **Summary:** Defines a workflow named “Backend CI” that already checks out the repo, installs Temurin Java 17 (with Maven dependency caching), runs `mvn clean verify` inside `backend/`, performs a SonarQube analysis on pushes to `main`, and publishes Surefire/Failsafe reports via `dorny/test-reporter`. Trivy steps are commented out pending a Docker image build.
    *   **Recommendation:** Reuse this file rather than starting from scratch—tighten it to match the task (ensure Trivy capability is either implemented or clearly blocked by missing Dockerfile, and keep conditional Sonar execution gated on secrets). Preserve the working-directory scoping so commands run inside `./backend`.
*   **File:** `.github/workflows/frontend-ci.yml`
    *   **Summary:** Provides a "Frontend CI" workflow that checks out the repo, sets up Node.js 18 with npm caching (tied to `frontend/package-lock.json`), runs `npm ci`, `npm run lint`, `npm run test`, and `npm run build`, and uploads the compiled `frontend/dist` artifacts.
    *   **Recommendation:** Validate that npm scripts exist and fail fast; consider surfacing test reports or cache directories only if the frontend tooling produces them. Artifacts are already uploaded—ensure naming/retention requirements align with expectations.
*   **File:** `.github/workflows/ci.yml`
    *   **Summary:** A legacy workflow that builds/runs Docker Compose services (`jaeger`, `postgresql`, etc.), executes Maven tests inside `docker compose run server`, and performs Sonar analysis via the same container. It predates the new backend/frontend split and may conflict with the dedicated workflows.
    *   **Recommendation:** Decide whether to retire or update this pipeline once the new backend/frontend workflows satisfy coverage to avoid redundant builds. If you retain it temporarily, document its purpose so contributors aren’t confused by duplicate CI checks.
*   **File:** `README.md`
    *   **Summary:** The landing README already contains placeholder Markdown badges for “Backend CI” and “Frontend CI” pointing at `YOUR_GITHUB_ORG`. It also instructs developers on environment setup and infrastructure bootstrapping.
    *   **Recommendation:** Update the badge URLs to the real GitHub org/repo names once the workflows are stable, and describe at a high level what each workflow verifies so contributors know when to inspect GitHub Actions logs.

### Implementation Tips & Notes
*   **Tip:** Keep SonarQube steps conditional on both the branch (`main`) and the presence of `secrets.SONAR_TOKEN` so forks can run the workflow without failing.
*   **Tip:** Trivy scanning requires a Docker image. If the backend doesn’t yet have a Dockerfile, either add one or wrap the scan in a conditional that only runs when the build artifact exists, matching the acceptance criteria when possible.
*   **Tip:** Cache Maven and npm dependencies via the official setup actions (already configured) so the workflows stay fast; avoid manually invoking `actions/cache` redundantly.
*   **Note:** Expose test results using the existing `dorny/test-reporter` step or GitHub’s JUnit upload to make PR failures easier to triage.
*   **Warning:** Ensure the workflows respect the repo’s multi-module structure (root `backend/` and `frontend/`). Running `mvn` or `npm` from the wrong directory will silently skip the intended code and provide a false sense of security.
