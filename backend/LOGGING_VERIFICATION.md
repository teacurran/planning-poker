# Logging Configuration Verification Guide

This document explains how to verify that the structured JSON logging with correlation IDs is working correctly.

## Overview

The application now includes:
- **Structured JSON Logging**: All logs are output in JSON format for easy parsing by log aggregation systems
- **Correlation IDs**: Every HTTP request and WebSocket session gets a unique correlation ID that appears in all related log entries
- **Environment-Specific Log Levels**: WARN for production, INFO for staging, DEBUG for development
- **MDC Fields**: correlationId, userId, roomId, and action fields are automatically included in JSON logs

## Implementation Files

- `backend/src/main/java/com/scrumpoker/logging/LoggingConstants.java` - Constants for MDC keys and headers
- `backend/src/main/java/com/scrumpoker/logging/CorrelationIdFilter.java` - JAX-RS filter that generates/propagates correlation IDs
- `backend/src/main/resources/application.properties` - JSON logging configuration (lines 214-251)
- `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java` - WebSocket handler with correlation ID support

## Manual Verification Steps

### 1. Start the Application

```bash
cd backend
mvn quarkus:dev
```

The application will start in development mode with DEBUG log level and JSON logging enabled.

### 2. Verify JSON Log Format

Check the console output. You should see logs in JSON format like:

```json
{
  "timestamp": "2025-11-05T04:20:00.123Z",
  "sequence": 1234,
  "loggerClassName": "org.jboss.logging.Logger",
  "loggerName": "com.scrumpoker.logging.CorrelationIdFilter",
  "level": "INFO",
  "message": "HTTP GET /api/v1/rooms -> 200 (45 ms)",
  "threadName": "executor-thread-1",
  "threadId": 42,
  "mdc": {
    "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  },
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "hostName": "localhost",
  "processName": "scrum-poker-backend",
  "processId": 12345
}
```

### 3. Test HTTP Correlation IDs

Make an HTTP request and observe the correlation ID in logs:

```bash
# Without correlation ID header (auto-generated)
curl -v http://localhost:8080/q/health/ready

# With custom correlation ID header (preserved)
curl -v -H "X-Correlation-ID: my-test-correlation-123" http://localhost:8080/q/health/ready
```

**Expected behavior:**
- Response headers include `X-Correlation-ID`
- All log entries for that request include the same `correlationId` field
- If you provide a custom correlation ID, it's preserved and used throughout the request

### 4. Test Correlation ID Across Multiple Log Entries

Make a request to an endpoint that performs multiple operations:

```bash
# Create a room (requires authentication, will log multiple operations)
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: test-room-creation-123" \
  -d '{"name": "Sprint Planning", "votingSystem": "FIBONACCI"}'
```

Check the logs - all entries related to this request should have `"correlationId": "test-room-creation-123"`

### 5. Test WebSocket Correlation IDs

Connect to a WebSocket endpoint and observe correlation IDs:

```bash
# Using websocat or wscat
wscat -c "ws://localhost:8080/ws/room/test-room-id?token=<jwt-token>"
```

**Expected behavior:**
- WebSocket `onOpen` generates a unique correlation ID for the session
- All log entries for that WebSocket session include the same correlation ID
- The correlation ID persists across multiple messages

### 6. Test Environment-Specific Log Levels

Test different environments:

```bash
# Development mode (DEBUG level)
mvn quarkus:dev
# You should see DEBUG, INFO, WARN, and ERROR logs

# Staging mode (INFO level)
mvn quarkus:dev -Dquarkus.profile=staging
# You should see INFO, WARN, and ERROR logs (no DEBUG)

# Production mode (WARN level)
mvn quarkus:dev -Dquarkus.profile=prod
# You should see only WARN and ERROR logs
```

### 7. Verify MDC Field Propagation

When authenticated requests are made:

```bash
curl -X GET http://localhost:8080/api/v1/rooms/123 \
  -H "Authorization: Bearer <jwt-token>" \
  -H "X-Correlation-ID: test-mdc-123"
```

**Expected log entry:**
```json
{
  "message": "HTTP GET /api/v1/rooms/123 -> 200 (12 ms)",
  "correlationId": "test-mdc-123",
  "userId": "user-id-from-jwt",
  "roomId": "123"
}
```

## Verifying Specific Features

### Correlation ID Generation

**Test:** Make a request without `X-Correlation-ID` header
**Expected:** A UUID correlation ID is auto-generated (format: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`)

```bash
curl -v http://localhost:8080/q/health/ready 2>&1 | grep X-Correlation-ID
```

### Correlation ID Preservation

**Test:** Make a request with custom `X-Correlation-ID` header
**Expected:** The same correlation ID is used in logs and response headers

```bash
curl -v -H "X-Correlation-ID: custom-id-789" http://localhost:8080/q/health/ready 2>&1 | grep X-Correlation-ID
```

### Request Logging

**Test:** Make any HTTP request
**Expected:** A log entry with format: `HTTP <METHOD> <PATH> -> <STATUS> (<DURATION> ms)`

Example log entry:
```json
{
  "level": "INFO",
  "message": "HTTP POST /api/v1/rooms -> 201 (156 ms)",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

### WebSocket Session Correlation

**Test:** Connect to WebSocket and send messages
**Expected:** All log entries for that WebSocket session share the same correlation ID

Look for log entries like:
```json
{
  "message": "WebSocket connection established: user anon_12345, room test-room, session abc123",
  "correlationId": "ws-correlation-id-123",
  "roomId": "test-room",
  "userId": "anon_12345"
}
```

## Log Aggregation Setup

### For Loki (with Promtail)

1. Configure Promtail to tail the application logs
2. Point Promtail to your Loki instance
3. Query logs in Grafana using correlation IDs:

```promql
{app="scrum-poker"} | json | correlationId="a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

### For AWS CloudWatch Logs

1. Configure CloudWatch agent to capture console output
2. Logs are automatically parsed as JSON
3. Query using CloudWatch Insights:

```sql
fields @timestamp, message, correlationId, userId, roomId
| filter correlationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
| sort @timestamp desc
```

## Troubleshooting

### Logs are not in JSON format

**Check:**
- `quarkus.log.console.json=true` in application.properties
- `quarkus-logging-json` extension is in pom.xml (run `mvn quarkus:list-extensions`)

### Correlation ID not appearing in logs

**Check:**
- The `CorrelationIdFilter` is being loaded (should see it in startup logs)
- MDC fields are configured in application.properties (lines 227-231)
- The request is going through the JAX-RS filter chain (not a static resource)

### Different correlation ID for each log entry

**Check:**
- MDC is being properly set in the filter
- You're not clearing MDC too early
- For WebSocket: correlation ID is stored in session user properties

### Log level not changing per environment

**Check:**
- The correct profile is active: `quarkus.profile` property
- Profile-specific properties use correct prefix: `%prod.`, `%staging.`, `%dev.`

## Success Criteria

✅ Application logs in JSON format when started
✅ Each log entry includes a `correlationId` field
✅ HTTP requests are logged with method, path, status, duration
✅ Correlation ID is consistent across multiple log entries for the same request
✅ WebSocket messages include `correlationId` in logs
✅ Log level adjusts per environment (WARN/INFO/DEBUG)

## Additional Notes

- The correlation ID is propagated through reactive chains automatically (Quarkus Context Propagation)
- WebSocket handlers set correlation ID in MDC for each message processing cycle
- MDC is always cleaned up in `finally` blocks to prevent thread-local leakage
- Sensitive data (JWT tokens, passwords, PII) is never logged
