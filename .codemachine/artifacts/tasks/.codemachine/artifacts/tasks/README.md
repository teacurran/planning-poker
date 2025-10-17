# Project Tasks - Scrum Poker Platform

## Overview

This directory contains the structured task definitions extracted from the Scrum Poker Platform Project Plan. All tasks have been organized by iteration and formatted as machine-readable JSON files.

## File Structure

```
.codemachine/artifacts/tasks/
├── tasks_I1.json          # Iteration 1: Project Setup & Database Foundation (8 tasks)
├── tasks_I2.json          # Iteration 2: Core Backend Services & API Contracts (8 tasks)
├── tasks_I3.json          # Iteration 3: Authentication & User Management (8 tasks)
├── tasks_I4.json          # Iteration 4: Real-Time Voting Engine (WebSocket) (8 tasks)
├── tasks_I5.json          # Iteration 5: Subscription & Billing (Stripe) (8 tasks)
├── tasks_I6.json          # Iteration 6: Reporting & Analytics (8 tasks)
├── tasks_I7.json          # Iteration 7: Enterprise Features (SSO & Organizations) (8 tasks)
├── tasks_I8.json          # Iteration 8: Deployment & Production Readiness (8 tasks)
├── tasks_manifest.json    # Index mapping iteration IDs to task files
└── README.md              # This file
```

## Statistics

- **Total Iterations:** 8
- **Total Tasks:** 64
- **Tasks per Iteration:** 8
- **Format:** JSON (machine-readable)

## Task Structure

Each task object contains the following fields:

| Field | Type | Description |
|-------|------|-------------|
| `task_id` | string | Unique task identifier (e.g., "I1.T1") |
| `iteration_id` | string | Parent iteration ID (e.g., "I1") |
| `iteration_goal` | string | Goal statement for the iteration |
| `description` | string | Detailed task description |
| `agent_type_hint` | string | Suggested agent type (e.g., "BackendAgent", "SetupAgent") |
| `inputs` | string | Required inputs and context |
| `input_files` | array | List of files the task depends on (relative paths) |
| `target_files` | array | Files to be created/modified (relative paths) |
| `deliverables` | string | Expected outputs |
| `acceptance_criteria` | string | Success criteria for task completion |
| `dependencies` | array | List of task_id dependencies |
| `parallelizable` | boolean | Whether task can run in parallel |
| `done` | boolean | Task completion status (initially false) |

## Manifest Usage

The `tasks_manifest.json` file provides a simple index for locating tasks by iteration:

```json
{
  "I1": "tasks_I1.json",
  "I2": "tasks_I2.json",
  ...
}
```

**Example usage:**
1. Read the manifest to find the file for iteration "I2"
2. Load `tasks_I2.json` to access all 8 tasks for that iteration

## Iteration Summaries

### Iteration 1: Project Setup & Database Foundation
- **Goal:** Establish project scaffolding, configure development environment, define database schema
- **Key Tasks:** Backend setup, Frontend setup, Database migrations, Entity classes, Docker Compose, CI/CD pipelines

### Iteration 2: Core Backend Services & API Contracts
- **Goal:** Implement foundational domain services and define API contracts
- **Key Tasks:** OpenAPI spec, WebSocket protocol, RoomService, UserService, REST controllers, Unit tests

### Iteration 3: Authentication & User Management
- **Goal:** Implement OAuth2 authentication and user management
- **Key Tasks:** OAuth2 adapter, JWT service, Auth controller, Frontend login, API client, Dashboard UI, E2E tests

### Iteration 4: Real-Time Voting Engine
- **Goal:** Implement WebSocket-based real-time voting functionality
- **Key Tasks:** WebSocket handler, Redis Pub/Sub, VotingService, Message handlers, Frontend WebSocket manager, Voting UI

### Iteration 5: Subscription & Billing
- **Goal:** Implement Stripe subscription billing and tier enforcement
- **Key Tasks:** Stripe adapter, BillingService, Webhook handler, Tier enforcement, Subscription API, Pricing UI, Tests

### Iteration 6: Reporting & Analytics
- **Goal:** Implement session history tracking and tier-based reporting
- **Key Tasks:** SessionHistoryService, ReportingService, Export processor, Reporting API, Frontend reporting UI, Tests

### Iteration 7: Enterprise Features
- **Goal:** Implement SSO integration and organization management
- **Key Tasks:** SSO adapter, OrganizationService, Audit logging, SSO auth flow, Organization API, Admin UI, Tests

### Iteration 8: Deployment & Production Readiness
- **Goal:** Prepare for production deployment
- **Key Tasks:** Kubernetes manifests, Monitoring setup, Performance optimization, Security hardening, Documentation, Smoke tests

## Path Conventions

All file paths in `input_files` and `target_files` are **relative to the project root** (not absolute paths).

**Examples:**
- ✅ `backend/src/main/java/com/scrumpoker/domain/user/User.java`
- ✅ `frontend/src/pages/LoginPage.tsx`
- ✅ `api/openapi.yaml`
- ❌ `/Users/username/project/backend/...` (absolute paths not used)

## Dependency Graph

Tasks are interconnected through the `dependencies` array. The orchestrator can use this to:

1. Determine execution order (topological sort)
2. Identify parallelizable tasks (`parallelizable: true` and no dependencies)
3. Validate that prerequisites are complete before starting dependent tasks

**Example dependency chain:**
- I1.T1 (Backend setup) → I1.T3 (Database migrations) → I1.T4 (Entity classes) → I1.T7 (Repositories)

## Validation

All tasks have been validated to ensure:
- ✅ Unique task IDs
- ✅ Valid iteration references
- ✅ Complete required fields
- ✅ Properly formatted JSON
- ✅ Relative file paths (no absolute paths)

## Usage by Orchestrator

An orchestrator can use these files to:

1. **Load all tasks for an iteration:**
   ```javascript
   const manifest = JSON.parse(fs.readFileSync('tasks_manifest.json'));
   const tasks = JSON.parse(fs.readFileSync(manifest['I1']));
   ```

2. **Find tasks ready to execute:**
   ```javascript
   const readyTasks = tasks.filter(task =>
     !task.done &&
     task.dependencies.every(depId => isCompleted(depId))
   );
   ```

3. **Identify parallelizable tasks:**
   ```javascript
   const parallelTasks = readyTasks.filter(task => task.parallelizable);
   ```

## Generation Details

- **Generated:** 2025-10-17
- **Source:** Project Plan documents in `.codemachine/plan/`
- **Tool:** Claude AI (Sonnet 4.5)
- **Validation:** All 64 tasks extracted and validated

---

For questions or issues with the task definitions, refer to the original plan documents in `.codemachine/plan/`.
