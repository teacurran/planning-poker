# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T2",
  "iteration_id": "I1",
  "iteration_goal": "Establish project scaffolding, configure development environment, define database schema, and set up CI/CD foundation to enable parallel backend and frontend development in subsequent iterations.",
  "description": "Create React 18 TypeScript project using Vite. Install dependencies: React, React Router, Tailwind CSS, Headless UI, Zustand, React Query, Zod, date-fns, recharts. Configure Tailwind CSS with custom theme (primary color, dark mode support). Set up directory structure: `components`, `pages`, `services`, `stores`, `types`, `utils`. Create placeholder components for routing (HomePage, RoomPage, DashboardPage). Configure TypeScript with strict mode, path aliases (`@/components`, `@/services`).",
  "agent_type_hint": "SetupAgent",
  "inputs": "*   Directory structure specification from Section 3\n        *   Frontend technology stack (React 18, Vite, TypeScript, Tailwind)\n        *   List of required npm packages",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   Working React application buildable with `npm run build`\n        *   Development server runnable with `npm run dev`\n        *   Tailwind CSS configured with custom theme\n        *   TypeScript configuration with strict checks and path aliases\n        *   Placeholder page components with basic routing",
  "acceptance_criteria": "*   `npm run dev` starts Vite dev server successfully\n        *   Navigating to `http://localhost:5173` displays HomePage component\n        *   Tailwind CSS classes render correctly (test with colored div)\n        *   TypeScript compilation successful with no errors\n        *   Path aliases work (import using `@/components/...`)",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: technology-stack (from 01_Plan_Overview_and_Setup.md)

```markdown
<!-- anchor: technology-stack -->
*   **Technology Stack:**
    *   **Frontend:**
        *   Framework: React 18+ with TypeScript
        *   UI Library: Tailwind CSS + Headless UI
        *   State Management: Zustand (client state) + React Query (server state)
        *   WebSocket: Native WebSocket API with reconnection wrapper
    *   **Backend:**
        *   Framework: Quarkus 3.x (Reactive mode)
        *   Language: Java 17 (LTS)
        *   Runtime: JVM mode (potential future native compilation)
    *   **Database:**
        *   Primary: PostgreSQL 15+ (ACID compliance, JSONB support, partitioning)
        *   ORM: Hibernate Reactive + Panache repositories
    *   **Messaging/Queues:**
        *   Redis 7+ Cluster (Pub/Sub for WebSocket broadcasting, Streams for async jobs)
    *   **Deployment:**
        *   Containerization: Docker (multi-stage builds)
        *   Orchestration: Kubernetes (AWS EKS or GCP GKE)
        *   Cloud Platform: AWS (primary) with CloudFront CDN, RDS, ElastiCache
    *   **Other Key Libraries/Tools:**
        *   **Auth:** Quarkus OIDC extension (OAuth2/SSO), SmallRye JWT
        *   **Payments:** Stripe Java SDK
        *   **Logging:** SLF4J with JSON formatter, Loki/CloudWatch aggregation
        *   **Metrics:** Prometheus + Grafana dashboards
        *   **Testing:** Testcontainers (integration), Playwright (E2E), JUnit 5
```

### Context: directory-structure (from 01_Plan_Overview_and_Setup.md)

