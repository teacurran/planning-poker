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
  "inputs": "Directory structure specification from Section 3, Frontend technology stack (React 18, Vite, TypeScript, Tailwind), List of required npm packages",
  "input_files": [],
  "target_files": [
    "frontend/package.json",
    "frontend/tsconfig.json",
    "frontend/vite.config.ts",
    "frontend/tailwind.config.js",
    "frontend/src/",
    "frontend/index.html"
  ],
  "deliverables": "Working React application buildable with `npm run build`, Development server runnable with `npm run dev`, Tailwind CSS configured with custom theme, TypeScript configuration with strict checks and path aliases, Placeholder page components with basic routing",
  "acceptance_criteria": "`npm run dev` starts Vite dev server successfully, Navigating to `http://localhost:5173` displays HomePage component, Tailwind CSS classes render correctly (test with colored div), TypeScript compilation successful with no errors, Path aliases work (import using `@/components/...`)",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: directory-structure (from 01_Plan_Overview_and_Setup.md)

```markdown
## 3. Directory Structure

*   **Root Directory:** `scrum-poker-platform/`

*   **Structure Definition:**

    The project follows a standard Maven multi-module structure for the backend and modern React SPA conventions for the frontend, with clear separation of concerns and dedicated locations for documentation, API specifications, and infrastructure-as-code.

    ~~~
    scrum-poker-platform/
    ├── backend/                          # Quarkus backend application
    │   [... backend structure ...]
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
    ~~~

**Justifications for Key Choices:**

1. **Component-Based Frontend (`frontend/src/components/`):** Reusable UI patterns, aligns with atomic design principles
2. **Domain organization in components:** Components are organized by feature domain (auth, room, dashboard) for scalability
3. **Separation of pages vs components:** Pages are route-level containers; components are reusable building blocks
4. **Services directory:** Centralizes all API communication logic (REST and WebSocket)
5. **Types directory:** TypeScript definitions for API contracts enable type-safe communication with backend
```

### Context: technology-stack (from 01_Plan_Overview_and_Setup.md)

```markdown
*   **Technology Stack:**
    *   **Frontend:**
        *   Framework: React 18+ with TypeScript
        *   UI Library: Tailwind CSS + Headless UI
        *   State Management: Zustand (client state) + React Query (server state)
        *   WebSocket: Native WebSocket API with reconnection wrapper
    *   **Other Key Libraries/Tools:**
        *   **Testing:** Testcontainers (integration), Playwright (E2E), JUnit 5
```

### Context: technology-stack-summary (from 02_Architecture_Overview.md)

```markdown
### 3.2. Technology Stack Summary

| **Category** | **Technology Choice** | **Justification** |
|--------------|----------------------|-------------------|
| **Frontend Framework** | **React 18+ with TypeScript** | Strong ecosystem, concurrent rendering for real-time updates, TypeScript for type safety in WebSocket message contracts |
| **UI Component Library** | **Tailwind CSS + Headless UI** | Utility-first CSS for rapid development, Headless UI for accessible components (modals, dropdowns), minimal bundle size |
| **State Management** | **Zustand + React Query** | Lightweight state management (Zustand), server state caching and synchronization (React Query), WebSocket integration support |
| **WebSocket Client** | **Native WebSocket API + Reconnecting wrapper** | Native browser API for compatibility, lightweight reconnection logic with exponential backoff |

#### Key Libraries & Extensions

**Frontend (React):**
- `@tanstack/react-query` - Server state management and caching
- `zustand` - Client-side state management (UI, WebSocket connection state)
- `react-hook-form` - Form validation and submission
- `zod` - Schema validation for API responses and WebSocket messages
- `date-fns` - Date/time formatting for session history
- `recharts` - Charting library for analytics dashboards
- `@headlessui/react` - Accessible UI components
- `heroicons` - Icon library

**DevOps & Testing:**
- `testcontainers` - Integration testing with PostgreSQL and Redis containers
- `rest-assured` - REST API testing
- `playwright` - End-to-end testing for WebSocket flows
- `k6` - Load testing for WebSocket concurrency benchmarks
```

