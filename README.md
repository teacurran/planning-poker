# Planning Poker

A real-time Scrum Planning Poker application built with Quarkus Reactive, Hibernate Reactive with Panache, Vue.js 3, and PrimeVue.

## Features

- Create and join planning poker rooms via unique room IDs
- Real-time card selection and reveal using WebSockets
- Fibonacci sequence voting values (0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, ?, ☕)
- Observer mode for non-voting participants
- Instant vote updates visible to all participants
- Statistics display after cards are revealed (average, consensus, distribution)
- Responsive design with PrimeVue components

## Tech Stack

- **Backend**: Quarkus with Reactive extensions
  - Hibernate Reactive with Panache for ORM
  - WebSockets for real-time communication
  - Flyway for database migrations
  - PostgreSQL (Reactive driver)
  
- **Frontend**: Vue.js 3
  - PrimeVue component library
  - Vite for build tooling
  - Quinoa for frontend/backend integration

## Prerequisites

- Java 17+
- Maven 3.8+
- Node.js 18+
- Docker and Docker Compose (for PostgreSQL)

## Getting Started

### 1. Start the PostgreSQL database

```bash
docker-compose up -d
```

### 2. Install frontend dependencies

```bash
cd src/main/webui
npm install
cd ../../..
```

### 3. Run the application in development mode

```bash
./mvnw quarkus:dev
```

The application will be available at:
- Backend API: http://localhost:8080/api
- Frontend (via Quinoa): http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui

## Development

### Backend Development

The backend uses Quarkus reactive stack. Main packages:
- `entity` - JPA entities with Panache
- `resource` - REST endpoints
- `service` - Business logic
- `websocket` - WebSocket endpoints and messages
- `dto` - Data transfer objects

### Frontend Development

For frontend-only development with hot reload:

```bash
cd src/main/webui
npm run dev
```

This will start Vite dev server on http://localhost:3000 with proxy to backend.

### Database Migrations

Flyway migrations are located in `src/main/resources/db/migration/`. They run automatically on startup.

## Building for Production

```bash
./mvnw clean package
```

This will:
1. Build the Vue.js frontend
2. Package it with Quarkus using Quinoa
3. Create an executable JAR

Run the production build:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

## How to Use

1. Navigate to the home page
2. Create a new room with a name, or join an existing room with its ID
3. Enter your username when joining a room
4. Select your estimation card (if not an observer)
5. Wait for all participants to vote
6. Click "Reveal Cards" to show all votes
7. Review statistics and discuss
8. Click "New Round" to start another estimation

## Project Structure

```
planning-poker/
├── src/
│   ├── main/
│   │   ├── java/               # Quarkus backend
│   │   ├── resources/          # Configuration and migrations
│   │   └── webui/              # Vue.js frontend
│   └── test/
├── docker-compose.yml          # PostgreSQL setup
├── pom.xml                     # Maven configuration
└── README.md
```