```markdown
<!-- anchor: directory-structure -->
## 3. Directory Structure

*   **Root Directory:** `scrum-poker-platform/`

*   **Structure Definition:**

    The project follows a standard Maven multi-module structure for the backend and modern React SPA conventions for the frontend, with clear separation of concerns and dedicated locations for documentation, API specifications, and infrastructure-as-code.

    ~~~
    scrum-poker-platform/
    ├── backend/                          # Quarkus backend application
    │   ├── src/
    │   │   ├── main/
    │   │   │   ├── java/
    │   │   │   │   └── com/scrumpoker/
    │   │   │   │       ├── api/          # REST controllers, WebSocket handlers
    │   │   │   │       │   ├── rest/     # JAX-RS resource classes
    │   │   │   │       │   └── websocket/ # WebSocket endpoint handlers
    │   │   │   │       ├── domain/       # Domain services and business logic
    │   │   │   │       │   ├── user/     # User, UserPreference entities + service
    │   │   │   │       │   ├── room/     # Room, Round, Vote entities + service
    │   │   │   │       │   ├── billing/  # Subscription, Payment entities + service
    │   │   │   │       │   ├── reporting/ # SessionHistory, analytics service
    │   │   │   │       │   └── organization/ # Organization, OrgMember entities + service
    │   │   │   │       ├── repository/   # Panache repositories
    │   │   │   │       ├── integration/  # External service adapters
    │   │   │   │       │   ├── oauth/    # Google/Microsoft OAuth2 clients
    │   │   │   │       │   ├── sso/      # OIDC/SAML2 adapters
    │   │   │   │       │   ├── stripe/   # Stripe SDK wrapper
    │   │   │   │       │   └── email/    # SendGrid/SES client
    │   │   │   │       ├── event/        # Redis Pub/Sub publisher/subscriber
    │   │   │   │       ├── config/       # Application configuration classes
    │   │   │   │       └── security/     # Authentication filters, JWT utilities
    │   │   │   └── resources/
    │   │   │       ├── application.properties  # Quarkus configuration
    │   │   │       └── db/
    │   │   │           └── migration/    # Flyway SQL migration scripts
    │   │   │               ├── V1__initial_schema.sql
    │   │   │               ├── V2__add_organizations.sql
    │   │   │               └── ...
    │   │   └── test/
    │   │       ├── java/
    │   │       │   └── com/scrumpoker/
    │   │       │       ├── api/          # REST/WebSocket integration tests
    │   │       │       ├── domain/       # Unit tests for services
    │   │       │       └── repository/   # Repository tests with Testcontainers
    │   │       └── resources/
    │   │           └── application-test.properties
    │   ├── pom.xml                       # Maven project descriptor
    │   └── Dockerfile                    # Multi-stage Docker build
    │
    ├── frontend/                         # React SPA
    │   ├── public/
    │   │   ├── index.html
    │   │   └── favicon.ico
    │   ├── src/
    │   │   ├── components/               # Reusable UI components
    │   │   │   ├── common/               # Buttons, modals, forms
    │   │   │   ├── room/                 # Room lobby, voting card, reveal
    │   │   │   ├── auth/                 # Login, OAuth callback
    │   │   │   └── dashboard/            # User dashboard, settings
    │   │   ├── pages/                    # Route-level page components
    │   │   │   ├── HomePage.tsx
    │   │   │   ├── RoomPage.tsx
    │   │   │   ├── DashboardPage.tsx
    │   │   │   └── SettingsPage.tsx
    │   │   ├── services/                 # API clients and WebSocket manager
    │   │   │   ├── api.ts                # REST API client (React Query)
    │   │   │   └── websocket.ts          # WebSocket connection manager
    │   │   ├── stores/                   # Zustand state stores
    │   │   │   ├── authStore.ts
    │   │   │   ├── roomStore.ts
    │   │   │   └── uiStore.ts
    │   │   ├── types/                    # TypeScript type definitions
    │   │   │   ├── api.ts                # API DTOs (generated from OpenAPI)
    │   │   │   └── websocket.ts          # WebSocket message types
    │   │   ├── utils/                    # Utility functions
    │   │   ├── App.tsx                   # Root component with routing
    │   │   ├── index.tsx                 # Entry point
    │   │   └── tailwind.config.js        # Tailwind CSS configuration
    │   ├── package.json
    │   ├── tsconfig.json
    │   └── vite.config.ts                # Vite build configuration
    │
    ├── marketing-site/                   # Separate static marketing website
    │   ├── src/
    │   │   ├── pages/
    │   │   │   ├── index.astro           # Landing page
    │   │   │   ├── pricing.astro
    │   │   │   ├── demo.astro
    │   │   │   └── blog/
    │   │   ├── components/
    │   │   └── layouts/
    │   ├── public/
    │   │   └── assets/
    │   ├── astro.config.mjs              # Astro framework configuration
    │   └── package.json
    │
    ├── docs/                             # Documentation and design artifacts
    │   ├── architecture/                 # System architecture blueprint (reference)
    │   │   ├── 01_Context_and_Drivers.md
    │   │   ├── 02_Architecture_Overview.md
    │   │   ├── 03_System_Structure_and_Data.md
    │   │   ├── 04_Behavior_and_Communication.md
    │   │   ├── 05_Operational_Architecture.md
    │   │   ├── 06_Rationale_and_Future.md
    │   │   └── architecture_manifest.json
    │   ├── diagrams/                     # UML and architectural diagrams
    │   │   ├── component_diagram.puml
    │   │   ├── sequence_vote_flow.puml
    │   │   ├── sequence_oauth.puml
    │   │   ├── erd.puml
    │   │   └── deployment_aws.puml
    │   ├── adr/                          # Architectural Decision Records
    │   │   ├── 001-modular-monolith.md
    │   │   ├── 002-quarkus-reactive.md
    │   │   └── ...
    │   └── runbooks/                     # Operational runbooks
    │       ├── deployment.md
    │       ├── disaster-recovery.md
    │       └── scaling.md
    │
    ├── api/                              # API specifications
    │   ├── openapi.yaml                  # OpenAPI 3.1 REST API spec
    │   └── websocket-protocol.md         # WebSocket message catalog
    │
    ├── infra/                            # Infrastructure as Code
    │   ├── kubernetes/                   # Kubernetes manifests
    │   │   ├── base/
    │   │   │   ├── deployment.yaml
    │   │   │   ├── service.yaml
    │   │   │   ├── ingress.yaml
    │   │   │   ├── configmap.yaml
    │   │   │   └── hpa.yaml              # HorizontalPodAutoscaler
    │   │   ├── overlays/
    │   │   │   ├── dev/
    │   │   │   ├── staging/
    │   │   │   └── production/
    │   │   └── kustomization.yaml
    │   ├── terraform/                    # AWS infrastructure (optional)
    │   │   ├── main.tf
    │   │   ├── vpc.tf
    │   │   ├── eks.tf
    │   │   ├── rds.tf
    │   │   └── elasticache.tf
    │   └── helm/                         # Helm chart (alternative to raw K8s)
    │       ├── Chart.yaml
    │       ├── values.yaml
    │       └── templates/
    │
    ├── scripts/                          # Utility scripts
    │   ├── generate-nanoid.js            # Room ID generator
    │   ├── seed-database.sql             # Test data seeding
    │   └── load-test.js                  # k6 load test script
    │
    ├── .github/                          # GitHub Actions CI/CD
    │   └── workflows/
    │       ├── backend-ci.yml            # Backend build, test, scan
    │       ├── frontend-ci.yml           # Frontend build, test, lint
    │       ├── deploy-staging.yml
    │       └── deploy-production.yml
    │
    ├── docker-compose.yml                # Local development environment
    ├── .gitignore
    ├── README.md                         # Project overview and setup instructions
    └── LICENSE
    ~~~

**Justifications for Key Choices:**

1. **Maven Standard Layout (backend):** Quarkus convention, familiar to Java developers, supports multi-module if needed
2. **Domain-Driven Directory Structure (backend/src/main/java/com/scrumpoker/domain/):** Clear bounded contexts (user, room, billing, reporting, organization) align with business domains and support future microservice extraction
3. **Separate `api/` Directory:** Decouples API contracts (OpenAPI spec, WebSocket protocol) from implementation, enables contract-first development and client SDK generation
4. **Dedicated `docs/` Hierarchy:** Centralizes architecture blueprints, diagrams (PlantUML source), ADRs, and runbooks for discoverability
5. **Separate `marketing-site/` Directory:** Isolates SEO-optimized static content from SPA, different deployment pipeline (static hosting vs. CDN)
6. **Infrastructure as Code (`infra/`):** Supports GitOps workflows, version-controlled Kubernetes manifests and Terraform scripts
7. **Flyway Migration Scripts (`backend/src/main/resources/db/migration/`):** Automated, version-controlled database schema evolution
8. **Component-Based Frontend (`frontend/src/components/`):** Reusable UI patterns, aligns with atomic design principles
9. **Testcontainers Support (`backend/src/test/`):** Integration tests with real PostgreSQL/Redis instances for high confidence
```