### Context: architectural-style (from 02_Architecture_Overview.md)

```markdown
**Primary Style:** **Modular Monolith with Event-Driven Reactive Patterns**

#### Rationale

The chosen architectural style balances the simplicity and operational efficiency required for a small-to-medium scale SaaS application with the scalability and real-time responsiveness demanded by WebSocket-based collaborative features.

**Why Modular Monolith:**
1. **Team Size & Velocity:** A modular monolith allows a small development team to iterate rapidly without the operational overhead of microservices
2. **Transactional Consistency:** Core business operations (room creation, vote recording, subscription changes) benefit from ACID transactions within a single database
3. **Simplified Deployment:** Single deployable artifact reduces CI/CD complexity
4. **Clear Upgrade Path:** Well-defined module boundaries enable future extraction into microservices if scale demands

**Why Event-Driven Reactive Patterns:**
1. **Real-Time Requirements:** WebSocket connections require non-blocking I/O to efficiently handle thousands of concurrent connections
2. **Quarkus Reactive:** Leverages Quarkus's reactive runtime for high-concurrency WebSocket handling
3. **Event Propagation:** Redis Pub/Sub enables stateless application nodes to broadcast room state changes
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/pom.xml`
    *   **Summary:** This file defines the Quarkus 3.15.1 backend project with all required reactive extensions. It uses Java 17 and Maven as the build system.
    *   **Recommendation:** You MUST ensure the frontend development server (Vite on port 5173) is configured in CORS settings to communicate with the backend during local development. The backend is configured to accept CORS from `http://localhost:5173` in `backend/src/main/resources/application.properties`.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** This file contains comprehensive Quarkus configuration including database, Redis, JWT, OIDC, WebSocket, HTTP, CORS, health checks, metrics, and logging settings. The HTTP server runs on port 8080 by default.
    *   **Recommendation:** You SHOULD note that the CORS configuration already includes `http://localhost:5173` (Vite's default port) in the allowed origins list at line 99: `quarkus.http.cors.origins=${CORS_ORIGINS:http://localhost:3000,http://localhost:5173}`. Your frontend development server will be able to make API calls to `http://localhost:8080` without CORS issues.

*   **File:** `backend/src/main/java/com/scrumpoker/*/package-info.java`
    *   **Summary:** Package documentation files exist for all backend module directories (api, config, domain, event, integration, repository, security), indicating a well-organized hexagonal architecture structure.
    *   **Recommendation:** You SHOULD mirror this level of organization in the frontend project structure with clear separation of concerns (components, pages, services, stores, types, utils).

### Implementation Tips & Notes

*   **CRITICAL: Project Location:** The frontend project MUST be created at the root level as `frontend/` directory (sibling to `backend/`), NOT nested inside the backend project or src directories. The current codebase shows that `frontend/` directory does not exist yet - you will be creating it from scratch.

*   **CRITICAL: Vite Entry Point:** Create `index.html` at `frontend/index.html` (at the frontend project root), NOT in a `public/` subdirectory. Vite uses the root-level `index.html` as the entry point, unlike Create React App which uses `public/index.html`. However, you SHOULD still create a `frontend/public/` directory for static assets like favicon.

*   **Required npm Packages:** Based on the architecture documents, you MUST install these npm packages:
    - **Core:** `react@^18`, `react-dom@^18`, `react-router-dom@^6`
    - **Build Tool:** `vite@^5`, `@vitejs/plugin-react@^4`
    - **TypeScript:** `typescript@^5`, `@types/react@^18`, `@types/react-dom@^18`, `@types/node@^20`
    - **UI/Styling:** `tailwindcss@^3`, `postcss@^8`, `autoprefixer@^10`, `@headlessui/react@^2`, `@heroicons/react@^2`
    - **State Management:** `zustand@^4`, `@tanstack/react-query@^5`
    - **Validation:** `zod@^3`
    - **Utilities:** `date-fns@^3`, `recharts@^2`
    - **Development:** `eslint@^8`, `@typescript-eslint/parser@^6`, `@typescript-eslint/eslint-plugin@^6`

*   **Path Aliases Configuration:** The task requires path aliases (`@/components`, `@/services`). You MUST configure BOTH:
    1. `tsconfig.json` with `"paths"` compiler option for TypeScript type checking
    2. `vite.config.ts` with `resolve.alias` for Vite module resolution at runtime

    These must match exactly, or imports will fail at build time.

*   **Tailwind CSS Setup:** Tailwind requires THREE configuration steps:
    1. Create `tailwind.config.js` with content paths pointing to all template files
    2. Create `postcss.config.js` with Tailwind and Autoprefixer plugins
    3. Create a CSS file (e.g., `src/index.css`) and add Tailwind directives: `@tailwind base;`, `@tailwind components;`, `@tailwind utilities;`
    4. Import the CSS file in your entry point (`src/main.tsx` or `src/index.tsx`)

*   **Custom Tailwind Theme:** The task requires a custom theme with primary color and dark mode support. In `tailwind.config.js`, you MUST:
    - Enable dark mode with `darkMode: 'class'` (or 'media')
    - Define custom colors in `theme.extend.colors` (e.g., a primary color palette)
    - Configure responsive breakpoints if needed

*   **Directory Structure:** You MUST create these subdirectories under `frontend/src/`:
    - `components/` (with subdirectories: `common/`, `room/`, `auth/`, `dashboard/`)
    - `pages/`
    - `services/`
    - `stores/`
    - `types/`
    - `utils/`

*   **Placeholder Page Components:** Create at minimum THREE placeholder page components as specified in the task:
    - `src/pages/HomePage.tsx` - Landing/home page
    - `src/pages/RoomPage.tsx` - Estimation room page
    - `src/pages/DashboardPage.tsx` - User dashboard

    Each page component SHOULD include at least a heading and a colored div using Tailwind CSS classes to verify styling works.

*   **React Router Setup:** You MUST set up React Router in `App.tsx` with routes for the placeholder pages:
    ```tsx
    import { BrowserRouter, Routes, Route } from 'react-router-dom';
    ```
    Define routes like: `/` → HomePage, `/room/:roomId` → RoomPage, `/dashboard` → DashboardPage

*   **TypeScript Strict Mode:** The task requires TypeScript strict mode. In `tsconfig.json`, you MUST set:
    ```json
    "compilerOptions": {
      "strict": true,
      "noUnusedLocals": true,
      "noUnusedParameters": true,
      "noFallthroughCasesInSwitch": true
    }
    ```

*   **Backend Integration Ready:** The backend is already running on port 8080 with CORS configured for `http://localhost:5173`. You DO NOT need to set up API calls yet (that's for later iterations), but ensure the frontend dev server runs on the default Vite port 5173.

*   **Git Ignore:** The root `.gitignore` already exists. You SHOULD ensure `frontend/node_modules/` and `frontend/dist/` are excluded. Check and add these entries if missing.

*   **Verification After Setup:** To verify the task is complete:
    1. Run `npm install` in `frontend/` directory - should complete without errors
    2. Run `npm run dev` - Vite dev server should start on port 5173
    3. Open `http://localhost:5173` in browser - should see HomePage with Tailwind styling
    4. Run `npm run build` - should compile TypeScript and build production bundle without errors
    5. Check imports with path aliases work (e.g., `import Button from '@/components/common/Button'`)

*   **Project Context:** This frontend will eventually become a real-time Scrum Poker estimation platform with WebSocket-based collaborative gameplay. The UI must support voting interfaces, room management, authentication flows, and responsive design for mobile devices. Keep this in mind when organizing components.