### Context: usability-nfrs (from 01_Context_and_Drivers.md)

```markdown
<!-- anchor: usability-nfrs -->
#### Usability
- **Responsive Design:** Mobile-first Tailwind CSS with breakpoints for tablet/desktop
- **Accessibility:** WCAG 2.1 Level AA compliance for keyboard navigation and screen readers
- **Browser Support:** Last 2 versions of Chrome, Firefox, Safari, Edge
- **Internationalization:** English language in initial release, i18n framework for future localization
```

### Context: preferences (from 01_Context_and_Drivers.md)

```markdown
<!-- anchor: preferences -->
#### Preferences
- **Frontend Framework:** React or Vue preferred for SPA development with strong ecosystem
- **CSS Framework:** Tailwind CSS for rapid UI development and consistent design system
- **Observability:** Prometheus/Grafana stack for metrics, ELK or Loki for centralized logging
- **CI/CD:** GitLab CI or GitHub Actions for automated testing, building, and deployment
- **Deployment Target:** AWS or GCP with managed Kubernetes (EKS/GKE) for reduced operational overhead
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `frontend/package.json`
    *   **Summary:** Defines the Vite-powered React 18 workspace with the exact dependency set called out in the plan (React Router, Tailwind, Headless UI, Zustand, React Query, Zod, date-fns, Recharts) plus Axios for HTTP access and Playwright for E2E. Scripts already expose `npm run dev`, `build`, `lint`, and multiple Playwright targets, while the `test` script is a placeholder that exits immediately.
    *   **Recommendation:** Keep dependency additions centralized here—install any new UI or tooling packages with `npm install` so that lockfiles stay in sync, and continue wiring new scripts (tests, storybook, etc.) through this file to match the existing npm workflow.
*   **File:** `frontend/tailwind.config.js`
    *   **Summary:** Enables class-based dark mode and extends the theme with a bespoke `primary` palette (50–950). The config watches `index.html` plus every TS/TSX source file beneath `src/`.
    *   **Recommendation:** Reuse the defined `primary-*` scales for accent colors instead of hardcoding hex values; if additional brand tokens are required, extend them here to keep styling centralized and automatically tree-shaken.
*   **File:** `frontend/tsconfig.json`
    *   **Summary:** TypeScript runs in strict mode with bundler module resolution, JSX `react-jsx`, and lint-focused flags like `noUnusedLocals`. It exposes a comprehensive alias map for `@/components`, `@/pages`, `@/services`, `@/stores`, `@/hooks`, `@/types`, `@/utils`, and `@/contexts`.
    *   **Recommendation:** Always import via these aliases (mirrored in `vite.config.ts`) to avoid brittle relative paths. When adding new top-level directories, update both `tsconfig.json` and `vite.config.ts` to keep IDE resolution and Vite builds aligned.
*   **File:** `frontend/src/App.tsx`
    *   **Summary:** Wraps the entire SPA in a `QueryClientProvider` with tuned defaults, a global `UpgradeModalProvider`, and `BrowserRouter`. Routes already cover the full journey (home/login, OAuth callback, pricing/billing, room experience, dashboard, reporting pages, and enterprise organization admin screens) with `PrivateRoute` guarding authenticated areas.
    *   **Recommendation:** When introducing new pages or placeholder screens, register them here and decide whether they belong behind `PrivateRoute`. Share the existing `QueryClient` instead of instantiating another, and prefer context providers/hooks that already exist (auth, upgrade modal) for consistency.

### Implementation Tips & Notes
*   **Tip:** `vite.config.ts` mirrors the TS path aliases and proxies `/api` to `http://localhost:8080`; leverage that instead of hardcoding backend URLs inside services.
*   **Tip:** Tailwind is already wired through `src/index.css` (imported by `main.tsx`). After adding new components, run `npm run dev` once so that Vite picks up fresh classes and purges unused styles correctly.
*   **Note:** Placeholder pages such as `HomePage.tsx` already demonstrate importing shared components via `@/components/...` and using the custom `primary` palette—follow that pattern to keep styling cohesive.
*   **Warning:** The npm `test` script intentionally exits with status 0; if you rely on automated testing in CI, replace this with real unit tests or ensure the CI workflow invokes `npm run lint`/`npm run build` to catch regressions.
